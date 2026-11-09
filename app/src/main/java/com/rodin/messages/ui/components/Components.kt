@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rodin.messages.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodin.messages.ui.theme.avatarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

// ------------------------------------------------------------------ shared transitions

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun ProvideAnimScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalAnimScope provides scope, content = content)

/** Applies a shared-bounds transition when inside a SharedTransitionLayout + nav destination. */
@Composable
fun Modifier.sharedKey(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalAnimScope.current ?: return this
    return with(shared) {
        this@sharedKey.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 380f) },
        )
    }
}

// ------------------------------------------------------------------ helpers

@Composable
fun rememberBitmap(uri: Uri?): ImageBitmap? {
    val ctx = LocalContext.current
    val state by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { s ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeStream(s, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return state
}

fun formatTime(ctx: android.content.Context, t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    return when {
        DateUtils.isToday(t) -> DateFormat.getTimeFormat(ctx).format(t).lowercase()
        DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        now.timeInMillis - t < 6 * DateUtils.DAY_IN_MILLIS -> DateFormat.format("EEE", then).toString()
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> DateFormat.format("dd MMM", then).toString()
        else -> DateFormat.format("dd/MM/yy", then).toString()
    }
}

fun daySection(t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    return when {
        DateUtils.isToday(t) -> "Today"
        DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        now.timeInMillis - t < 7 * DateUtils.DAY_IN_MILLIS -> "This week"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == then.get(Calendar.MONTH) -> "This month"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> DateFormat.format("MMMM", then).toString()
        else -> DateFormat.format("MMMM yyyy", then).toString()
    }
}

/** Card shape for an item inside a visually grouped list (like the Dialer's recents). */
fun groupShape(index: Int, count: Int, outer: Dp = 26.dp, inner: Dp = 8.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

// ------------------------------------------------------------------ avatar

@Composable
fun Avatar(
    name: String,
    photo: Uri?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    highlighted: Boolean = false,
    group: Boolean = false,
) {
    val bmp = rememberBitmap(photo)
    val (bg, fg) = avatarColors(name, highlighted)
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier.size(size).clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val letter = if (group) "👥" else name.firstOrNull { it.isLetterOrDigit() }?.uppercase()
                ?: if (name.startsWith("+")) "+" else "#"
            Text(letter, color = fg, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
fun BigHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "title",
            ) { t ->
                Text(t, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        trailing()
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.5f, 600f), label = "s")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(44.dp).scale(s),
        interactionSource = src,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ------------------------------------------------------------------ search pill

@Composable
fun SearchPill(hint: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(56.dp).sharedKey("search"),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ------------------------------------------------------------------ chips

@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    badge: (T) -> Int = { 0 },
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        items(items, key = { label(it) }) { item ->
            val sel = item == selected
            val bg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, tween(250), label = "bg")
            val border by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outline, tween(250), label = "bd")
            val pad by animateDpAsState(if (sel) 16.dp else 14.dp, spring(0.6f, 500f), label = "p")
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(50))
                    .clickable { onSelect(item) }
                    .padding(horizontal = pad, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(item), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                val b = badge(item)
                AnimatedVisibility(b > 0, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    Box(
                        Modifier.padding(start = 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(if (b > 99) "99+" else "$b", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 28.dp, top = 18.dp, bottom = 8.dp),
    )
}

// ------------------------------------------------------------------ floating pill nav (Dialer style)

data class NavItem(val label: String, val icon: ImageVector, val badge: Int = 0)

@Composable
fun PillNavBar(items: List<NavItem>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                val sel = i == selected
                val bg by animateColorAsState(
                    if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    spring(stiffness = 500f), label = "nb")
                val iconScale by animateFloatAsState(if (sel) 1.08f else 1f, spring(0.4f, 500f), label = "is")
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable { onSelect(i) }
                        .padding(horizontal = if (sel) 18.dp else 16.dp, vertical = 14.dp)
                        .animateContentSizeSpring(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Icon(item.icon, item.label, Modifier.scale(iconScale),
                            tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.badge > 0) {
                            Box(Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(8.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error))
                        }
                    }
                    AnimatedVisibility(sel, enter = expandHorizontally(spring(0.8f, 500f)) + fadeIn(),
                        exit = shrinkHorizontally(spring(0.9f, 600f)) + fadeOut()) {
                        Text(item.label, Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

fun Modifier.animateContentSizeSpring(): Modifier =
    this.then(Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f)))

// ------------------------------------------------------------------ empty state

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "p")
    val s by pulse.animateFloat(
        0.94f, 1.06f,
        androidx.compose.animation.core.infiniteRepeatable(tween(1400), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "s",
    )
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).scale(s).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

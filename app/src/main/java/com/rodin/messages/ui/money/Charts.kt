package com.rodin.messages.ui.money

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rodin.messages.finance.Cat
import com.rodin.messages.finance.formatRupees

val Cat.icon: ImageVector
    get() = when (this) {
        Cat.FOOD -> Icons.Rounded.Restaurant
        Cat.GROCERIES -> Icons.Rounded.ShoppingBasket
        Cat.SHOPPING -> Icons.Rounded.ShoppingBag
        Cat.TRAVEL -> Icons.Rounded.DirectionsCar
        Cat.FUEL -> Icons.Rounded.LocalGasStation
        Cat.BILLS -> Icons.Rounded.Receipt
        Cat.ENTERTAINMENT -> Icons.Rounded.Movie
        Cat.HEALTH -> Icons.Rounded.LocalHospital
        Cat.EDUCATION -> Icons.Rounded.School
        Cat.RENT -> Icons.Rounded.Home
        Cat.EMI -> Icons.Rounded.AccountBalance
        Cat.INVESTMENT -> Icons.AutoMirrored.Rounded.TrendingUp
        Cat.INSURANCE -> Icons.Rounded.Shield
        Cat.CASH -> Icons.Rounded.Payments
        Cat.PEOPLE -> Icons.Rounded.Person
        Cat.INCOME -> Icons.Rounded.Work
        Cat.CASHBACK -> Icons.Rounded.Redeem
        Cat.REFUND -> Icons.AutoMirrored.Rounded.Undo
        Cat.TRANSFER -> Icons.Rounded.SwapHoriz
        Cat.OTHER -> Icons.Rounded.Category
    }

val Cat.tint: Color get() = Color(color)

/** Rupee amount that counts up/down smoothly whenever the value changes. */
@Composable
fun AnimatedRupees(paise: Long, style: TextStyle, color: Color = MaterialTheme.colorScheme.onSurface, prefix: String = "") {
    val v by animateFloatAsState(paise / 100f, tween(900, easing = FastOutSlowInEasing), label = "rupees")
    Text(prefix + formatRupees((v * 100).toLong()), style = style, color = color)
}

@Composable
fun CatIcon(cat: Cat, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(cat.tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Icon(cat.icon, cat.label, Modifier.size(size * 0.5f), tint = cat.tint) }
}

/** Ring: spent share of (spent + received), sweeping in on first draw. */
@Composable
fun SpendRing(spent: Long, received: Long, modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
    val spentColor = MaterialTheme.colorScheme.primary
    val recvColor = Color(0xFF66BB6A)
    val total = (spent + received).coerceAtLeast(1)
    val target = spent.toFloat() / total
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, spring(0.85f, 60f)) }
    Canvas(modifier.size(size)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (received > 0) {
            drawArc(recvColor, -90f + 360f * anim.value, 360f * (1 - anim.value), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawArc(spentColor, -90f, 360f * anim.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** One bar per day of the month; bars grow in with a left-to-right stagger. Today is highlighted. */
@Composable
fun DailyBars(values: LongArray, today: Int, modifier: Modifier = Modifier) {
    val progress = remember(values.contentHashCode()) { Animatable(0f) }
    LaunchedEffect(values.contentHashCode()) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val future = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val n = values.size
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val local = ((progress.value * 1.5f) - (i.toFloat() / n) * 0.5f).coerceIn(0f, 1f)
            val minH = 3.dp.toPx()
            val h = if (v == 0L) minH else (size.height * (v / max)).coerceAtLeast(minH) * local
            val color = when {
                today >= 0 && i > today -> future
                i == today -> primary
                else -> dim
            }
            drawRoundRect(
                color,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

/** Horizontal bar whose fill animates to [fraction]. */
@Composable
fun FillBar(fraction: Float, color: Color, modifier: Modifier = Modifier, delayMs: Int = 0) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(800, delayMs, FastOutSlowInEasing)) }
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(anim.value).height(8.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun CategoryRows(rows: List<Pair<Cat, Long>>, total: Long, onClick: (Cat) -> Unit) {
    Column {
        rows.forEachIndexed { i, (cat, amt) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable { onClick(cat) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatIcon(cat, 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatRupees(amt), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(Modifier.height(6.dp))
                    FillBar(amt.toFloat() / total.coerceAtLeast(1), cat.tint, delayMs = i * 70)
                }
            }
        }
    }
}

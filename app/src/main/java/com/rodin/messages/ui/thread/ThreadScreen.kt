@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.rodin.messages.ui.thread

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.telephony.SmsMessage
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rodin.messages.data.Classifier
import com.rodin.messages.data.Message
import com.rodin.messages.data.MsgStatus
import com.rodin.messages.data.SimInfo
import com.rodin.messages.ui.ThreadViewModel
import com.rodin.messages.ui.components.Avatar
import com.rodin.messages.ui.components.daySection
import com.rodin.messages.ui.components.rememberBitmap
import com.rodin.messages.ui.components.sharedKey
import kotlinx.coroutines.launch
import java.util.Calendar

private val urlRegex = Regex("(https?://|www\\.)[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+", RegexOption.IGNORE_CASE)
private val phoneRegex = Regex("(?<![\\w])(\\+?\\d[\\d \\-]{8,14}\\d)(?![\\w])")
private val REACTIONS = listOf("❤️", "😂", "👍", "😮", "😢", "🙏", "🔥")

@Composable
fun ThreadScreen(
    vm: ThreadViewModel,
    isDefault: Boolean,
    defaultSubId: Int,
    sendOnEnter: Boolean,
    textScale: Float,
    prefillText: String? = null,
    prefillImages: List<Uri> = emptyList(),
    showBack: Boolean = true,
    onBack: () -> Unit,
    onForward: (String) -> Unit,
    onRequestDefault: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onPin: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onBlock: () -> Unit,
    onDeleteThread: () -> Unit,
) {
    val ctx = LocalContext.current
    val messages by vm.messages.collectAsState()
    val conv by vm.conversation.collectAsState()
    val addresses by vm.addresses.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val sims by vm.sims.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val title = conv?.title ?: addresses.joinToString { vm.contactFor(it).display }
    val primary = addresses.firstOrNull() ?: ""
    val firstContact = remember(primary) { vm.contactFor(primary) }
    val canReply = addresses.size > 1 || !Classifier.isBusinessSender(primary)

    var text by rememberSaveable { mutableStateOf(prefillText ?: vm.draft()) }
    val images = remember { mutableStateListOf<Uri>().apply { addAll(prefillImages) } }
    var selectedSub by rememberSaveable { mutableStateOf(-2) }
    LaunchedEffect(sims, conv) {
        if (selectedSub == -2 && sims.isNotEmpty()) {
            selectedSub = when {
                defaultSubId >= 0 && sims.any { it.subId == defaultSubId } -> defaultSubId
                conv?.subId?.let { s -> sims.any { it.subId == s } } == true -> conv!!.subId
                else -> sims.first().subId
            }
        }
    }
    var actionFor by remember { mutableStateOf<Message?>(null) }
    var showSchedule by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Track which messages existed when the screen opened, so only new ones animate in.
    val initialKeys = remember { HashSet<String>() }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (loaded && !initialized) { messages.forEach { initialKeys += it.key }; initialized = true }
    }

    val listState = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }
    LaunchedEffect(messages.size) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        images.addAll(uris.filterNot { it in images })
    }

    fun doSend() {
        if (text.isBlank() && images.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.send(text, images.toList(), if (selectedSub == -2) -1 else selectedSub)
        text = ""; images.clear()
        scope.launch { listState.animateScrollToItem(0) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        // ---------------------------------------------------------------- top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                Surface(onClick = onBack, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
            }
            Avatar(
                title, firstContact.photoUri,
                Modifier.padding(start = 4.dp).sharedKey("avatar_${vm.threadId}"),
                size = 44.dp, highlighted = true, group = addresses.size > 1,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    if (addresses.size == 1 && firstContact.name != null && firstContact.lookupKey != null) append(primary)
                    else if (addresses.size > 1) append("${addresses.size} people")
                    else if (Classifier.isBusinessSender(primary)) append("Business · ${primary.uppercase()}")
                    if (conv?.muted == true) append(" · Muted")
                }
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (addresses.size == 1 && primary.any { it.isDigit() } && !Classifier.isBusinessSender(primary)) {
                Surface(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$primary"))) },
                    shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(46.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Call, "Call", Modifier.size(22.dp)) } }
                Spacer(Modifier.width(6.dp))
            }
            Box {
                Surface(onClick = { menu = true }, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreVert, "More") }
                }
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp)) {
                    val c = conv
                    DropdownMenuItem({ Text(if (c?.pinned == true) "Unpin" else "Pin") }, { onPin(c?.pinned != true); menu = false },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) })
                    DropdownMenuItem({ Text(if (c?.archived == true) "Unarchive" else "Archive") },
                        { onArchive(c?.archived != true); menu = false }, leadingIcon = { Icon(Icons.Rounded.Archive, null) })
                    DropdownMenuItem({ Text(if (c?.muted == true) "Unmute" else "Mute") }, { onMute(c?.muted != true); menu = false },
                        leadingIcon = { Icon(Icons.Rounded.NotificationsOff, null) })
                    if (addresses.size == 1) DropdownMenuItem({ Text("Block") }, { onBlock(); menu = false },
                        leadingIcon = { Icon(Icons.Rounded.Block, null) })
                    DropdownMenuItem({ Text("Delete conversation", color = MaterialTheme.colorScheme.error) },
                        { onDeleteThread(); menu = false },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }

        // ---------------------------------------------------------------- messages
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                items(reversed.size, key = { reversed[it].key }) { idx ->
                    val m = reversed[idx]
                    val older = reversed.getOrNull(idx + 1)
                    val newer = reversed.getOrNull(idx - 1)
                    val joinPrev = older != null && older.outgoing == m.outgoing && m.date - older.date < 3 * 60_000 &&
                        daySection(older.date) == daySection(m.date) && older.address == m.address
                    val joinNext = newer != null && newer.outgoing == m.outgoing && newer.date - m.date < 3 * 60_000 &&
                        newer.address == m.address
                    Column(Modifier.animateItem()) {
                        if (older == null || !sameDay(older.date, m.date)) DayChip(m.date)
                        MessageBubble(
                            m = m,
                            joinPrev = joinPrev,
                            joinNext = joinNext,
                            animate = initialized && m.key !in initialKeys,
                            showSender = addresses.size > 1 && !m.outgoing && !joinPrev,
                            senderName = if (addresses.size > 1 && !m.outgoing) vm.contactFor(m.address).display else null,
                            sim = if (sims.size > 1) sims.firstOrNull { it.subId == m.subId } else null,
                            textScale = textScale,
                            onLong = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); actionFor = m },
                            onRetry = { vm.retry(m) },
                            onImage = { viewer = it },
                            onCopyOtp = { code ->
                                ctx.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("OTP", code))
                                Toast.makeText(ctx, "Copied $code", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                showJump,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                enter = scaleIn(spring(0.5f, 500f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Surface(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 4.dp,
                    modifier = Modifier.size(44.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.KeyboardArrowDown, "Latest") } }
            }
            if (loaded && messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(title, firstContact.photoUri, size = 88.dp, highlighted = true)
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text("Say hi 👋", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---------------------------------------------------------------- composer
        when {
            !isDefault -> NoticeBar("Set Messages as your default SMS app to reply", "Set default", onRequestDefault)
            !canReply -> NoticeBar("This sender doesn't accept replies", null) {}
            else -> Composer(
                text = text,
                onText = { text = it; vm.saveDraft(it) },
                images = images,
                sims = sims,
                selectedSub = selectedSub,
                sendOnEnter = sendOnEnter,
                onCycleSim = {
                    if (sims.size > 1) {
                        val i = sims.indexOfFirst { it.subId == selectedSub }
                        selectedSub = sims[(i + 1) % sims.size].subId
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemoveImage = { images.remove(it) },
                onSend = ::doSend,
                onSchedule = { if (text.isNotBlank() && images.isEmpty()) showSchedule = true },
            )
        }
    }

    actionFor?.let { m ->
        MessageActions(
            m = m,
            onDismiss = { actionFor = null },
            onReact = { vm.react(m, it); actionFor = null },
            onCopy = {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", m.body))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show(); actionFor = null
            },
            onStar = { vm.toggleStar(m); actionFor = null },
            onForward = { onForward(m.body); actionFor = null },
            onDelete = { vm.delete(m); actionFor = null },
            onSendNow = { vm.sendScheduledNow(m); actionFor = null },
            simName = sims.firstOrNull { it.subId == m.subId }?.let { "${it.name} · ${it.carrier}" },
        )
    }

    if (showSchedule) {
        ScheduleSheet(onDismiss = { showSchedule = false }) { time ->
            vm.schedule(text, time, if (selectedSub == -2) -1 else selectedSub)
            text = ""; showSchedule = false
            Toast.makeText(ctx, "Scheduled for ${DateFormat.format("EEE, d MMM · h:mm a", time)}", Toast.LENGTH_SHORT).show()
        }
    }

    viewer?.let { uri ->
        Dialog(onDismissRequest = { viewer = null }) {
            val bmp = rememberBitmap(uri)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { viewer = null }) {
                bmp?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = a }
    val c2 = Calendar.getInstance().apply { timeInMillis = b }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DayChip(t: Long) {
    val label = when (daySection(t)) {
        "Today", "Yesterday" -> daySection(t)
        else -> DateFormat.format("EEEE, d MMMM", t).toString()
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun linkified(body: String, linkColor: Color): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium))
    data class Hit(val range: IntRange, val url: String)
    val hits = (urlRegex.findAll(body).map { Hit(it.range, if (it.value.startsWith("www", true)) "https://${it.value}" else it.value) } +
        phoneRegex.findAll(body).map { Hit(it.range, "tel:" + it.value.filter { c -> c.isDigit() || c == '+' }) })
        .sortedBy { it.range.first }
    return buildAnnotatedString {
        var i = 0
        for (h in hits) {
            if (h.range.first < i) continue
            append(body.substring(i, h.range.first))
            withLink(LinkAnnotation.Url(h.url, style)) { append(body.substring(h.range)) }
            i = h.range.last + 1
        }
        if (i < body.length) append(body.substring(i))
    }
}

@Composable
private fun MessageBubble(
    m: Message,
    joinPrev: Boolean,
    joinNext: Boolean,
    animate: Boolean,
    showSender: Boolean,
    senderName: String?,
    sim: SimInfo?,
    textScale: Float,
    onLong: () -> Unit,
    onRetry: () -> Unit,
    onImage: (Uri) -> Unit,
    onCopyOtp: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val out = m.outgoing
    val big = 24.dp
    val small = 6.dp
    val shape = if (out) RoundedCornerShape(big, if (joinPrev) small else big, if (joinNext) small else big, big)
    else RoundedCornerShape(if (joinPrev) small else big, big, big, if (joinNext) small else big)

    val scheduled = m.status == MsgStatus.SCHEDULED
    val failed = m.status == MsgStatus.FAILED
    val bg = when {
        failed -> MaterialTheme.colorScheme.errorContainer
        scheduled -> MaterialTheme.colorScheme.tertiaryContainer
        out -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        scheduled -> MaterialTheme.colorScheme.onTertiaryContainer
        out -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sendingAlpha by animateFloatAsState(if (m.status == MsgStatus.SENDING) 0.7f else 1f, tween(300), label = "sa")

    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) { if (animate) appear.animateTo(1f, spring(0.62f, 380f)) }

    Column(
        Modifier.fillMaxWidth().padding(top = if (joinPrev) 2.dp else 8.dp),
        horizontalAlignment = if (out) Alignment.End else Alignment.Start,
    ) {
        if (showSender && senderName != null) {
            Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 14.dp, bottom = 3.dp))
        }
        Box(
            Modifier.graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f) * sendingAlpha
                scaleX = 0.6f + 0.4f * v; scaleY = scaleX
                translationY = (1f - v) * 40f
                transformOrigin = TransformOrigin(if (out) 1f else 0f, 1f)
            },
        ) {
            Column(
                Modifier
                    .widthIn(max = 310.dp)
                    .clip(shape)
                    .background(bg)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(),
                        onClick = { if (failed) onRetry() },
                        onLongClick = onLong,
                    )
                    .animateContentSize(),
            ) {
                m.attachments.filter { it.mime.startsWith("image/") }.forEach { a ->
                    val bmp = rememberBitmap(a.uri)
                    Box(
                        Modifier.padding(4.dp).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .heightIn(min = 120.dp).widthIn(min = 160.dp).clickable { onImage(a.uri) },
                    ) {
                        bmp?.let { Image(it, null, Modifier.widthIn(max = 300.dp).heightIn(max = 360.dp), contentScale = ContentScale.Crop) }
                    }
                }
                m.attachments.filterNot { it.mime.startsWith("image/") }.forEach { a ->
                    Text("📎 ${a.name ?: a.mime}", color = fg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                if (m.body.isNotBlank()) {
                    if (!m.subject.isNullOrBlank()) Text(m.subject, color = fg, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
                    Text(
                        linkified(m.body, if (out && !failed && !scheduled) fg else MaterialTheme.colorScheme.primary),
                        color = fg,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp * textScale, lineHeight = 22.sp * textScale),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            // reaction badge
            androidx.compose.animation.AnimatedVisibility(
                m.reaction != null,
                modifier = Modifier.align(if (out) Alignment.BottomStart else Alignment.BottomEnd)
                    .offset(x = if (out) (-10).dp else 10.dp, y = 14.dp),
                enter = scaleIn(spring(0.35f, 500f)), exit = scaleOut(),
            ) {
                Text(
                    m.reaction ?: "",
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 15.sp,
                )
            }
        }
        if (m.reaction != null) Spacer(Modifier.height(12.dp))

        // OTP quick copy
        if (!out) {
            val otp = remember(m.body) { Classifier.extractOtp(m.body) }
            if (otp != null) {
                val shimmer = rememberInfiniteTransition(label = "o")
                val a by shimmer.animateFloat(0.75f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                Surface(
                    onClick = { onCopyOtp(otp) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = a),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy $otp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = 1.sp)
                    }
                }
            }
        }

        // meta line under the last bubble of a group
        if (!joinNext || failed || scheduled) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m.starred) Icon(Icons.Rounded.Star, null, Modifier.size(13.dp).padding(end = 2.dp), tint = Color(0xFFF2C14E))
                if (sim != null) {
                    Icon(Icons.Rounded.SimCard, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ${sim.slot + 1} · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val time = DateFormat.getTimeFormat(ctx).format(m.date).lowercase()
                AnimatedContent(m.status, transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                }, label = "st") { st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (st) {
                            MsgStatus.SENDING -> SendingDots()
                            MsgStatus.SENT -> MetaText("$time · Sent", Icons.Rounded.Check)
                            MsgStatus.DELIVERED -> MetaText("$time · Delivered", Icons.Rounded.DoneAll)
                            MsgStatus.FAILED -> MetaText("Not sent · Tap to retry", Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error)
                            MsgStatus.SCHEDULED -> MetaText("Scheduled · " + DateFormat.format("d MMM, h:mm a", m.date), Icons.Rounded.Schedule,
                                MaterialTheme.colorScheme.tertiary)
                            MsgStatus.NONE -> MetaText(time, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaText(t: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(t, style = MaterialTheme.typography.labelSmall, color = color)
    if (icon != null) Icon(icon, null, Modifier.padding(start = 4.dp).size(13.dp), tint = color)
}

@Composable
private fun SendingDots() {
    val t = rememberInfiniteTransition(label = "d")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Sending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        repeat(3) { i ->
            val y by t.animateFloat(0f, -4f, infiniteRepeatable(tween(350, delayMillis = i * 120), RepeatMode.Reverse), label = "y$i")
            Box(Modifier.padding(start = 3.dp).offset(y = y.dp).size(4.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun NoticeBar(text: String, action: String?, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.navigationBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    images: List<Uri>,
    sims: List<SimInfo>,
    selectedSub: Int,
    sendOnEnter: Boolean,
    onCycleSim: () -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSend: () -> Unit,
    onSchedule: () -> Unit,
) {
    val canSend = text.isNotBlank() || images.isNotEmpty()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        AnimatedVisibility(images.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(Modifier.padding(start = 56.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it.toString() }) { u ->
                    val bmp = rememberBitmap(u)
                    Box(Modifier.animateItem().size(84.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Surface(onClick = { onRemoveImage(u) }, shape = CircleShape, color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp), tint = Color.White) }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Surface(onClick = onAttach, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AddPhotoAlternate, "Attach image") }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = text,
                        onValueChange = onText,
                        modifier = Modifier.weight(1f).padding(vertical = 15.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default,
                        ),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) Text(if (images.isEmpty()) "Text message" else "Add a caption (MMS)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                inner()
                            }
                        },
                    )
                    if (sims.size > 1) {
                        val sim = sims.firstOrNull { it.subId == selectedSub } ?: sims.first()
                        val simColor by animateColorAsState(
                            if (sim.slot == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            label = "sc")
                        Surface(onClick = onCycleSim, shape = RoundedCornerShape(14.dp), color = simColor,
                            modifier = Modifier.padding(vertical = 8.dp)) {
                            AnimatedContent(sim, transitionSpec = {
                                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                            }, label = "sim") { s ->
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.SimCard, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${s.slot + 1}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // segment counter + send
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (text.length > 70 && images.isEmpty()) {
                    val calc = remember(text) { runCatching { SmsMessage.calculateLength(text, false) }.getOrNull() }
                    if (calc != null) Text("${calc[2]}/${calc[0]}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                val scale by animateFloatAsState(if (canSend) 1f else 0.85f, spring(0.45f, 500f), label = "sb")
                val rot by animateFloatAsState(if (canSend) 0f else -30f, spring(0.5f, 300f), label = "r")
                val color by animateColorAsState(
                    if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "c")
                Box(
                    Modifier.size(52.dp).scale(scale).clip(CircleShape).background(color)
                        .combinedClickable(enabled = canSend, onClick = onSend, onLongClick = onSchedule),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send, "Send (hold to schedule)",
                        Modifier.graphicsLayer { rotationZ = rot },
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActions(
    m: Message,
    simName: String?,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onSendNow: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var details by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (m.status != MsgStatus.SCHEDULED) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    REACTIONS.forEachIndexed { i, e ->
                        val s = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { kotlinx.coroutines.delay(i * 35L); s.animateTo(1f, spring(0.4f, 400f)) }
                        val selected = m.reaction == e
                        Box(
                            Modifier.scale(s.value).size(46.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onReact(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }
            val actions = buildList {
                if (m.status == MsgStatus.SCHEDULED) add(Triple(Icons.AutoMirrored.Rounded.Send, "Send now", onSendNow))
                add(Triple(Icons.Rounded.ContentCopy, "Copy text", onCopy))
                if (m.status != MsgStatus.SCHEDULED) add(Triple(if (m.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (m.starred) "Unstar" else "Star", onStar))
                add(Triple(Icons.Rounded.Shortcut, "Forward", onForward))
                add(Triple(Icons.Rounded.Info, "Details") { details = !details })
                add(Triple(Icons.Rounded.Delete, if (m.status == MsgStatus.SCHEDULED) "Cancel scheduled message" else "Delete", onDelete))
            }
            actions.forEachIndexed { i, (icon, label, action) ->
                val danger = icon == Icons.Rounded.Delete
                Surface(
                    onClick = action,
                    shape = com.rodin.messages.ui.components.groupShape(i, actions.size, 24.dp, 6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(18.dp))
                        Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            AnimatedVisibility(details) {
                Column(Modifier.padding(top = 14.dp, start = 8.dp)) {
                    val fmt = DateFormat.format("EEE, d MMM yyyy · h:mm:ss a", m.date)
                    Text("${if (m.outgoing) "Sent" else "Received"}: $fmt", style = MaterialTheme.typography.bodyMedium)
                    Text("Type: ${if (m.isMms) "MMS" else "SMS"}", style = MaterialTheme.typography.bodyMedium)
                    if (!m.outgoing) Text("From: ${m.address}", style = MaterialTheme.typography.bodyMedium)
                    simName?.let { Text("SIM: $it", style = MaterialTheme.typography.bodyMedium) }
                    Text("Status: ${m.status.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ScheduleSheet(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Long?>(null) }
    fun at(daysAhead: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysAhead); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val now = System.currentTimeMillis()
    val options = buildList {
        add("In 1 hour" to now + 3_600_000L)
        at(0, 21).takeIf { it > now + 60_000 }?.let { add("Tonight, 9:00 pm" to it) }
        add("Tomorrow, 9:00 am" to at(1, 9))
        add("Tomorrow, 6:00 pm" to at(1, 18))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Schedule message", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
            (options.map { it.first to { onPick(it.second) } } + ("Pick date & time" to { pickDate = true }))
                .forEachIndexed { i, (label, action) ->
                    Surface(
                        onClick = action,
                        shape = com.rodin.messages.ui.components.groupShape(i, options.size + 1, 24.dp, 6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(18.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
        }
    }
    if (pickDate) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = now)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton({ pickDate = false; pickTime = ds.selectedDateMillis ?: now }) { Text("Next") } },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(ds) }
    }
    pickTime?.let { day ->
        val ts = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton({
                    val c = Calendar.getInstance().apply {
                        timeInMillis = day
                        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = day }
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), ts.hour, ts.minute, 0)
                    }
                    pickTime = null
                    if (c.timeInMillis > System.currentTimeMillis()) onPick(c.timeInMillis)
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton({ pickTime = null }) { Text("Cancel") } },
            text = { TimePicker(ts) },
        )
    }
}

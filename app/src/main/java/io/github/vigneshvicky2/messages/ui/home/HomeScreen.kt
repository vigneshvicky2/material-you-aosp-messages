@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.vigneshvicky2.messages.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vigneshvicky2.messages.data.Category
import io.github.vigneshvicky2.messages.data.Contact
import io.github.vigneshvicky2.messages.data.Conversation
import io.github.vigneshvicky2.messages.data.SimInfo
import io.github.vigneshvicky2.messages.data.SwipeAction
import io.github.vigneshvicky2.messages.ui.MainViewModel
import io.github.vigneshvicky2.messages.ui.components.Avatar
import io.github.vigneshvicky2.messages.ui.components.BigHeader
import io.github.vigneshvicky2.messages.ui.components.ChipRow
import io.github.vigneshvicky2.messages.ui.components.CircleIconButton
import io.github.vigneshvicky2.messages.ui.components.EmptyState
import io.github.vigneshvicky2.messages.ui.components.NavItem
import io.github.vigneshvicky2.messages.ui.components.PillNavBar
import io.github.vigneshvicky2.messages.ui.components.SearchPill
import io.github.vigneshvicky2.messages.ui.components.SectionLabel
import io.github.vigneshvicky2.messages.ui.components.daySection
import io.github.vigneshvicky2.messages.ui.components.formatTime
import io.github.vigneshvicky2.messages.ui.components.groupShape
import io.github.vigneshvicky2.messages.ui.components.sharedKey
import kotlinx.coroutines.delay

private enum class ChatFilter(val label: String) { ALL("All"), UNREAD("Unread"), PERSONAL("Contacts"), ARCHIVED("Archived") }
private enum class UpdateFilter(val label: String, val cats: Set<Category>) {
    ALL("All", setOf(Category.OTP, Category.TRANSACTION, Category.PROMOTION, Category.GOVERNMENT)),
    OTP("OTP", setOf(Category.OTP)),
    TXN("Transactions", setOf(Category.TRANSACTION)),
    OFFERS("Offers", setOf(Category.PROMOTION)),
    GOVT("Govt", setOf(Category.GOVERNMENT)),
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpen: (Conversation) -> Unit,
    onOpenThreadId: (Long, List<String>) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onRequestDefault: () -> Unit,
) {
    val convs by vm.conversations.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val isDefault by vm.isDefault.collectAsState()
    val sims by vm.sims.collectAsState()
    val settings by vm.prefs.settings.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var sheetFor by remember { mutableStateOf<Conversation?>(null) }
    var chatFilter by rememberSaveable { mutableStateOf(ChatFilter.ALL) }
    var updFilter by rememberSaveable { mutableStateOf(UpdateFilter.ALL) }

    val unreadPersonal = convs.count { !it.archived && it.category == Category.PERSONAL && it.unreadCount > 0 }
    val unreadUpdates = convs.count { !it.archived && it.category in UpdateFilter.ALL.cats && it.unreadCount > 0 }
    val tabs = listOf(
        NavItem("Chats", Icons.AutoMirrored.Rounded.Chat, unreadPersonal),
        NavItem("Updates", Icons.AutoMirrored.Rounded.ReceiptLong, unreadUpdates),
        NavItem("Starred", Icons.Rounded.Star),
        NavItem("Contacts", Icons.Rounded.People),
        NavItem("Spam", Icons.Rounded.Report),
    )
    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
    LaunchedEffect(tab) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(0.9f, Spring.StiffnessMediumLow)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(180)) { -it / 6 * dir } + fadeOut(tween(150)))
            },
            label = "tabs",
        ) { t ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item(key = "header") {
                    Column(Modifier.statusBarsPadding()) {
                        BigHeader("Messages", tabs[t].label) {
                            CircleIconButton(Icons.Rounded.Settings, "Settings", Modifier.padding(top = 10.dp), onSettings)
                        }
                        Spacer(Modifier.height(16.dp))
                        SearchPill("Search messages or people", Modifier.padding(horizontal = 20.dp), onSearch)
                    }
                }
                item(key = "banner") {
                    AnimatedVisibility(!isDefault, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        DefaultAppBanner(onRequestDefault)
                    }
                }
                when (t) {
                    0 -> chatsTab(chatFilter, { chatFilter = it }, convs, loaded, sims, settings.swipeLeft, settings.swipeRight, vm, onOpen) { sheetFor = it }
                    1 -> updatesTab(updFilter, { updFilter = it }, convs, loaded, sims, settings.swipeLeft, settings.swipeRight, vm, onOpen) { sheetFor = it }
                    2 -> starredTab(vm, onOpenThreadId)
                    3 -> contactsTab(vm, onNewChat) { c -> vm.threadFor(listOf(c.number)) { onOpenThreadId(it, listOf(c.number)) } }
                    4 -> spamTab(convs, sims, vm, onOpen, onSettings) { sheetFor = it }
                }
            }
        }

        // Floating action button (Dialer's round dialpad button → compose)
        StartChatFab(
            expanded = atTop,
            visible = tab != 3,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 96.dp),
            onClick = onNewChat,
        )

        PillNavBar(
            items = tabs,
            selected = tab,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
        ) { tab = it }
    }

    sheetFor?.let { c ->
        ConversationSheet(c, vm, onDismiss = { sheetFor = null })
    }
}

// ============================================================ tabs

private fun LazyListScope.conversationSections(
    list: List<Conversation>,
    sims: List<SimInfo>,
    left: SwipeAction,
    right: SwipeAction,
    vm: MainViewModel,
    onOpen: (Conversation) -> Unit,
    onLong: (Conversation) -> Unit,
) {
    val grouped = list.groupBy { daySection(it.date) }
    grouped.forEach { (section, items) ->
        item(key = "sec_$section") { SectionLabel(section, Modifier.animateItem()) }
        items.forEachIndexed { i, c ->
            item(key = c.threadId) {
                SwipeRow(c, left, right, vm, Modifier.animateItem(), shapeIndex = i, count = items.size) {
                    ConversationCard(c, sims, groupShape(i, items.size), onClick = { onOpen(c) }, onLong = { onLong(c) })
                }
            }
        }
    }
}

private fun LazyListScope.chatsTab(
    f: ChatFilter, setF: (ChatFilter) -> Unit,
    convs: List<Conversation>, loaded: Boolean, sims: List<SimInfo>,
    left: SwipeAction, right: SwipeAction, vm: MainViewModel,
    onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit,
) {
    item(key = "chips_chats") {
        Column {
            Spacer(Modifier.height(14.dp))
            ChipRow(ChatFilter.entries, f, { it.label },
                badge = { if (it == ChatFilter.UNREAD) convs.count { c -> c.unreadCount > 0 && c.category == Category.PERSONAL && !c.archived } else 0 }) { setF(it) }
        }
    }
    val base = convs.filter { it.category == Category.PERSONAL }
    val shown = when (f) {
        ChatFilter.ALL -> base.filter { !it.archived && !it.pinned }
        ChatFilter.UNREAD -> base.filter { !it.archived && it.unreadCount > 0 }
        ChatFilter.PERSONAL -> base.filter { !it.archived && it.contacts.any { c -> c.lookupKey != null } }
        ChatFilter.ARCHIVED -> convs.filter { it.archived }
    }
    val pinned = convs.filter { it.pinned && !it.archived }
    if (f == ChatFilter.ALL && pinned.isNotEmpty()) {
        item(key = "pinned") { PinnedRow(pinned, onOpen, onLong) }
    }
    if (loaded && shown.isEmpty() && !(f == ChatFilter.ALL && pinned.isNotEmpty())) {
        item(key = "empty") {
            EmptyState(
                if (f == ChatFilter.ARCHIVED) Icons.Rounded.Archive else Icons.AutoMirrored.Rounded.Chat,
                if (f == ChatFilter.ARCHIVED) "Nothing archived" else "No conversations",
                if (f == ChatFilter.ARCHIVED) "Swipe a chat to archive it" else "Start a new chat with the button below",
            )
        }
    }
    conversationSections(shown, sims, left, right, vm, onOpen, onLong)
}


private fun LazyListScope.updatesTab(
    f: UpdateFilter, setF: (UpdateFilter) -> Unit,
    convs: List<Conversation>, loaded: Boolean, sims: List<SimInfo>,
    left: SwipeAction, right: SwipeAction, vm: MainViewModel,
    onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit,
) {
    item(key = "chips_upd") {
        Column {
            Spacer(Modifier.height(14.dp))
            ChipRow(UpdateFilter.entries, f, { it.label },
                badge = { uf -> convs.count { it.category in uf.cats && it.unreadCount > 0 && !it.archived } }) { setF(it) }
        }
    }
    val shown = convs.filter { it.category in f.cats && !it.archived }
    if (loaded && shown.isEmpty()) {
        item(key = "empty") { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "All caught up", "Bank alerts, OTPs and offers land here") }
    }
    conversationSections(shown, sims, left, right, vm, onOpen, onLong)
}

private fun LazyListScope.starredTab(vm: MainViewModel, onOpen: (Long, List<String>) -> Unit) {
    item(key = "starred") {
        val starred by vm.starred.collectAsState()
        val ctx = LocalContext.current
        Column(Modifier.padding(top = 12.dp)) {
            if (starred.isEmpty()) {
                EmptyState(Icons.Rounded.Star, "No starred messages", "Long-press any message and tap Star to keep it here")
            }
            starred.forEachIndexed { i, (c, m) ->
                Surface(
                    onClick = { onOpen(c.threadId, c.addresses) },
                    shape = groupShape(i, starred.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.title, c.contacts.first().photoUri, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(c.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(formatTime(ctx, m.date), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Star, "Unstar", tint = Color(0xFFF2C14E),
                                modifier = Modifier.size(22.dp).clip(CircleShape).combinedClickable { vm.unstar(m) })
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(m.body, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.contactsTab(vm: MainViewModel, onNewChat: () -> Unit, onContact: (Contact) -> Unit) {
    item(key = "c_new") {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).combinedClickable(onClick = onNewChat)
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(18.dp))
            Text("New group or chat", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
    }
    item(key = "c_list") {
        val contacts by vm.contacts.collectAsState()
        Column {
            contacts.forEachIndexed { i, c ->
                val prev = contacts.getOrNull(i - 1)?.display?.firstOrNull()?.uppercaseChar()
                val cur = c.display.firstOrNull()?.uppercaseChar()
                if (cur != prev) SectionLabel(cur?.toString() ?: "#")
                ContactCard(c) { onContact(c) }
            }
            if (contacts.isEmpty()) EmptyState(Icons.Rounded.People, "No contacts", "Allow contacts permission to see people here")
        }
    }
}

private fun LazyListScope.spamTab(
    convs: List<Conversation>, sims: List<SimInfo>, vm: MainViewModel,
    onOpen: (Conversation) -> Unit, onSettings: () -> Unit, onLong: (Conversation) -> Unit,
) {
    val spam = convs.filter { it.category == Category.SPAM }
    item(key = "spam_info") {
        val blocked by vm.blocked.collectAsState()
        Surface(
            onClick = onSettings,
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Spam protection", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("${blocked.size} blocked · ${spam.size} flagged chats", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
    if (spam.isEmpty()) item(key = "spam_empty") { EmptyState(Icons.Rounded.VerifiedUser, "No spam", "Suspicious and blocked chats show up here") }
    spam.forEachIndexed { i, c ->
        item(key = c.threadId) {
            ConversationCard(c, sims, groupShape(i, spam.size), Modifier.animateItem(), onClick = { onOpen(c) }, onLong = { onLong(c) })
        }
    }
}

// ============================================================ pieces

@Composable
private fun DefaultAppBanner(onClick: () -> Unit) {
    val glow = androidx.compose.animation.core.rememberInfiniteTransition(label = "g")
    val a by glow.animateFloat(0.55f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).graphicsLayer { alpha = a }.clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Make Messages your SMS app", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Needed to send, receive and organise messages", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            TextButton(onClick = onClick) { Text("Set") }
        }
    }
}

@Composable
private fun PinnedRow(pinned: List<Conversation>, onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit) {
    Column {
        SectionLabel("Pinned")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(pinned, key = { it.threadId }) { c ->
                val ring by animateColorAsState(
                    if (c.unreadCount > 0) MaterialTheme.colorScheme.primary else Color.Transparent, tween(400), label = "r")
                Column(
                    Modifier.width(72.dp).animateItem().clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { onOpen(c) }, onLongClick = { onLong(c) }).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.border(2.5.dp, ring, RoundedCornerShape(24.dp)).padding(4.dp)) {
                        Avatar(c.title, c.contacts.first().photoUri, Modifier.sharedKey("avatar_${c.threadId}"),
                            size = 54.dp, highlighted = c.unreadCount > 0, group = c.isGroup)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(c.title, Modifier.padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (c.unreadCount > 0) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun ConversationCard(
    c: Conversation,
    sims: List<SimInfo>,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val unread = c.unreadCount > 0
    // staggered entrance
    val appear = remember { Animatable(if (seen.contains(c.threadId)) 1f else 0f) }
    LaunchedEffect(c.threadId) {
        if (appear.value < 1f) {
            delay((seen.size % 12) * 28L)
            seen.add(c.threadId)
            appear.animateTo(1f, spring(0.75f, 260f))
        }
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 60f
                scaleX = 0.96f + 0.04f * appear.value
                scaleY = scaleX
            }
            .sharedKey("card_${c.threadId}")
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong()
            }),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.title, c.contacts.first().photoUri, Modifier.sharedKey("avatar_${c.threadId}"),
                size = 48.dp, highlighted = unread, group = c.isGroup)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold, fontSize = 16.sp),
                        color = if (c.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (c.pinned) Icon(Icons.Rounded.PushPin, null, Modifier.padding(start = 6.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    if (c.muted) Icon(Icons.Rounded.NotificationsOff, null, Modifier.padding(start = 6.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.hasError) Icon(Icons.Rounded.ErrorOutline, null, Modifier.padding(end = 4.dp).size(15.dp),
                        tint = MaterialTheme.colorScheme.error)
                    if (c.hasAttachment) Icon(Icons.Rounded.Image, null, Modifier.padding(end = 4.dp).size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (c.lastOutgoing) "You: " else "") + c.snippet.replace('\n', ' '),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    )
                }
                if (sims.size > 1) {
                    val sim = sims.firstOrNull { it.subId == c.subId }
                    if (sim != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SimCard, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(sim.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTime(ctx, c.date), style = MaterialTheme.typography.labelMedium,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(unread, enter = scaleIn(spring(0.4f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(
                        Modifier.widthIn(min = 22.dp).height(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (c.unreadCount > 99) "99+" else "${c.unreadCount}", color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private val seen = HashSet<Long>()

@Composable
private fun ContactCard(c: Contact, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.display, c.photoUri, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.display, style = MaterialTheme.typography.titleMedium)
                Text(c.number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.Chat, "Message", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwipeRow(
    c: Conversation, left: SwipeAction, right: SwipeAction, vm: MainViewModel,
    modifier: Modifier, shapeIndex: Int, count: Int, content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    fun perform(a: SwipeAction) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (a) {
            SwipeAction.ARCHIVE -> vm.setArchived(c, !c.archived)
            SwipeAction.DELETE -> vm.delete(c)
            SwipeAction.READ -> if (c.unreadCount > 0) vm.markRead(c) else vm.markUnread(c)
            SwipeAction.PIN -> vm.setPinned(c, !c.pinned)
            SwipeAction.NONE -> Unit
        }
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.EndToStart -> perform(left)
                SwipeToDismissBoxValue.StartToEnd -> perform(right)
                else -> Unit
            }
            false
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = right != SwipeAction.NONE,
        enableDismissFromEndToStart = left != SwipeAction.NONE,
        backgroundContent = {
            val dir = state.dismissDirection
            val action = if (dir == SwipeToDismissBoxValue.StartToEnd) right else left
            val progress = state.progress.coerceIn(0f, 1f)
            val active = state.targetValue != SwipeToDismissBoxValue.Settled
            val color by animateColorAsState(
                when {
                    !active -> MaterialTheme.colorScheme.surfaceContainerHighest
                    action == SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }, label = "sw")
            val iconScale by animateFloatAsState(if (active) 1.25f else 0.8f + progress * 0.2f, spring(0.4f, 600f), label = "is")
            val icon: ImageVector = when (action) {
                SwipeAction.ARCHIVE -> if (c.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive
                SwipeAction.DELETE -> Icons.Rounded.Delete
                SwipeAction.READ -> if (c.unreadCount > 0) Icons.Rounded.DoneAll else Icons.Rounded.MarkChatUnread
                SwipeAction.PIN -> Icons.Rounded.PushPin
                SwipeAction.NONE -> Icons.Rounded.DoneAll
            }
            Box(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 2.dp).clip(groupShape(shapeIndex, count)).background(color)
                    .padding(horizontal = 28.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(icon, action.label, Modifier.scale(iconScale), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
    ) { content() }
}

@Composable
private fun ConversationSheet(c: Conversation, vm: MainViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.padding(start = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.title, c.contacts.first().photoUri, size = 48.dp, group = c.isGroup)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(c.title, style = MaterialTheme.typography.titleLarge)
                    Text(c.addresses.joinToString(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val actions = buildList {
                add(Triple(Icons.Rounded.PushPin, if (c.pinned) "Unpin" else "Pin") { vm.setPinned(c, !c.pinned) })
                add(Triple(if (c.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    if (c.archived) "Unarchive" else "Archive") { vm.setArchived(c, !c.archived) })
                add(Triple(if (c.unreadCount > 0) Icons.Rounded.DoneAll else Icons.Rounded.MarkChatUnread,
                    if (c.unreadCount > 0) "Mark as read" else "Mark as unread") {
                    if (c.unreadCount > 0) vm.markRead(c) else vm.markUnread(c)
                })
                add(Triple(Icons.Rounded.NotificationsOff, if (c.muted) "Unmute" else "Mute notifications") { vm.setMuted(c, !c.muted) })
                add(Triple(Icons.Rounded.Report, if (c.category == Category.SPAM) "Not spam" else "Report spam") {
                    vm.markSpam(c, c.category != Category.SPAM)
                })
                if (!c.isGroup) add(Triple(Icons.Rounded.Block, "Block ${c.title}") { vm.block(c) })
            }
            actions.forEachIndexed { i, (icon, label, action) ->
                Surface(
                    onClick = { action(); onDismiss() },
                    shape = groupShape(i, actions.size + 1, 24.dp, 6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(18.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val delColor by animateColorAsState(
                if (confirmDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh, label = "d")
            Surface(
                onClick = { if (confirmDelete) { vm.delete(c); onDismiss() } else confirmDelete = true },
                shape = groupShape(actions.size, actions.size + 1, 24.dp, 6.dp),
                color = delColor,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Delete, null,
                        tint = if (confirmDelete) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(18.dp))
                    Text(if (confirmDelete) "Tap again to delete forever" else "Delete conversation",
                        color = if (confirmDelete) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun StartChatFab(expanded: Boolean, visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (visible) 1f else 0f, spring(0.55f, 400f), label = "fs")
    val corner by animateDpAsState(if (expanded) 22.dp else 32.dp, spring(0.7f, 400f), label = "fc")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier.scale(scale).sharedKey("newchat"),
    ) {
        Row(Modifier.height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Chat, "Start chat", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            AnimatedVisibility(expanded, enter = expandHorizontally(spring(0.8f, 500f)) + fadeIn(),
                exit = shrinkHorizontally(spring(0.9f, 600f)) + fadeOut()) {
                Text("Start chat", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

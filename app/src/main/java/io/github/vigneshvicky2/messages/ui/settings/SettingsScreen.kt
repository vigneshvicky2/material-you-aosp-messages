package io.github.vigneshvicky2.messages.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.messages.data.Backup
import io.github.vigneshvicky2.messages.data.Palette
import io.github.vigneshvicky2.messages.data.SwipeAction
import io.github.vigneshvicky2.messages.data.ThemeMode
import io.github.vigneshvicky2.messages.ui.MainViewModel
import io.github.vigneshvicky2.messages.ui.components.BigHeader
import io.github.vigneshvicky2.messages.ui.components.SectionLabel
import io.github.vigneshvicky2.messages.ui.components.groupShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onRequestDefault: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.prefs.settings.collectAsState()
    val sims by vm.sims.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val isDefault by vm.isDefault.collectAsState()
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { Backup.export(ctx, uri) }.getOrDefault(-1) }
            Toast.makeText(ctx, if (n >= 0) "Exported $n messages" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Backup.import(ctx, uri) }.getOrNull() }
            Toast.makeText(ctx, r?.let { "Restored ${it.messages} messages (${it.skipped} skipped)" } ?: "Import failed",
                Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Surface(onClick = onBack, shape = CircleShape, color = Color.Transparent,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp).size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
                BigHeader("Messages", "Settings")
            }
        }

        item {
            val bg by animateColorAsState(
                if (isDefault) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer, label = "d")
            Surface(
                onClick = { if (!isDefault) onRequestDefault() else ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                shape = RoundedCornerShape(28.dp), color = bg,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDefault) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.Send, null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isDefault) "Default SMS app" else "Not the default SMS app", style = MaterialTheme.typography.titleMedium)
                        Text(if (isDefault) "Messages sends and receives your SMS & MMS. Tap to change."
                            else "Tap to make Messages your SMS app. You can switch back to the old app anytime.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ------------------------------------------------ appearance
        item { SectionLabel("Appearance") }
        item {
            Group(0, 3) {
                Column(Modifier.padding(20.dp)) {
                    RowTitle(Icons.Rounded.DarkMode, "Theme")
                    Spacer(Modifier.size(14.dp))
                    Segmented(ThemeMode.entries, s.theme, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { t ->
                        vm.prefs.update { it.copy(theme = t) }
                    }
                }
            }
        }
        item {
            Group(1, 3) {
                Column(Modifier.padding(vertical = 20.dp)) {
                    RowTitle(Icons.Rounded.Palette, "Colours", Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.size(14.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(Palette.entries) { p ->
                            val sel = p == s.palette
                            val ring by animateDpAsState(if (sel) 3.dp else 0.dp, spring(0.4f, 500f), label = "r")
                            val size by animateDpAsState(if (sel) 58.dp else 50.dp, spring(0.4f, 500f), label = "s")
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(64.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(size).clip(RoundedCornerShape(18.dp))
                                            .border(ring, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(18.dp))
                                            .background(if (p == Palette.DYNAMIC) MaterialTheme.colorScheme.primary else Color(p.seed))
                                            .clickable { vm.prefs.update { it.copy(palette = p) } },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(sel) { Icon(Icons.Rounded.Check, null, tint = Color.White) }
                                    }
                                }
                                Text(p.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        item {
            Group(2, 3) {
                Column(Modifier.padding(20.dp)) {
                    RowTitle(Icons.Rounded.TextFields, "Message text size · ${(s.textScale * 100).toInt()}%")
                    Slider(s.textScale, { v -> vm.prefs.update { it.copy(textScale = (v * 20).toInt() / 20f) } }, valueRange = 0.85f..1.4f)
                    Text("The quick brown fox 🦊", style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * s.textScale))
                }
            }
        }

        // ------------------------------------------------ sending
        item { SectionLabel("Sending") }
        if (sims.size > 1) {
            item {
                Group(0, 3) {
                    Column(Modifier.padding(20.dp)) {
                        RowTitle(Icons.Rounded.SimCard, "Default SIM for new messages")
                        Spacer(Modifier.size(14.dp))
                        val opts = listOf(-1) + sims.map { it.subId }
                        Segmented(opts, s.defaultSubId, { id -> if (id == -1) "Auto" else sims.first { it.subId == id }.let { "${it.name} · ${it.carrier}" } }) { id ->
                            vm.prefs.update { it.copy(defaultSubId = id) }
                        }
                    }
                }
            }
        }
        item {
            Group(if (sims.size > 1) 1 else 0, if (sims.size > 1) 3 else 2) {
                ToggleRow(Icons.Rounded.CheckCircle, "Delivery reports", "Show when an SMS reaches the recipient", s.deliveryReports) { v ->
                    vm.prefs.update { it.copy(deliveryReports = v) }
                }
            }
        }
        item {
            Group(if (sims.size > 1) 2 else 1, if (sims.size > 1) 3 else 2) {
                ToggleRow(Icons.AutoMirrored.Rounded.KeyboardReturn, "Send with Enter key", "Enter sends instead of adding a new line", s.sendOnEnter) { v ->
                    vm.prefs.update { it.copy(sendOnEnter = v) }
                }
            }
        }

        // ------------------------------------------------ notifications
        item { SectionLabel("Notifications") }
        item {
            Group(0, 5) {
                ToggleRow(Icons.Rounded.Password, "Auto-copy OTP", "New codes go straight to the clipboard — Gboard offers them as a one-tap paste", s.otpAutoCopy) { v ->
                    vm.prefs.update { it.copy(otpAutoCopy = v) }
                }
            }
        }
        item {
            Group(1, 5) {
                ToggleRow(Icons.Rounded.ContentCopy, "Copy OTP button", "Adds a one-tap copy action to OTP notifications", s.otpCopyAction) { v ->
                    vm.prefs.update { it.copy(otpCopyAction = v) }
                }
            }
        }
        item {
            Group(2, 5) {
                ToggleRow(Icons.Rounded.ChatBubble, "Chat bubbles", "Personal chats can float as bubbles", s.bubbles) { v ->
                    vm.prefs.update { it.copy(bubbles = v) }
                }
            }
        }
        item {
            Group(3, 5) {
                ClickRow(Icons.Rounded.Notifications, "Preview notifications", "Show sample chat, OTP and bank alerts") {
                    io.github.vigneshvicky2.messages.notify.Notifier.preview(ctx)
                }
            }
        }
        item {
            Group(4, 5) {
                ClickRow(Icons.Rounded.Notifications, "Notification channels", "Sound, vibration and priority") {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            }
        }

        // ------------------------------------------------ swipe
        item { SectionLabel("Swipe actions") }
        item {
            Group(0, 2) {
                Column(Modifier.padding(20.dp)) {
                    RowTitle(Icons.Rounded.SwipeRight, "Swipe right")
                    Spacer(Modifier.size(12.dp))
                    Segmented(SwipeAction.entries, s.swipeRight, { it.label }) { a -> vm.prefs.update { it.copy(swipeRight = a) } }
                }
            }
        }
        item {
            Group(1, 2) {
                Column(Modifier.padding(20.dp)) {
                    RowTitle(Icons.Rounded.SwipeLeft, "Swipe left")
                    Spacer(Modifier.size(12.dp))
                    Segmented(SwipeAction.entries, s.swipeLeft, { it.label }) { a -> vm.prefs.update { it.copy(swipeLeft = a) } }
                }
            }
        }

        // ------------------------------------------------ blocked
        item { SectionLabel("Blocked numbers · ${blocked.size}") }
        if (blocked.isEmpty()) {
            item {
                Group(0, 1) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(if (isDefault) "No blocked numbers" else "Set as default SMS app to manage blocking",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(blocked.size) { i ->
            val n = blocked[i]
            Group(i, blocked.size) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton({ vm.unblock(n) }) { Text("Unblock") }
                }
            }
        }

        // ------------------------------------------------ backup
        item { SectionLabel("Backup") }
        item {
            Group(0, 2) {
                ClickRow(Icons.Rounded.Upload, "Export messages", "Save all SMS, stars, pins and settings to a JSON file") {
                    exporter.launch("messages-backup-${System.currentTimeMillis() / 1000}.json")
                }
            }
        }
        item {
            Group(1, 2) {
                ClickRow(Icons.Rounded.Download, "Restore from backup", if (isDefault) "Duplicates are skipped automatically"
                    else "Needs Messages to be the default SMS app") {
                    if (isDefault) importer.launch(arrayOf("application/json", "*/*")) else onRequestDefault()
                }
            }
        }
        item {
            Text(
                "Messages ${io.github.vigneshvicky2.messages.BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth().padding(28.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Group(i: Int, n: Int, content: @Composable () -> Unit) {
    Surface(
        shape = groupShape(i, n),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    ) { content() }
}

@Composable
private fun RowTitle(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            val o = options[i]
            val sel = o == selected
            val bg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "b")
            val fg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "f")
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(o) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text(label(o), color = fg, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

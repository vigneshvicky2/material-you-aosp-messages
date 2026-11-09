package io.github.vigneshvicky2.messages.ui.newchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.messages.data.Contact
import io.github.vigneshvicky2.messages.ui.MainViewModel
import io.github.vigneshvicky2.messages.ui.components.Avatar
import io.github.vigneshvicky2.messages.ui.components.SectionLabel
import io.github.vigneshvicky2.messages.ui.components.groupShape
import io.github.vigneshvicky2.messages.ui.components.sharedKey

@Composable
fun NewChatScreen(vm: MainViewModel, onBack: () -> Unit, onStart: (List<String>) -> Unit) {
    val contacts by vm.contacts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(false) }
    var dialpad by rememberSaveable { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Contact>() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(dialpad) { runCatching { focus.requestFocus() } }

    val q = query.trim()
    val digits = q.filter { it.isDigit() || it == '+' }
    val isNumber = digits.length >= 3 && q.all { it.isDigit() || it in "+ -()" }
    val filtered = remember(contacts, q) {
        if (q.isEmpty()) contacts else contacts.filter {
            it.display.contains(q, true) || (digits.length >= 2 && it.number.filter { c -> c.isDigit() }.contains(digits.trimStart('+')))
        }
    }

    fun choose(c: Contact) {
        if (group) {
            if (picked.any { it.number == c.number }) picked.removeAll { it.number == c.number } else picked.add(c)
            query = ""
        } else onStart(listOf(c.number))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sharedKey("newchat")) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onBack, shape = CircleShape, color = MaterialTheme.colorScheme.background, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Messages", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (group) "New group" else "New chat", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            // "To" pill
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(56.dp),
            ) {
                Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("To", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (isNumber) onStart(listOf(digits)) }),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("Name or number", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge)
                                inner()
                            }
                        },
                    )
                    Surface(onClick = { dialpad = !dialpad }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (dialpad) Icons.Rounded.Keyboard else Icons.Rounded.Dialpad, "Toggle keypad")
                        }
                    }
                }
            }
            AnimatedVisibility(picked.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 6.dp)) {
                    items(picked, key = { it.number }) { c ->
                        Surface(
                            onClick = { picked.remove(c) }, shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.animateItem(),
                        ) {
                            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(c.display, c.photoUri, size = 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(c.display, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 120.dp)) {
                item(key = "group") {
                    val bg by animateColorAsState(
                        if (group) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "g")
                    Surface(
                        onClick = { group = !group; if (!group) picked.clear() },
                        shape = RoundedCornerShape(26.dp), color = bg,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Create group", style = MaterialTheme.typography.titleMedium)
                                Text(if (group) "Pick people below" else "Send one message to many people",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (isNumber) {
                    item(key = "num") {
                        Surface(
                            onClick = { choose(Contact(null, digits)) },
                            shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(digits, null, size = 48.dp, highlighted = true)
                                Spacer(Modifier.width(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (group) "Add $digits" else "Send to $digits", style = MaterialTheme.typography.titleMedium)
                                    Text("Unsaved number", style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.AutoMirrored.Rounded.Chat, null)
                            }
                        }
                    }
                }
                val sections = filtered.groupBy { it.display.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
                sections.forEach { (letter, list) ->
                    item(key = "l_$letter") { SectionLabel(letter.toString(), Modifier.animateItem()) }
                    list.forEachIndexed { i, c ->
                        item(key = "c_${c.number}") {
                            val sel = picked.any { it.number == c.number }
                            val bg by animateColorAsState(
                                if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "s")
                            Surface(
                                onClick = { choose(c) },
                                shape = groupShape(i, list.size),
                                color = bg,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).animateItem(),
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        Avatar(c.display, c.photoUri, size = 48.dp)
                                        androidx.compose.animation.AnimatedVisibility(sel, Modifier.align(Alignment.BottomEnd),
                                            enter = scaleIn(spring(0.4f, 600f)), exit = scaleOut()) {
                                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp)
                                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                                                tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.width(18.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.display, style = MaterialTheme.typography.titleMedium)
                                        Text(c.number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            picked.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(20.dp),
            enter = scaleIn(spring(0.5f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut(),
        ) {
            Surface(onClick = { onStart(picked.map { it.number }) }, shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Start with ${picked.size}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

package com.rodin.messages.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rodin.messages.data.Conversation
import com.rodin.messages.ui.MainViewModel
import com.rodin.messages.ui.components.Avatar
import com.rodin.messages.ui.components.EmptyState
import com.rodin.messages.ui.components.formatTime
import com.rodin.messages.ui.components.groupShape
import com.rodin.messages.ui.components.sharedKey

@Composable
fun SearchScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (Conversation) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val results by vm.searchResults.collectAsState()
    val focus = remember { FocusRequester() }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) { vm.search(q) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(16.dp).height(56.dp).sharedKey("search"),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onBack, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    q, { q = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) Text("Search messages or people", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                if (q.isNotEmpty()) {
                    Surface(onClick = { q = "" }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                }
            }
        }
        if (q.isNotBlank() && results.isEmpty()) {
            EmptyState(Icons.Rounded.SearchOff, "No results", "Try a different name, number or word")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(results, key = { i, (c, m) -> "${c.threadId}_${m?.key ?: "c"}_$i" }) { i, (c, m) ->
                Surface(
                    onClick = { onOpen(c) },
                    shape = groupShape(i, results.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).animateItem(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.title, c.contacts.first().photoUri, size = 48.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(highlight(c.title, q), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                highlight((m?.body ?: c.snippet).replace('\n', ' '), q),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatTime(ctx, m?.date ?: c.date), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun highlight(text: String, q: String) = buildAnnotatedString {
    if (q.isBlank()) { append(text); return@buildAnnotatedString }
    var i = 0
    // center the snippet around the first hit
    val first = text.indexOf(q, ignoreCase = true)
    val src = if (first > 40) "…" + text.substring(first - 30) else text
    while (i < src.length) {
        val j = src.indexOf(q, i, ignoreCase = true)
        if (j < 0) { append(src.substring(i)); break }
        append(src.substring(i, j))
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
            append(src.substring(j, j + q.length))
        }
        i = j + q.length
    }
}

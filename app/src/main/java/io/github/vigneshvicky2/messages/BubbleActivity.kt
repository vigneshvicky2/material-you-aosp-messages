package io.github.vigneshvicky2.messages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import io.github.vigneshvicky2.messages.data.Prefs
import io.github.vigneshvicky2.messages.data.SmsRepo
import io.github.vigneshvicky2.messages.notify.Notifier
import io.github.vigneshvicky2.messages.ui.theme.MessagesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compact conversation shown inside a floating notification bubble. */
class BubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val threadId = intent.getLongExtra(Notifier.EXTRA_THREAD, -1)
        if (threadId < 0) { finish(); return }
        val prefs = Prefs.get(this)
        val repo = SmsRepo.get(this)
        setContent {
            val s by prefs.settings.collectAsState()
            var addrs by remember { mutableStateOf<List<String>?>(null) }
            LaunchedEffect(threadId) {
                addrs = withContext(Dispatchers.IO) { repo.conversation(threadId)?.addresses.orEmpty() }
            }
            MessagesTheme(s.theme, s.palette) {
                addrs?.let { a ->
                    ThreadRoute(
                        threadId, a, null, repo.isDefaultSmsApp(), s.defaultSubId, s.sendOnEnter, s.textScale, Prefill(),
                        showBack = false,
                        onBack = { finish() },
                        onForward = {},
                        onRequestDefault = {},
                        onDeleted = { finish() },
                    )
                }
            }
        }
    }
}

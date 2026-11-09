@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rodin.messages

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rodin.messages.data.Prefs
import com.rodin.messages.notify.Notifier
import com.rodin.messages.ui.MainViewModel
import com.rodin.messages.ui.ThreadViewModel
import com.rodin.messages.ui.components.LocalSharedScope
import com.rodin.messages.ui.components.ProvideAnimScope
import com.rodin.messages.ui.home.HomeScreen
import com.rodin.messages.ui.home.SearchScreen
import com.rodin.messages.ui.newchat.NewChatScreen
import com.rodin.messages.ui.settings.SettingsScreen
import com.rodin.messages.ui.theme.MessagesTheme
import com.rodin.messages.ui.thread.ThreadScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Content handed over from share / sendto intents. */
data class Prefill(val text: String? = null, val images: List<Uri> = emptyList())

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
    }
    private val pendingIntent = mutableStateOf<Intent?>(null)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshEnvironment()
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshEnvironment()
    }

    fun requestDefaultSms() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingIntent.value = intent
        permLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
        setContent {
            val settings by vm.prefs.settings.collectAsState()
            MessagesTheme(settings.theme, settings.palette) {
                AppNav(vm, pendingIntent.value, onIntentHandled = { pendingIntent.value = null }, onRequestDefault = ::requestDefaultSms)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    override fun onResume() {
        super.onResume()
        vm.refreshEnvironment()
    }
}

private fun encodeAddrs(a: List<String>) = Uri.encode(a.joinToString(","))
private fun decodeAddrs(s: String?) = s?.let(Uri::decode)?.split(",")?.filter { it.isNotBlank() }.orEmpty()

fun NavHostController.openThread(id: Long, addrs: List<String>) =
    navigate("thread/$id?a=${encodeAddrs(addrs)}") { launchSingleTop = true }

@Composable
private fun AppNav(vm: MainViewModel, intent: Intent?, onIntentHandled: () -> Unit, onRequestDefault: () -> Unit) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val prefill = androidx.compose.runtime.remember { mutableStateOf(Prefill()) }
    val settings by vm.prefs.settings.collectAsState()
    val isDefault by vm.isDefault.collectAsState()

    // ---- incoming intents
    LaunchedEffect(intent) {
        val i = intent ?: return@LaunchedEffect
        val threadId = i.getLongExtra(Notifier.EXTRA_THREAD, -1)
        when {
            threadId > 0 -> {
                val conv = withContext(Dispatchers.IO) { vm.repo.conversation(threadId) }
                nav.openThread(threadId, conv?.addresses.orEmpty())
            }
            i.action == Intent.ACTION_SENDTO || (i.action == Intent.ACTION_VIEW && i.data?.scheme?.startsWith("sms") == true) ||
                (i.action == Intent.ACTION_SEND && i.data != null) -> {
                val addrs = i.data?.schemeSpecificPart?.substringBefore("?")?.split(",", ";")?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }.orEmpty()
                val body = i.getStringExtra("sms_body") ?: i.getStringExtra(Intent.EXTRA_TEXT)
                    ?: i.data?.getQueryParameterSafe("body")
                prefill.value = Prefill(body)
                if (addrs.isNotEmpty()) {
                    val id = withContext(Dispatchers.IO) { vm.repo.threadIdFor(addrs) }
                    nav.openThread(id, addrs)
                } else nav.navigate("new")
            }
            i.action == Intent.ACTION_SEND -> {
                val text = i.getStringExtra(Intent.EXTRA_TEXT)
                @Suppress("DEPRECATION")
                val img = if (i.type?.startsWith("image/") == true)
                    (i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)) else null
                prefill.value = Prefill(text, listOfNotNull(img))
                nav.navigate("new")
            }
        }
        onIntentHandled()
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = "home",
                enterTransition = { slideInHorizontally(spring(0.9f, 400f)) { it / 4 } + fadeIn(tween(220)) },
                exitTransition = { scaleOut(tween(250), targetScale = 0.94f) + fadeOut(tween(200)) },
                popEnterTransition = { scaleIn(tween(250), initialScale = 0.94f) + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(spring(0.9f, 400f)) { it / 4 } + fadeOut(tween(180)) },
            ) {
                composable("home") {
                    ProvideAnimScope(this) {
                        HomeScreen(
                            vm = vm,
                            onOpen = { c -> nav.openThread(c.threadId, c.addresses) },
                            onOpenThreadId = { id, a -> nav.openThread(id, a) },
                            onNewChat = { nav.navigate("new") },
                            onSettings = { nav.navigate("settings") },
                            onSearch = { nav.navigate("search") },
                            onRequestDefault = onRequestDefault,
                        )
                    }
                }
                composable("search") {
                    ProvideAnimScope(this) {
                        SearchScreen(vm, onBack = { nav.popBackStack() }) { c ->
                            nav.popBackStack(); nav.openThread(c.threadId, c.addresses)
                        }
                    }
                }
                composable("settings") {
                    ProvideAnimScope(this) { SettingsScreen(vm, onBack = { nav.popBackStack() }, onRequestDefault = onRequestDefault) }
                }
                composable("new") {
                    ProvideAnimScope(this) {
                        NewChatScreen(vm, onBack = { nav.popBackStack() }) { addrs ->
                            scope.launch {
                                val id = withContext(Dispatchers.IO) { vm.repo.threadIdFor(addrs) }
                                nav.popBackStack()
                                nav.openThread(id, addrs)
                            }
                        }
                    }
                }
                composable(
                    "thread/{id}?a={a}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("a") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val addrs = decodeAddrs(entry.arguments?.getString("a"))
                    val p = prefill.value
                    LaunchedEffect(Unit) { prefill.value = Prefill() }
                    ProvideAnimScope(this) {
                        ThreadRoute(
                            id, addrs, vm, isDefault, settings.defaultSubId, settings.sendOnEnter, settings.textScale, p,
                            showBack = true,
                            onBack = { nav.popBackStack() },
                            onForward = { text -> prefill.value = Prefill(text); nav.navigate("new") },
                            onRequestDefault = onRequestDefault,
                            onDeleted = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private fun Uri.getQueryParameterSafe(key: String): String? = runCatching {
    Uri.parse("x://h/?" + (query ?: "")).getQueryParameter(key)
}.getOrNull()

@Composable
fun ThreadRoute(
    id: Long,
    addrs: List<String>,
    mainVm: MainViewModel?,
    isDefault: Boolean,
    defaultSubId: Int,
    sendOnEnter: Boolean,
    textScale: Float,
    prefill: Prefill,
    showBack: Boolean,
    onBack: () -> Unit,
    onForward: (String) -> Unit,
    onRequestDefault: () -> Unit,
    onDeleted: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val tvm: ThreadViewModel = viewModel(key = "thread_$id") { ThreadViewModel(app, id, addrs) }
    val prefs = Prefs.get(app)
    val scope = rememberCoroutineScope()
    ThreadScreen(
        vm = tvm,
        isDefault = isDefault,
        defaultSubId = defaultSubId,
        sendOnEnter = sendOnEnter,
        textScale = textScale,
        prefillText = prefill.text,
        prefillImages = prefill.images,
        showBack = showBack,
        onBack = onBack,
        onForward = onForward,
        onRequestDefault = onRequestDefault,
        onArchive = { prefs.setArchived(id, it); if (it) onBack() },
        onPin = { prefs.setPinned(id, it) },
        onMute = { prefs.setMuted(id, it) },
        onBlock = {
            scope.launch(Dispatchers.IO) {
                tvm.addresses.value.forEach { tvm.repo.block(it) }
                prefs.setCategory(id, com.rodin.messages.data.Category.SPAM)
            }
            mainVm?.refreshEnvironment()
        },
        onDeleteThread = {
            scope.launch(Dispatchers.IO) { tvm.repo.deleteThread(id) }
            onDeleted()
        },
    )
}


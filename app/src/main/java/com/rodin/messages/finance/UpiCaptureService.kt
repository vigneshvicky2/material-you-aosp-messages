package com.rodin.messages.finance

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads completed-payment screens in Google Pay and super.money and records them as transactions.
 * The system only delivers events from the two packages declared in res/xml/upi_capture.xml.
 */
class UpiCaptureService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Runnable? = null
    private var lastSignature = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in APPS) return
        // Screens animate in; wait until they settle, then read once.
        pending?.let(handler::removeCallbacks)
        pending = Runnable { scan(pkg) }.also { handler.postDelayed(it, 700) }
    }

    private fun scan(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val texts = ArrayList<String>()
        collect(root, texts, 0)
        if (texts.isEmpty()) return
        val sig = texts.hashCode()
        if (sig == lastSignature) return
        lastSignature = sig

        if (learning(this) && texts.any { it.contains("₹") }) {
            // Learning mode: log the screen so its layout can be supported precisely (local logcat only)
            Log.d(TAG, "[$pkg] " + texts.joinToString(" | "))
        }
        val t = UpiScreenParser.parse(texts) ?: return
        scope.launch {
            runCatching { FinanceRepo.get(this@UpiCaptureService).ingestScreen(t, pkg) }
                .onSuccess { added -> if (learning(this@UpiCaptureService)) Log.d(TAG, "parsed=$t added=$added") }
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 40 || out.size > 400) return
        if (!node.isPassword) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrEmpty() && desc != node.text?.toString()?.trim()) out.add(desc)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out, depth + 1) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "UpiCapture"
        const val GPAY = "com.google.android.apps.nbu.paisa.user"
        const val SUPERMONEY = "money.super.payments"
        val APPS = setOf(GPAY, SUPERMONEY)

        fun appName(pkg: String) = when (pkg) { GPAY -> "Google Pay"; SUPERMONEY -> "super.money"; else -> pkg }

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, UpiCaptureService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("upi_capture", Context.MODE_PRIVATE)
        fun learning(ctx: Context) = prefs(ctx).getBoolean("learning", false)
        fun setLearning(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("learning", on).apply()
    }
}

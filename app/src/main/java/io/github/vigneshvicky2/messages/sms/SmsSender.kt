package io.github.vigneshvicky2.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import io.github.vigneshvicky2.messages.data.Prefs
import io.github.vigneshvicky2.messages.data.ScheduledMessage
import io.github.vigneshvicky2.messages.data.SmsRepo
import io.github.vigneshvicky2.messages.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmsSender {
    const val ACTION_SENT = "io.github.vigneshvicky2.messages.SMS_SENT"
    const val ACTION_DELIVERED = "io.github.vigneshvicky2.messages.SMS_DELIVERED"

    fun smsManager(ctx: Context, subId: Int): SmsManager {
        val base = ctx.getSystemService(SmsManager::class.java)
        val id = if (subId >= 0) subId else SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (id >= 0) base.createForSubscriptionId(id) else base
    }

    /** Sends [body] to each address (group = individual SMS, like AOSP). Returns thread id. */
    fun send(ctx: Context, addresses: List<String>, body: String, subId: Int): Long {
        val repo = SmsRepo.get(ctx)
        val threadId = repo.threadIdFor(addresses)
        val delivery = Prefs.get(ctx).settings.value.deliveryReports
        val sm = smsManager(ctx, subId)
        val effectiveSub = if (subId >= 0) subId else SubscriptionManager.getDefaultSmsSubscriptionId()
        for (addr in addresses) {
            val uri = repo.insertOutgoingSms(addr, body, effectiveSub, threadId)
            val parts = sm.divideMessage(body)
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent>()
            val msgId = uri?.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis()
            parts.indices.forEach { i ->
                sent += pi(ctx, ACTION_SENT, uri, i, parts.size, (msgId * 16 + i).toInt())
                delivered += pi(ctx, ACTION_DELIVERED, uri, i, parts.size, (msgId * 16 + i).toInt() + 8)
            }
            try {
                if (parts.size > 1) {
                    sm.sendMultipartTextMessage(addr, null, parts, sent, if (delivery) delivered else null)
                } else {
                    sm.sendTextMessage(addr, null, body, sent[0], if (delivery) delivered[0] else null)
                }
            } catch (e: Exception) {
                uri?.let { markFailed(ctx, it) }
            }
        }
        return threadId
    }

    private fun pi(ctx: Context, action: String, uri: Uri?, part: Int, total: Int, req: Int): PendingIntent {
        val i = Intent(ctx, SmsStatusReceiver::class.java).apply {
            this.action = action
            data = uri
            putExtra("part", part)
            putExtra("total", total)
        }
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    fun markFailed(ctx: Context, uri: Uri) {
        runCatching {
            ctx.contentResolver.update(uri, ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            }, null, null)
        }
    }

    /** Retry a failed SMS: delete the failed row and send again. */
    fun retry(ctx: Context, smsId: Long) {
        val cr = ctx.contentResolver
        val uri = Uri.parse("content://sms/$smsId")
        cr.query(uri, arrayOf("address", "body", "sub_id"), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val addr = c.getString(0); val body = c.getString(1); val sub = c.getInt(2)
                cr.delete(uri, null, null)
                send(ctx, listOf(addr), body, sub)
            }
        }
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val uri = intent.data ?: return
        val part = intent.getIntExtra("part", 0)
        val total = intent.getIntExtra("total", 1)
        val cr = ctx.contentResolver
        when (intent.action) {
            SmsSender.ACTION_SENT -> {
                if (resultCode != android.app.Activity.RESULT_OK) {
                    SmsSender.markFailed(ctx, uri)
                    Notifier.notifyFailed(ctx, uri)
                } else if (part == total - 1) {
                    // don't override a failure from an earlier part
                    val failed = cr.query(uri, arrayOf("type"), null, null, null)?.use {
                        it.moveToFirst() && it.getInt(0) == Telephony.Sms.MESSAGE_TYPE_FAILED
                    } ?: false
                    if (!failed) cr.update(uri, ContentValues().apply {
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                    }, null, null)
                }
            }
            SmsSender.ACTION_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu") ?: return
                val format = intent.getStringExtra("format")
                val status = runCatching { SmsMessage.createFromPdu(pdu, format).status }.getOrDefault(0)
                val v = when {
                    status == 0 -> Telephony.Sms.STATUS_COMPLETE
                    status >= 64 -> Telephony.Sms.STATUS_FAILED
                    else -> Telephony.Sms.STATUS_PENDING
                }
                if (part == total - 1 || v == Telephony.Sms.STATUS_FAILED) {
                    cr.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, v) }, null, null)
                }
            }
        }
    }
}

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val pending = goAsync()
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", -1))
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A delivery may contain messages from one sender (multipart) — group by address.
                msgs.groupBy { it.displayOriginatingAddress ?: "" }.forEach { (addr, parts) ->
                    val body = parts.joinToString("") { it.displayMessageBody ?: "" }
                    val repo = SmsRepo.get(ctx)
                    repo.insertIncomingSms(addr, body, parts[0].timestampMillis, subId, parts[0].serviceCenterAddress)
                    Notifier.notifyIncoming(ctx, addr)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class HeadlessSmsSendService : android.app.Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val data = intent?.data
        if (!text.isNullOrBlank() && data != null) {
            val recipients = data.schemeSpecificPart.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
            if (recipients.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    SmsSender.send(this@HeadlessSmsSendService, recipients, text, -1)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

object Scheduler {
    fun schedule(ctx: Context, m: ScheduledMessage) {
        Prefs.get(ctx).addScheduled(m)
        arm(ctx, m)
    }

    fun arm(ctx: Context, m: ScheduledMessage) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, m.id)
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m.time, pi) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m.time, pi) }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
        Prefs.get(ctx).removeScheduled(id)
    }

    private fun pending(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, ScheduledSendReceiver::class.java).putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun rearmAll(ctx: Context) {
        val now = System.currentTimeMillis()
        Prefs.get(ctx).scheduled().forEach { m ->
            if (m.time <= now) sendNow(ctx, m) else arm(ctx, m)
        }
    }

    fun sendNow(ctx: Context, m: ScheduledMessage) {
        Prefs.get(ctx).removeScheduled(m.id)
        SmsSender.send(ctx, m.addresses, m.body, m.subId)
    }
}

class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val m = Prefs.get(ctx).scheduled().firstOrNull { it.id == id } ?: return
        val p = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Scheduler.sendNow(ctx, m) } finally { p.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.rearmAll(ctx)
    }
}

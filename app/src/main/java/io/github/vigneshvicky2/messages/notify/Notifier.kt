package io.github.vigneshvicky2.messages.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import io.github.vigneshvicky2.messages.BubbleActivity
import io.github.vigneshvicky2.messages.MainActivity
import io.github.vigneshvicky2.messages.R
import io.github.vigneshvicky2.messages.data.Category
import io.github.vigneshvicky2.messages.data.Classifier
import io.github.vigneshvicky2.messages.data.Contact
import io.github.vigneshvicky2.messages.data.Prefs
import io.github.vigneshvicky2.messages.data.SmsRepo
import io.github.vigneshvicky2.messages.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Notifier {
    const val CH_MESSAGES = "messages"
    const val CH_UPDATES = "updates"
    const val CH_FAILED = "failed"
    const val KEY_REPLY = "reply"
    const val EXTRA_THREAD = "thread_id"

    /** Copies an OTP to the clipboard, flagged sensitive so Android hides it in previews. */
    fun copyOtp(ctx: Context, otp: String) {
        val clip = ClipData.newPlainText("OTP", otp)
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        runCatching { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip) }
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Personal messages and OTPs"
            setAllowBubbles(true)
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_UPDATES, "Updates & offers", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Bank alerts, transactions and promotional messages"
        })
        nm.createNotificationChannel(NotificationChannel(CH_FAILED, "Failed messages", NotificationManager.IMPORTANCE_HIGH))
    }

    private val avatarColors = intArrayOf(
        0xFF3E5A8C.toInt(), 0xFF6A4C93.toInt(), 0xFF2E7D6B.toInt(), 0xFF9C4A5E.toInt(), 0xFF8C6A2E.toInt(), 0xFF3D6F8E.toInt(),
    )

    fun letterBitmap(name: String, size: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = avatarColors[(name.hashCode() and 0x7FFFFFFF) % avatarColors.size]
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.color = Color.WHITE
        p.textSize = size * 0.45f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
        val y = size / 2f - (p.descent() + p.ascent()) / 2
        c.drawText(letter, size / 2f, y, p)
        return bmp
    }

    private fun personIcon(ctx: Context, contact: Contact): IconCompat {
        contact.photoUri?.let { uri ->
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s)?.let { return IconCompat.createWithBitmap(it) }
                }
            }
        }
        return IconCompat.createWithBitmap(letterBitmap(contact.display))
    }

    fun openThreadIntent(ctx: Context, threadId: Long): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_THREAD, threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(ctx, threadId.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun shortcutId(threadId: Long) = "thread_$threadId"

    private fun pushShortcut(ctx: Context, threadId: Long, title: String, person: Person, icon: IconCompat) {
        runCatching {
            val sm = ctx.getSystemService(ShortcutManager::class.java)
            val si = ShortcutInfo.Builder(ctx, shortcutId(threadId))
                .setShortLabel(title)
                .setLongLived(true)
                .setIcon(icon.toIcon(ctx))
                .setPerson(person.toAndroidPerson())
                .setCategories(setOf("android.shortcut.conversation"))
                .setIntent(Intent(ctx, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW; putExtra(EXTRA_THREAD, threadId)
                })
                .build()
            sm.pushDynamicShortcut(si)
        }
    }

    /** Build/refresh the notification for the thread the given address belongs to. */
    fun notifyIncoming(ctx: Context, address: String) {
        val repo = SmsRepo.get(ctx)
        val threadId = runCatching { repo.threadIdFor(listOf(address)) }.getOrNull() ?: return
        show(ctx, threadId)
    }

    fun show(ctx: Context, threadId: Long) {
        val repo = SmsRepo.get(ctx)
        val prefs = Prefs.get(ctx)
        val conv = repo.conversation(threadId) ?: return
        if (conv.archived && conv.muted) return
        val unread = repo.messages(threadId, 30).filter { !it.outgoing && !it.read }.takeLast(8)
        if (unread.isEmpty()) { cancel(ctx, threadId); return }

        val me = Person.Builder().setName("You").build()
        val contact = conv.contacts.first()
        val icon = personIcon(ctx, contact)
        val sender = Person.Builder()
            .setName(conv.title)
            .setIcon(icon)
            .setKey(conv.primaryAddress)
            .setImportant(conv.category == Category.PERSONAL)
            .build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(if (conv.isGroup) conv.title else null)
            .setGroupConversation(conv.isGroup)
        unread.forEach { m ->
            val p = if (conv.isGroup) Person.Builder().setName(repo.contacts.lookup(m.address).display).build() else sender
            style.addMessage(m.body.ifBlank { "📎 Attachment" }, m.date, p)
        }

        val personal = conv.category == Category.PERSONAL || conv.category == Category.OTP
        val channel = if (personal) CH_MESSAGES else CH_UPDATES
        pushShortcut(ctx, threadId, conv.title, sender, icon)

        val reqBase = threadId.toInt() * 8
        val replyPi = PendingIntent.getBroadcast(
            ctx, reqBase + 1,
            Intent(ctx, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.REPLY)
                .putExtra(EXTRA_THREAD, threadId).putExtra("addresses", conv.addresses.toTypedArray())
                .putExtra("sub", conv.subId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val readPi = PendingIntent.getBroadcast(
            ctx, reqBase + 2,
            Intent(ctx, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.MARK_READ)
                .putExtra(EXTRA_THREAD, threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setStyle(style)
            .setShortcutId(shortcutId(threadId))
            .setContentIntent(openThreadIntent(ctx, threadId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(unread.last().date)
            .setNumber(unread.size)
            .setOnlyAlertOnce(false)
            .setSilent(conv.muted)
            .setPriority(if (personal) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        val otp = Classifier.extractOtp(unread.last().body)
        val autoCopied = otp != null && prefs.settings.value.otpAutoCopy &&
            System.currentTimeMillis() - unread.last().date < 10 * 60_000
        if (autoCopied) {
            copyOtp(ctx, otp!!)
            b.setSubText("Code copied · paste it anywhere")
        }
        if (otp != null && prefs.settings.value.otpCopyAction) {
            val copyPi = PendingIntent.getBroadcast(
                ctx, reqBase + 3,
                Intent(ctx, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.COPY_OTP)
                    .putExtra(EXTRA_THREAD, threadId).putExtra("otp", otp),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(NotificationCompat.Action.Builder(null, "Copy $otp", copyPi).build())
        }
        if (!Classifier.isBusinessSender(conv.primaryAddress) || conv.isGroup) {
            val ri = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
            b.addAction(
                NotificationCompat.Action.Builder(null, "Reply", replyPi)
                    .addRemoteInput(ri)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build(),
            )
        }
        b.addAction(
            NotificationCompat.Action.Builder(null, "Mark as read", readPi)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false).build(),
        )

        if (prefs.settings.value.bubbles && personal) {
            val bubbleIntent = PendingIntent.getActivity(
                ctx, reqBase + 4,
                Intent(ctx, BubbleActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("messages://thread/$threadId")).putExtra(EXTRA_THREAD, threadId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            b.setBubbleMetadata(
                NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
                    .setDesiredHeight(640)
                    .setSuppressNotification(false)
                    .build(),
            )
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(threadId.toInt(), b.build()) }
    }

    /**
     * Posts sample notifications (no database writes) so the look can be previewed:
     * a personal chat, an OTP and a bank transaction alert.
     */
    fun preview(ctx: Context) {
        val now = System.currentTimeMillis()
        val me = Person.Builder().setName("You").build()
        val open = PendingIntent.getActivity(ctx, 7700, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(id: Int, notifId: Int, act: String, extra: Intent.() -> Unit = {}): PendingIntent = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, NotificationActionReceiver::class.java).setAction(act)
                .putExtra(EXTRA_THREAD, -1L).putExtra("notif_id", notifId).apply(extra),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        // 1) personal conversation
        val priyaIcon = IconCompat.createWithBitmap(letterBitmap("Priya"))
        val priya = Person.Builder().setName("Priya").setIcon(priyaIcon).setKey("demo_priya").setImportant(true).build()
        pushShortcut(ctx, -77L, "Priya", priya, priyaIcon)
        val chat = NotificationCompat.MessagingStyle(me)
            .addMessage("Hey! Are you free this evening? 🙂", now - 60_000, priya)
            .addMessage("Let's grab dinner at 8 — the new place near the station", now, priya)
        val replyPi = action(7701, 990001, NotificationActionReceiver.REPLY)
        val b1 = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif).setStyle(chat).setShortcutId(shortcutId(-77L))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(now).setNumber(2).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(NotificationCompat.Action.Builder(null, "Reply", replyPi)
                .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build())
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).build())
            .addAction(NotificationCompat.Action.Builder(null, "Mark as read", action(7702, 990001, NotificationActionReceiver.MARK_READ))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build())

        // 2) OTP
        val bankIcon = IconCompat.createWithBitmap(letterBitmap("HDFCBK"))
        val bank = Person.Builder().setName("HDFCBK").setIcon(bankIcon).setKey("demo_bank").build()
        pushShortcut(ctx, -78L, "HDFCBK", bank, bankIcon)
        val otpText = "482913 is your OTP for a transaction of Rs. 2,499.00 at AMAZON on your HDFC Bank card ending 4821. Valid for 5 mins. Do not share it with anyone."
        val otp = Classifier.extractOtp(otpText) ?: "482913"
        val previewAuto = Prefs.get(ctx).settings.value.otpAutoCopy
        if (previewAuto) copyOtp(ctx, otp)
        val b2 = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif)
            .setStyle(NotificationCompat.MessagingStyle(me).addMessage(otpText, now, bank))
            .setShortcutId(shortcutId(-78L))
            .apply { if (previewAuto) setSubText("Code copied · paste it anywhere") }
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(now).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(NotificationCompat.Action.Builder(null, "Copy $otp",
                action(7703, 990002, NotificationActionReceiver.COPY_OTP) { putExtra("otp", otp) }).build())
            .addAction(NotificationCompat.Action.Builder(null, "Mark as read", action(7704, 990002, NotificationActionReceiver.MARK_READ))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build())

        // 3) transaction alert on the quieter Updates channel
        val b3 = NotificationCompat.Builder(ctx, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notif)
            .setStyle(NotificationCompat.MessagingStyle(me).addMessage(
                "Rs.350.00 debited from A/c XX4821 on 08-11-26 to VPA swiggy@icici (UPI Ref 431298765012). Avl Bal Rs.12,840.55 -HDFC Bank",
                now, bank))
            .setShortcutId(shortcutId(-78L))
            .setContentIntent(open).setAutoCancel(true).setWhen(now)
            .addAction(NotificationCompat.Action.Builder(null, "Mark as read", action(7705, 990003, NotificationActionReceiver.MARK_READ)).build())

        val nm = NotificationManagerCompat.from(ctx)
        runCatching {
            nm.notify(990001, b1.build())
            nm.notify(990002, b2.build())
            nm.notify(990003, b3.build())
        }
    }

    fun cancel(ctx: Context, threadId: Long) {
        NotificationManagerCompat.from(ctx).cancel(threadId.toInt())
    }

    fun notifyFailed(ctx: Context, uri: Uri) {
        val cr = ctx.contentResolver
        val isMms = uri.toString().startsWith("content://mms")
        var threadId = -1L
        runCatching {
            cr.query(uri, arrayOf("thread_id"), null, null, null)?.use { if (it.moveToFirst()) threadId = it.getLong(0) }
        }
        if (threadId < 0) return
        val title = SmsRepo.get(ctx).conversation(threadId)?.title ?: "message"
        val n = NotificationCompat.Builder(ctx, CH_FAILED)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Message not sent")
            .setContentText("Couldn't send ${if (isMms) "MMS" else "SMS"} to $title. Tap to retry.")
            .setContentIntent(openThreadIntent(ctx, threadId))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify((threadId + 900000).toInt(), n) }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val REPLY = "io.github.vigneshvicky2.messages.REPLY"
        const val MARK_READ = "io.github.vigneshvicky2.messages.MARK_READ"
        const val COPY_OTP = "io.github.vigneshvicky2.messages.COPY_OTP"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val threadId = intent.getLongExtra(Notifier.EXTRA_THREAD, -1)
        // Preview notifications carry their own id: dismiss and only run harmless parts.
        val previewId = intent.getIntExtra("notif_id", -1)
        if (previewId > 0) {
            NotificationManagerCompat.from(ctx).cancel(previewId)
            if (intent.action == COPY_OTP) intent.getStringExtra("otp")?.let { otp ->
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OTP", otp))
                Toast.makeText(ctx, "Copied $otp", Toast.LENGTH_SHORT).show()
            }
            if (intent.action == REPLY) Toast.makeText(ctx, "Preview: reply not sent", Toast.LENGTH_SHORT).show()
            return
        }
        when (intent.action) {
            COPY_OTP -> {
                val otp = intent.getStringExtra("otp") ?: return
                Notifier.copyOtp(ctx, otp)
                Toast.makeText(ctx, "Copied $otp", Toast.LENGTH_SHORT).show()
                val p = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { SmsRepo.get(ctx).markRead(threadId); Notifier.cancel(ctx, threadId) } finally { p.finish() }
                }
            }
            MARK_READ -> {
                val p = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { SmsRepo.get(ctx).markRead(threadId); Notifier.cancel(ctx, threadId) } finally { p.finish() }
                }
            }
            REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()
                val addrs = intent.getStringArrayExtra("addresses")?.toList().orEmpty()
                if (text.isNullOrBlank() || addrs.isEmpty()) return
                val sub = intent.getIntExtra("sub", -1)
                val p = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SmsSender.send(ctx, addrs, text, sub)
                        SmsRepo.get(ctx).markRead(threadId)
                        Notifier.cancel(ctx, threadId)
                    } finally { p.finish() }
                }
            }
        }
    }
}

package com.rodin.messages.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.FileProvider
import com.rodin.messages.data.SmsRepo
import com.rodin.messages.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

object Mms {
    const val ACTION_SENT = "com.rodin.messages.MMS_SENT"
    const val ACTION_DOWNLOADED = "com.rodin.messages.MMS_DOWNLOADED"
    private const val MAX_IMAGE_BYTES = 280 * 1024
    private val MMS_SERVICE_PKGS = listOf("com.android.mms.service", "com.android.phone")

    private fun dir(ctx: Context) = File(ctx.cacheDir, "mms").apply { mkdirs() }
    private fun authority(ctx: Context) = "${ctx.packageName}.files"

    /** Load + downscale an image so it fits carrier MMS limits. */
    fun compressImage(ctx: Context, uri: Uri): ByteArray {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        var maxDim = 1600
        var bmp: Bitmap = ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val s = info.size
            val scale = maxOf(s.width, s.height).toFloat() / maxDim
            if (scale > 1f) d.setTargetSize((s.width / scale).toInt(), (s.height / scale).toInt())
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_IMAGE_BYTES || (quality <= 35 && maxDim <= 640)) return out.toByteArray()
            if (quality > 50) quality -= 15 else {
                maxDim = (maxDim * 0.75f).toInt()
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.75f).toInt(), (bmp.height * 0.75f).toInt(), true)
            }
        }
    }

    fun send(ctx: Context, addresses: List<String>, text: String, images: List<Uri>, subId: Int): Long {
        val repo = SmsRepo.get(ctx)
        val threadId = repo.threadIdFor(addresses)
        val parts = ArrayList<Pdu.Part>()
        images.forEachIndexed { i, u ->
            runCatching { parts += Pdu.Part("image/jpeg", compressImage(ctx, u), "image$i.jpg", "image$i") }
        }
        if (text.isNotBlank()) parts += Pdu.Part("text/plain", text.toByteArray(), "text.txt", "text")
        val effectiveSub = if (subId >= 0) subId else SubscriptionManager.getDefaultSmsSubscriptionId()

        val mmsUri = persist(ctx, threadId, box = 4, from = null, to = addresses, parts = parts,
            subject = null, read = true, subId = effectiveSub, mType = 128, trId = null, msgId = null, date = null)

        val file = File(dir(ctx), "send_${System.currentTimeMillis()}.pdu")
        file.writeBytes(Pdu.encodeSendReq(addresses, parts))
        val contentUri = FileProvider.getUriForFile(ctx, authority(ctx), file)
        MMS_SERVICE_PKGS.forEach { runCatching { ctx.grantUriPermission(it, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }

        val pi = PendingIntent.getBroadcast(
            ctx, file.hashCode(),
            Intent(ctx, MmsStatusReceiver::class.java).apply {
                action = ACTION_SENT
                data = mmsUri
                putExtra("file", file.absolutePath)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        try {
            SmsSender.smsManager(ctx, effectiveSub).sendMultimediaMessage(ctx, contentUri, null, null, pi)
        } catch (e: Exception) {
            mmsUri?.let { setBox(ctx, it, 5) }
        }
        return threadId
    }

    fun setBox(ctx: Context, uri: Uri, box: Int) {
        runCatching { ctx.contentResolver.update(uri, ContentValues().apply { put("msg_box", box) }, null, null) }
    }

    fun download(ctx: Context, notif: Pdu.Msg, subId: Int) {
        val location = notif.contentLocation ?: return
        val file = File(dir(ctx), "dl_${System.currentTimeMillis()}.pdu").apply { createNewFile() }
        val contentUri = FileProvider.getUriForFile(ctx, authority(ctx), file)
        MMS_SERVICE_PKGS.forEach {
            runCatching {
                ctx.grantUriPermission(it, contentUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val pi = PendingIntent.getBroadcast(
            ctx, file.hashCode(),
            Intent(ctx, MmsStatusReceiver::class.java).apply {
                action = ACTION_DOWNLOADED
                putExtra("file", file.absolutePath)
                putExtra("sub", subId)
                putExtra("location", location)
                putExtra("from", notif.from)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        SmsSender.smsManager(ctx, subId).downloadMultimediaMessage(ctx, location, contentUri, null, pi)
    }

    /** Write an MMS (with addresses and parts) into the telephony provider. */
    fun persist(
        ctx: Context, threadId: Long, box: Int, from: String?, to: List<String>, parts: List<Pdu.Part>,
        subject: String?, read: Boolean, subId: Int, mType: Int, trId: String?, msgId: String?, date: Long?,
    ): Uri? {
        val cr = ctx.contentResolver
        val v = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, (date ?: (System.currentTimeMillis() / 1000)))
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, if (read) 1 else 0)
            put(Telephony.Mms.SEEN, if (read) 1 else 0)
            put(Telephony.Mms.MESSAGE_TYPE, mType)
            put(Telephony.Mms.MMS_VERSION, 18)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.SUBSCRIPTION_ID, subId)
            put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.contentType.startsWith("text/") }) 1 else 0)
            subject?.let { put(Telephony.Mms.SUBJECT, it); put(Telephony.Mms.SUBJECT_CHARSET, 106) }
            trId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            msgId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
        }
        val uri = runCatching { cr.insert(Telephony.Mms.CONTENT_URI, v) }.getOrNull() ?: return null
        val id = uri.lastPathSegment ?: return uri

        fun addr(a: String, type: Int) = runCatching {
            cr.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
                put("address", a); put("type", type); put("charset", 106); put("msg_id", id)
            })
        }
        addr(from ?: "insert-address-token", 137)
        to.forEach { addr(it, 151) }

        parts.forEach { p ->
            val pv = ContentValues().apply {
                put("mid", id)
                put("ct", p.contentType)
                p.name?.let { put("cl", it); put("name", it) }
                p.cid?.let { put("cid", "<$it>") }
                if (p.contentType.startsWith("text/")) { put("chset", 106); put("text", p.text) }
            }
            val partUri = runCatching { cr.insert(Uri.parse("content://mms/$id/part"), pv) }.getOrNull()
            if (partUri != null && !p.contentType.startsWith("text/")) {
                runCatching { cr.openOutputStream(partUri)?.use { it.write(p.data) } }
            }
        }
        return uri
    }
}

class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", -1))
        val msg = Pdu.parse(data) ?: return
        if (msg.type != Pdu.MT_NOTIFICATION_IND) return
        val p = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Mms.download(ctx, msg, subId) } catch (_: Exception) { } finally { p.finish() }
        }
    }
}

class MmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val file = intent.getStringExtra("file")?.let(::File)
        val ok = resultCode == android.app.Activity.RESULT_OK
        when (intent.action) {
            Mms.ACTION_SENT -> {
                intent.data?.let { Mms.setBox(ctx, it, if (ok) 2 else 5) }
                if (!ok) intent.data?.let { Notifier.notifyFailed(ctx, it) }
                file?.delete()
            }
            Mms.ACTION_DOWNLOADED -> {
                val p = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (ok && file != null && file.length() > 0) {
                            val msg = Pdu.parse(file.readBytes())
                            if (msg != null && msg.type == Pdu.MT_RETRIEVE_CONF) {
                                val from = msg.from ?: intent.getStringExtra("from") ?: ""
                                val repo = SmsRepo.get(ctx)
                                val others = (msg.to.filterNot { it.isBlank() } + from).distinct()
                                    .filterNot { isSelf(ctx, it) }
                                val recipients = if (others.isEmpty()) listOf(from) else others
                                val tid = repo.threadIdFor(recipients)
                                Mms.persist(
                                    ctx, tid, box = 1, from = from, to = msg.to, parts = msg.parts,
                                    subject = msg.subject, read = false, subId = intent.getIntExtra("sub", -1),
                                    mType = 132, trId = msg.transactionId, msgId = msg.messageId, date = msg.date,
                                )
                                // remove the framework's placeholder notification row, if any
                                intent.getStringExtra("location")?.let { loc ->
                                    runCatching {
                                        ctx.contentResolver.delete(Telephony.Mms.CONTENT_URI, "m_type=130 AND ct_l=?", arrayOf(loc))
                                    }
                                }
                                Notifier.notifyIncoming(ctx, from)
                            }
                        }
                    } finally {
                        file?.delete(); p.finish()
                    }
                }
            }
        }
    }

    private fun isSelf(ctx: Context, a: String): Boolean = runCatching {
        val sm = ctx.getSystemService(SubscriptionManager::class.java)
        @Suppress("MissingPermission")
        sm.activeSubscriptionInfoList.orEmpty().any { info ->
            val n = runCatching { sm.getPhoneNumber(info.subscriptionId) }.getOrNull().orEmpty()
            n.isNotBlank() && n.takeLast(10) == a.filter { it.isDigit() }.takeLast(10)
        }
    }.getOrDefault(false)
}

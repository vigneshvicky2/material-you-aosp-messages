package com.rodin.messages.data

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.Telephony
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SmsRepo(private val ctx: Context) {
    private val cr get() = ctx.contentResolver
    val contacts = ContactsRepo(ctx)
    val prefs = Prefs.get(ctx)

    fun isDefaultSmsApp(): Boolean {
        val held = runCatching {
            ctx.getSystemService(android.app.role.RoleManager::class.java)
                .isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        }.getOrDefault(false)
        return held || Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
    }

    /** Emits Unit whenever SMS/MMS provider data changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        cr.registerContentObserver(Uri.parse("content://mms-sms/"), true, obs)
        cr.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        trySend(Unit)
        awaitClose { cr.unregisterContentObserver(obs) }
    }

    private fun canonicalAddresses(): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            cr.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)
                ?.use { c -> while (c.moveToNext()) map[c.getLong(0)] = c.getString(1) ?: "" }
        }
        return map
    }

    fun conversations(): List<Conversation> {
        val canon = canonicalAddresses()
        val pinned = prefs.pinned()
        val archived = prefs.archived()
        val muted = prefs.muted()
        val overrides = prefs.categoryOverrides()

        // unread per thread
        val unread = HashMap<Long, Int>()
        runCatching {
            cr.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), "read=0 AND type=1", null, null)
                ?.use { c -> while (c.moveToNext()) unread.merge(c.getLong(0), 1, Int::plus) }
            cr.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID), "read=0 AND msg_box=1", null, null)
                ?.use { c -> while (c.moveToNext()) unread.merge(c.getLong(0), 1, Int::plus) }
        }
        // last sub id + direction per thread
        val lastInfo = HashMap<Long, Pair<Int, Boolean>>()
        runCatching {
            cr.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.SUBSCRIPTION_ID, Telephony.Sms.TYPE),
                null, null, "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getLong(0)
                    if (!lastInfo.containsKey(t)) lastInfo[t] = c.getInt(1) to (c.getInt(2) != 1)
                }
            }
        }

        val out = ArrayList<Conversation>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        cr.query(
            uri,
            arrayOf("_id", "date", "message_count", "recipient_ids", "snippet", "error", "has_attachment"),
            null, null, "date DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val tid = c.getLong(0)
                val count = c.getInt(2)
                if (count == 0) continue
                val ids = (c.getString(3) ?: "").split(" ").mapNotNull { it.toLongOrNull() }
                val addrs = ids.mapNotNull { canon[it] }.filter { it.isNotBlank() }
                if (addrs.isEmpty()) continue
                val cts = addrs.map { contacts.lookup(it) }
                val snippet = c.getString(4) ?: ""
                val isContact = cts.any { it.lookupKey != null }
                val cat = overrides[tid]
                    ?: if (addrs.size > 1) Category.PERSONAL else Classifier.classify(addrs[0], snippet, isContact)
                val li = lastInfo[tid]
                out += Conversation(
                    threadId = tid,
                    addresses = addrs,
                    contacts = cts,
                    snippet = snippet,
                    date = c.getLong(1),
                    unreadCount = unread[tid] ?: 0,
                    messageCount = count,
                    hasError = c.getInt(5) != 0,
                    hasAttachment = c.getInt(6) != 0,
                    category = cat,
                    pinned = tid in pinned,
                    archived = tid in archived,
                    muted = tid in muted,
                    subId = li?.first ?: -1,
                    lastOutgoing = li?.second ?: false,
                )
            }
        }
        return out
    }

    fun conversation(threadId: Long): Conversation? = conversations().firstOrNull { it.threadId == threadId }

    fun messages(threadId: Long, limit: Int = 800): List<Message> {
        val starred = prefs.starred()
        val reactions = prefs.reactions()
        val list = ArrayList<Message>()
        cr.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("_id", "address", "body", "date", "type", "read", "status", "sub_id"),
            "thread_id=?", arrayOf(threadId.toString()), "date DESC LIMIT $limit",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val type = c.getInt(4)
                val st = c.getInt(6)
                val status = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MsgStatus.SENDING
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> MsgStatus.FAILED
                    Telephony.Sms.MESSAGE_TYPE_SENT -> when {
                        st == Telephony.Sms.STATUS_COMPLETE -> MsgStatus.DELIVERED
                        st == Telephony.Sms.STATUS_FAILED -> MsgStatus.FAILED
                        else -> MsgStatus.SENT
                    }
                    else -> MsgStatus.NONE
                }
                val key = "s$id"
                list += Message(
                    key = key, id = id, isMms = false, threadId = threadId,
                    address = c.getString(1) ?: "", body = c.getString(2) ?: "",
                    date = c.getLong(3), outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                    read = c.getInt(5) != 0, status = status, subId = c.getInt(7),
                    starred = key in starred, reaction = reactions[key],
                )
            }
        }
        cr.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "date", "msg_box", "read", "sub_id", "sub", "m_type"),
            "thread_id=?", arrayOf(threadId.toString()), "date DESC LIMIT $limit",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val box = c.getInt(2)
                val mType = c.getInt(6)
                val (text, atts) = mmsParts(id)
                val notDownloaded = mType == 130 // m-notification-ind
                val key = "m$id"
                list += Message(
                    key = key, id = id, isMms = true, threadId = threadId,
                    address = if (box == 1) mmsFrom(id) else "",
                    body = if (notDownloaded) "Multimedia message — downloading…" else text,
                    date = c.getLong(1) * 1000, outgoing = box != 1,
                    read = c.getInt(3) != 0,
                    status = when (box) {
                        4 -> MsgStatus.SENDING; 5 -> MsgStatus.FAILED; 2 -> MsgStatus.SENT; else -> MsgStatus.NONE
                    },
                    subId = c.getInt(4), attachments = atts, subject = c.getString(5),
                    starred = key in starred, reaction = reactions[key],
                )
            }
        }
        prefs.scheduled().filter { it.threadId == threadId }.forEach { s ->
            list += Message(
                key = "q${s.id}", id = s.id, isMms = false, threadId = threadId, address = "",
                body = s.body, date = s.time, outgoing = true, read = true,
                status = MsgStatus.SCHEDULED, subId = s.subId,
            )
        }
        list.sortBy { it.date }
        return list
    }

    private fun mmsParts(mmsId: Long): Pair<String, List<Attachment>> {
        val sb = StringBuilder()
        val atts = ArrayList<Attachment>()
        runCatching {
            cr.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text", "name", "cl"),
                "mid=?", arrayOf(mmsId.toString()), null)?.use { c ->
                while (c.moveToNext()) {
                    val ct = c.getString(1) ?: continue
                    when {
                        ct == "text/plain" -> c.getString(2)?.let { if (sb.isNotEmpty()) sb.append('\n'); sb.append(it) }
                        ct == "application/smil" -> Unit
                        else -> atts += Attachment(
                            Uri.parse("content://mms/part/${c.getLong(0)}"), ct, c.getString(3) ?: c.getString(4),
                        )
                    }
                }
            }
        }
        return sb.toString() to atts
    }

    private fun mmsFrom(mmsId: Long): String = runCatching {
        cr.query(Uri.parse("content://mms/$mmsId/addr"), arrayOf("address"), "type=137", null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: ""

    // ---------------- writes (require default SMS role) ----------------

    fun markRead(threadId: Long) {
        runCatching {
            val v = ContentValues().apply { put("read", 1); put("seen", 1) }
            cr.update(Telephony.Sms.CONTENT_URI, v, "thread_id=? AND read=0", arrayOf(threadId.toString()))
            cr.update(Telephony.Mms.CONTENT_URI, v, "thread_id=? AND read=0", arrayOf(threadId.toString()))
        }
    }

    fun markUnread(threadId: Long) {
        runCatching {
            cr.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), "thread_id=? AND type=1", arrayOf(threadId.toString()), "date DESC LIMIT 1")
                ?.use { c ->
                    if (c.moveToFirst()) cr.update(Uri.parse("content://sms/${c.getLong(0)}"),
                        ContentValues().apply { put("read", 0) }, null, null)
                }
        }
    }

    fun deleteThread(threadId: Long) {
        runCatching { cr.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null) }
        prefs.setPinned(threadId, false); prefs.setArchived(threadId, false)
    }

    fun deleteMessage(m: Message) {
        when {
            m.status == MsgStatus.SCHEDULED -> prefs.removeScheduled(m.id)
            m.isMms -> runCatching { cr.delete(Uri.parse("content://mms/${m.id}"), null, null) }
            else -> runCatching { cr.delete(Uri.parse("content://sms/${m.id}"), null, null) }
        }
    }

    fun threadIdFor(addresses: Collection<String>): Long =
        Telephony.Threads.getOrCreateThreadId(ctx, addresses.toSet())

    fun insertIncomingSms(address: String, body: String, sentAt: Long, subId: Int, serviceCenter: String?): Uri? {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            serviceCenter?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
        }
        return runCatching { cr.insert(Telephony.Sms.Inbox.CONTENT_URI, v) }.getOrNull()
    }

    fun insertOutgoingSms(address: String, body: String, subId: Int, threadId: Long): Uri? {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        return runCatching { cr.insert(Telephony.Sms.Outbox.CONTENT_URI, v) }.getOrNull()
    }

    fun search(query: String): List<Pair<Conversation, Message?>> {
        if (query.isBlank()) return emptyList()
        val convs = conversations()
        val byThread = convs.associateBy { it.threadId }
        val out = ArrayList<Pair<Conversation, Message?>>()
        val q = query.trim()
        convs.filter { c -> c.title.contains(q, true) || c.addresses.any { it.contains(q) } }.forEach { out += it to null }
        runCatching {
            cr.query(Telephony.Sms.CONTENT_URI, arrayOf("_id", "thread_id", "body", "date", "type", "address"),
                "body LIKE ?", arrayOf("%$q%"), "date DESC LIMIT 100")?.use { c ->
                while (c.moveToNext()) {
                    val conv = byThread[c.getLong(1)] ?: continue
                    out += conv to Message(
                        key = "s${c.getLong(0)}", id = c.getLong(0), isMms = false, threadId = conv.threadId,
                        address = c.getString(5) ?: "", body = c.getString(2) ?: "", date = c.getLong(3),
                        outgoing = c.getInt(4) != 1, read = true, status = MsgStatus.NONE, subId = -1,
                    )
                }
            }
        }
        return out
    }

    fun starredMessages(): List<Pair<Conversation, Message>> {
        val keys = prefs.starred()
        if (keys.isEmpty()) return emptyList()
        val convs = conversations().associateBy { it.threadId }
        val smsIds = keys.filter { it.startsWith("s") }.mapNotNull { it.drop(1).toLongOrNull() }
        if (smsIds.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Conversation, Message>>()
        cr.query(Telephony.Sms.CONTENT_URI, arrayOf("_id", "thread_id", "body", "date", "type", "address"),
            "_id IN (${smsIds.joinToString(",")})", null, "date DESC")?.use { c ->
            while (c.moveToNext()) {
                val conv = convs[c.getLong(1)] ?: continue
                out += conv to Message(
                    key = "s${c.getLong(0)}", id = c.getLong(0), isMms = false, threadId = conv.threadId,
                    address = c.getString(5) ?: "", body = c.getString(2) ?: "", date = c.getLong(3),
                    outgoing = c.getInt(4) != 1, read = true, status = MsgStatus.NONE, subId = -1, starred = true,
                )
            }
        }
        return out
    }

    // ---------------- blocking ----------------
    fun canBlock(): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(ctx) }.getOrDefault(false)

    fun isBlocked(number: String): Boolean =
        runCatching { BlockedNumberContract.isBlocked(ctx, number) }.getOrDefault(false)

    fun block(number: String) {
        runCatching {
            cr.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) })
        }
    }

    fun unblock(number: String) {
        runCatching { BlockedNumberContract.unblock(ctx, number) }
    }

    fun blockedNumbers(): List<String> = runCatching {
        val out = ArrayList<String>()
        cr.query(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)
            ?.use { c -> while (c.moveToNext()) out += c.getString(0) }
        out
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var inst: SmsRepo? = null
        fun get(ctx: Context): SmsRepo = inst ?: synchronized(this) {
            inst ?: SmsRepo(ctx.applicationContext).also { inst = it }
        }
    }
}

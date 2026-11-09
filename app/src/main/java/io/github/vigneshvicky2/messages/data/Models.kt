package io.github.vigneshvicky2.messages.data

import android.net.Uri

enum class Category(val label: String) {
    PERSONAL("Personal"),
    OTP("OTP"),
    TRANSACTION("Transactions"),
    PROMOTION("Offers"),
    GOVERNMENT("Govt"),
    SPAM("Spam"),
}

data class Contact(
    val name: String?,
    val number: String,
    val photoUri: Uri? = null,
    val lookupKey: String? = null,
) {
    val display: String get() = name ?: number
}

data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val contacts: List<Contact>,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val hasError: Boolean,
    val hasAttachment: Boolean,
    val category: Category,
    val pinned: Boolean,
    val archived: Boolean,
    val muted: Boolean,
    val subId: Int,
    val lastOutgoing: Boolean,
) {
    val title: String get() = contacts.joinToString(", ") { it.display }
    val isGroup: Boolean get() = addresses.size > 1
    val primaryAddress: String get() = addresses.firstOrNull() ?: ""
    val isRead: Boolean get() = unreadCount == 0
}

enum class MsgStatus { NONE, SENDING, SENT, DELIVERED, FAILED, SCHEDULED }

data class Attachment(
    val uri: Uri,
    val mime: String,
    val name: String?,
)

data class Message(
    /** Unique key across SMS/MMS ("s123" / "m45" / "q7" scheduled) */
    val key: String,
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val read: Boolean,
    val status: MsgStatus,
    val subId: Int,
    val attachments: List<Attachment> = emptyList(),
    val subject: String? = null,
    val starred: Boolean = false,
    val reaction: String? = null,
)

data class SimInfo(
    val subId: Int,
    val slot: Int,
    val name: String,
    val carrier: String,
    val color: Int,
)

data class ScheduledMessage(
    val id: Long,
    val threadId: Long,
    val addresses: List<String>,
    val body: String,
    val time: Long,
    val subId: Int,
)

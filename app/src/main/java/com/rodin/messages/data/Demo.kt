package com.rodin.messages.data

import android.content.Context
import com.rodin.messages.BuildConfig

/**
 * Screenshot / demo mode (debug builds only).
 *
 * Replaces every read of the SMS database, contacts and SIMs with made-up data and turns every write
 * (send, delete, mark read, block…) into a no-op, so screenshots never contain real messages.
 *
 *   adb shell am start -n com.rodin.messages.debug/com.rodin.messages.MainActivity --ez demo true
 *   adb shell am start -n com.rodin.messages.debug/com.rodin.messages.MainActivity --ez demo false
 */
object Demo {
    @Volatile var on: Boolean = false
        private set

    fun init(ctx: Context) {
        on = BuildConfig.DEBUG && ctx.getSharedPreferences("demo", Context.MODE_PRIVATE).getBoolean("on", false)
    }

    fun set(ctx: Context, enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        ctx.getSharedPreferences("demo", Context.MODE_PRIVATE).edit().putBoolean("on", enabled).apply()
        on = enabled
    }

    // ------------------------------------------------------------------ data

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR
    private val now get() = System.currentTimeMillis()

    private const val JIO = 9001
    private const val VI = 9002

    val sims = listOf(
        SimInfo(JIO, 0, "SIM 1", "Jio", 0),
        SimInfo(VI, 1, "SIM 2", "Vi", 0),
    )

    // Obviously fictional numbers (+91 90000 0xxxx)
    private fun c(name: String, n: Int) = Contact(name, "+91900000%04d".format(n), null, "demo-$n")

    val contacts = listOf(
        c("Aarav Mehta", 11), c("Amma", 12), c("Arjun Raman", 13), c("Divya Nair", 14), c("Karthik S", 15),
        c("Meera Iyer", 16), c("Nikhil Rao", 17), c("Priya Sharma", 18), c("Rahul Verma", 19), c("Sneha Pillai", 20),
    ).sortedBy { it.display }

    private fun byName(n: String) = contacts.first { it.name == n }

    private fun biz(sender: String) = Contact(Classifier.prettySender(sender), sender, null, null)

    private data class Thread(
        val id: Long, val people: List<Contact>, val cat: Category, val pinned: Boolean = false,
        val muted: Boolean = false, val unread: Int = 0, val sub: Int = JIO, val msgs: List<Message>,
    )

    private var nextId = 1L
    private fun m(thread: Long, from: Contact?, body: String, ago: Long, status: MsgStatus = MsgStatus.DELIVERED,
                  read: Boolean = true, sub: Int = JIO, starred: Boolean = false, reaction: String? = null): Message {
        val id = nextId++
        return Message(
            key = "d$id", id = id, isMms = false, threadId = thread, address = from?.number ?: "",
            body = body, date = now - ago, outgoing = from == null, read = read,
            status = if (from == null) status else MsgStatus.NONE, subId = sub, starred = starred, reaction = reaction,
        )
    }

    private fun threads(): List<Thread> {
        nextId = 1
        val priya = byName("Priya Sharma"); val amma = byName("Amma"); val arjun = byName("Arjun Raman")
        val karthik = byName("Karthik S"); val meera = byName("Meera Iyer"); val rahul = byName("Rahul Verma")
        val divya = byName("Divya Nair"); val nikhil = byName("Nikhil Rao")
        val hdfc = biz("AX-HDFCBK-T"); val iob = biz("JD-IOBCHN-S"); val swiggy = biz("VM-SWIGGY-P")
        val govt = biz("CP-GOVTIN-G"); val amazon = biz("VK-AMAZON-T"); val jio = biz("JM-JIOINF-S")
        val spam = Contact(null, "+919000099999", null, null)
        return listOf(
            Thread(9_000_001, listOf(priya), Category.PERSONAL, pinned = true, unread = 2, msgs = listOf(
                m(9_000_001, priya, "Are you still coming to the farewell tonight? 🎉", 26 * HOUR),
                m(9_000_001, null, "Yes! Leaving office by 7", 25 * HOUR + 50 * MIN, reaction = "👍"),
                m(9_000_001, priya, "Perfect. Booked a table at Little Italy for 8:30", 25 * HOUR + 40 * MIN, starred = true),
                m(9_000_001, null, "Sounds great, see you there", 25 * HOUR + 38 * MIN, status = MsgStatus.DELIVERED),
                m(9_000_001, priya, "That was so much fun 😄 sending you the photos", 3 * HOUR, reaction = "❤️"),
                m(9_000_001, null, "Thanks! Loved the cake", 2 * HOUR + 55 * MIN),
                m(9_000_001, priya, "Dinner at 8 again this Friday?", 12 * MIN, read = false),
                m(9_000_001, priya, "Rahul and Meera are in 🍕", 11 * MIN, read = false),
            )),
            Thread(9_000_002, listOf(amma), Category.PERSONAL, pinned = true, msgs = listOf(
                m(9_000_002, amma, "Did you eat?", 5 * HOUR),
                m(9_000_002, null, "Yes Amma, had lunch with the team", 4 * HOUR + 50 * MIN),
                m(9_000_002, amma, "Call me when you reach home 🙂", 40 * MIN),
            )),
            Thread(9_000_003, listOf(hdfc), Category.OTP, unread = 1, msgs = listOf(
                m(9_000_003, hdfc, "482913 is your OTP for a transaction of Rs. 2,499.00 at AMAZON on your HDFC Bank card ending 4821. Valid for 5 mins. Do not share it with anyone.", 4 * MIN, read = false),
            )),
            Thread(9_000_004, listOf(rahul, meera, nikhil), Category.PERSONAL, unread = 3, sub = VI, msgs = listOf(
                m(9_000_004, rahul, "Who's booking the cab for Saturday?", 2 * HOUR, sub = VI),
                m(9_000_004, null, "I'll do it, 6 AM pickup?", 110 * MIN, sub = VI),
                m(9_000_004, meera, "6 is perfect ✨", 90 * MIN, read = false, sub = VI),
                m(9_000_004, nikhil, "Bringing snacks 🍿", 80 * MIN, read = false, sub = VI),
                m(9_000_004, rahul, "Trip plan is pinned, check it", 70 * MIN, read = false, sub = VI),
            )),
            Thread(9_000_005, listOf(iob), Category.TRANSACTION, msgs = listOf(
                m(9_000_005, iob, "Your a/c XXXXX62 debited for payee SWIGGY for Rs. 349.00 on 09-11-2026, ref 631300004567. If not you, report to your bank immediately-IOB.", 3 * HOUR),
            )),
            Thread(9_000_006, listOf(arjun), Category.PERSONAL, msgs = listOf(
                m(9_000_006, arjun, "Sent you the slides for Monday's review", 6 * HOUR),
                m(9_000_006, null, "Got them, looks great. Small fix on slide 4", 5 * HOUR + 30 * MIN, status = MsgStatus.SENT),
            )),
            Thread(9_000_007, listOf(swiggy), Category.PROMOTION, msgs = listOf(
                m(9_000_007, swiggy, "Craving something? Flat 50% off on your next 3 orders 🍔 Use code TREAT50. T&C apply.", 7 * HOUR),
            )),
            Thread(9_000_008, listOf(karthik), Category.PERSONAL, muted = true, sub = VI, msgs = listOf(
                m(9_000_008, karthik, "Match at 7:30 tomorrow? 🏏", 26 * HOUR, sub = VI),
                m(9_000_008, null, "In! I'll bring the bat", 25 * HOUR, sub = VI),
            )),
            Thread(9_000_009, listOf(amazon), Category.TRANSACTION, msgs = listOf(
                m(9_000_009, amazon, "Refund of Rs.499.00 for your order #402-1234567 has been processed to your HDFC Bank card. It will reflect in 3-5 days.", 28 * HOUR),
            )),
            Thread(9_000_010, listOf(divya), Category.PERSONAL, msgs = listOf(
                m(9_000_010, divya, "Happy birthday!! 🎂🎈 Have the best year", 3 * DAY),
                m(9_000_010, null, "Thank you so much Divya ❤️", 3 * DAY - 20 * MIN, reaction = "🙏"),
            )),
            Thread(9_000_011, listOf(jio), Category.TRANSACTION, msgs = listOf(
                m(9_000_011, jio, "Recharge of Rs.349 successful for 9000000018. Plan: 2GB/day + Unlimited calls, valid 28 days.", 4 * DAY),
            )),
            Thread(9_000_012, listOf(govt), Category.GOVERNMENT, msgs = listOf(
                m(9_000_012, govt, "Know the mobile connections in your name. Visit sancharsaathi.gov.in - Dept of Telecom, Govt of India", 5 * DAY),
            )),
            Thread(9_000_013, listOf(spam), Category.SPAM, msgs = listOf(
                m(9_000_013, spam, "Congratulations! You have won a lottery of Rs 25,00,000. Click the link to claim now.", 2 * DAY),
            )),
        )
    }

    private fun Thread.toConversation(): Conversation {
        val last = msgs.maxBy { it.date }
        return Conversation(
            threadId = id, addresses = people.map { it.number }, contacts = people,
            snippet = last.body, date = last.date, unreadCount = unread, messageCount = msgs.size,
            hasError = false, hasAttachment = false, category = cat, pinned = pinned, archived = false,
            muted = muted, subId = sub, lastOutgoing = last.outgoing,
        )
    }

    fun conversations(): List<Conversation> = threads().map { it.toConversation() }.sortedByDescending { it.date }

    fun messages(threadId: Long): List<Message> =
        threads().firstOrNull { it.id == threadId }?.msgs?.sortedBy { it.date }.orEmpty()

    fun lookup(address: String): Contact? =
        contacts.firstOrNull { it.number == address }
            ?: threads().flatMap { it.people }.firstOrNull { it.number == address }

    fun search(q: String): List<Pair<Conversation, Message?>> {
        if (q.isBlank()) return emptyList()
        val out = ArrayList<Pair<Conversation, Message?>>()
        threads().forEach { t ->
            val conv = t.toConversation()
            if (conv.title.contains(q, true)) out += conv to null
            t.msgs.filter { it.body.contains(q, true) }.forEach { out += conv to it }
        }
        return out
    }

    fun starred(): List<Pair<Conversation, Message>> =
        threads().flatMap { t -> t.msgs.filter { it.starred }.map { t.toConversation() to it } }

    val blocked = listOf("+919000099999")
}

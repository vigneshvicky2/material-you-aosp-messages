package com.rodin.messages.finance

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.Telephony
import com.rodin.messages.data.ContactsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

private class FinanceDb(ctx: Context) : SQLiteOpenHelper(ctx, "finance.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txns(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_id INTEGER UNIQUE,
                sender TEXT NOT NULL,
                bank TEXT, account TEXT,
                amount INTEGER NOT NULL, dir INTEGER NOT NULL, channel TEXT NOT NULL,
                counterparty TEXT, vpa TEXT, ref TEXT, balance INTEGER,
                ts INTEGER NOT NULL, category TEXT NOT NULL, note TEXT,
                excluded INTEGER NOT NULL DEFAULT 0, dup_of INTEGER, failed INTEGER NOT NULL DEFAULT 0,
                confidence REAL NOT NULL DEFAULT 0, body TEXT NOT NULL, manual INTEGER NOT NULL DEFAULT 0,
                cat_locked INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL DEFAULT 'SMS')""",
        )
        db.execSQL("CREATE INDEX idx_txns_ts ON txns(ts)")
        db.execSQL("CREATE INDEX idx_txns_ref ON txns(ref)")
        db.execSQL("CREATE TABLE rules(key TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE txns ADD COLUMN source TEXT NOT NULL DEFAULT 'SMS'")
    }
}

data class AccountSummary(val bank: String?, val account: String, val balancePaise: Long?, val balanceTime: Long?, val txnCount: Int)

data class MonthSummary(
    val spentPaise: Long,
    val receivedPaise: Long,
    val byCategory: List<Pair<Cat, Long>>,
    val dailySpent: LongArray,          // index 0 = day 1
    val topMerchants: List<Pair<String, Long>>,
    val txnCount: Int,
)

class FinanceRepo private constructor(private val ctx: Context) {
    private val db = FinanceDb(ctx)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private fun bump() { _version.value++ }

    private val contacts by lazy { ContactsRepo(ctx) }
    private var contactNames: Set<String>? = null

    private fun isContact(name: String): Boolean {
        if (name.isBlank()) return false
        val set = contactNames ?: runCatching { contacts.all().mapNotNull { it.name?.lowercase() }.toSet() }
            .getOrDefault(emptySet()).also { contactNames = it }
        return name.lowercase() in set
    }

    // ------------------------------------------------------------ meta
    private fun meta(k: String): String? =
        db.readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun setMeta(w: SQLiteDatabase, k: String, v: String) {
        w.insertWithOnConflict("meta", null, ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun hasImported(): Boolean = meta("last_sms_id") != null

    fun rules(): Map<String, Cat> {
        val out = HashMap<String, Cat>()
        db.readableDatabase.rawQuery("SELECT key, category FROM rules", null).use { c ->
            while (c.moveToNext()) runCatching { out[c.getString(0)] = Cat.valueOf(c.getString(1)) }
        }
        return out
    }

    // ------------------------------------------------------------ import

    /** Reads every inbox SMS newer than the last import and stores the transactions found. */
    fun importNew(progress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val last = meta("last_sms_id")?.toLongOrNull() ?: 0L
        val cr = ctx.contentResolver
        val cursor = runCatching {
            cr.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("_id", "address", "body", "date"),
                "_id > ?", arrayOf(last.toString()), "_id ASC",
            )
        }.getOrNull() ?: return 0
        var added = 0
        var maxId = last
        val rules = rules()
        val w = db.writableDatabase
        cursor.use { c ->
            val total = c.count
            var done = 0
            w.beginTransaction()
            try {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    maxId = maxOf(maxId, id)
                    if (insertFromSms(w, id, c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3), rules)) added++
                    done++
                    if (done % 25 == 0) progress(done, total)
                }
                setMeta(w, "last_sms_id", maxId.toString())
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
            progress(total, total)
        }
        if (added > 0 || last == 0L) bump()
        return added
    }

    /** Live path: called right after a new SMS is written to the inbox. */
    fun ingest(smsId: Long, sender: String, body: String, time: Long): Txn? {
        val w = db.writableDatabase
        val ok = insertFromSms(w, smsId, sender, body, time, rules())
        if (ok) bump()
        return if (ok) bySms(smsId) else null
    }

    private fun insertFromSms(w: SQLiteDatabase, smsId: Long, sender: String, body: String, time: Long, rules: Map<String, Cat>): Boolean {
        val p = SmsTxnParser.parse(sender, body) ?: return false
        val cat = Categorizer.categorize(p, body, rules, ::isContact)
        val dup = findDuplicate(w, p, time)
        val v = ContentValues().apply {
            put("sms_id", smsId)
            put("sender", sender)
            put("bank", p.bank)
            put("account", p.account)
            put("amount", p.amountPaise)
            put("dir", if (p.direction == Direction.CREDIT) 1 else -1)
            put("channel", p.channel.name)
            put("counterparty", p.counterparty)
            put("vpa", p.vpa)
            put("ref", p.ref)
            if (p.balancePaise != null) put("balance", p.balancePaise) else putNull("balance")
            put("ts", time)
            put("category", cat.name)
            put("excluded", if (p.failed) 1 else 0)
            put("failed", if (p.failed) 1 else 0)
            if (dup != null) put("dup_of", dup) else putNull("dup_of")
            put("confidence", p.confidence)
            put("body", body)
        }
        val id = w.insertWithOnConflict("txns", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        if (id > 0 && dup == null && !p.failed) markSelfTransfer(w, id, p, time)
        if (id > 0 && dup != null) {
            // Same payment already known (e.g. from the GPay screen): add what only the SMS knows.
            fillMissing(w, dup, "balance" to p.balancePaise, "account" to p.account, "bank" to p.bank, "ref" to p.ref)
        }
        return id > 0
    }

    private fun fillMissing(w: SQLiteDatabase, id: Long, vararg fields: Pair<String, Any?>) {
        fields.forEach { (col, value) ->
            if (value == null) return@forEach
            val v = ContentValues().apply {
                when (value) {
                    is Long -> put(col, value)
                    is String -> put(col, value)
                    else -> put(col, value.toString())
                }
            }
            w.update("txns", v, "id=? AND $col IS NULL", arrayOf(id.toString()))
        }
    }

    /**
     * Records a payment read from a UPI app's success/details screen.
     * If the same payment is already known (bank SMS, or the screen was opened before), it is merged instead.
     * Returns true when a new transaction was added.
     */
    fun ingestScreen(t: ScreenTxn, pkg: String): Boolean {
        val w = db.writableDatabase
        val time = t.time ?: System.currentTimeMillis()
        val dir = if (t.direction == Direction.CREDIT) 1 else -1
        val existing: Long? = run {
            if (t.ref != null) {
                w.rawQuery("SELECT id FROM txns WHERE ref=? AND dup_of IS NULL LIMIT 1", arrayOf(t.ref))
                    .use { if (it.moveToFirst()) return@run it.getLong(0) }
            }
            w.rawQuery(
                "SELECT id FROM txns WHERE amount=? AND dir=? AND dup_of IS NULL AND ts BETWEEN ? AND ? ORDER BY ABS(ts-?) LIMIT 1",
                arrayOf(t.amountPaise.toString(), dir.toString(), (time - 300_000).toString(), (time + 300_000).toString(), time.toString()),
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
        }
        if (existing != null) {
            // The app screen knows the real payee name; the SMS often only has a VPA or a code.
            t.payee?.let { name ->
                w.update("txns", ContentValues().apply { put("counterparty", name) },
                    "id=? AND (counterparty IS NULL OR source='SMS')", arrayOf(existing.toString()))
            }
            fillMissing(w, existing, "vpa" to t.vpa, "ref" to t.ref, "account" to t.account, "bank" to t.bank)
            bump()
            return false
        }
        val parsed = ParsedTxn(
            amountPaise = t.amountPaise, direction = t.direction, channel = Channel.UPI,
            bank = if (t.upiLite) "UPI Lite" else t.bank, account = t.account, counterparty = t.payee, vpa = t.vpa,
            ref = t.ref, balancePaise = null, confidence = if (t.ref != null) 0.95f else 0.8f,
        )
        val app = UpiCaptureService.appName(pkg)
        val body = buildString {
            append(if (t.direction == Direction.DEBIT) "Paid " else "Received ")
            append(formatRupees(t.amountPaise, decimals = true))
            append(if (t.direction == Direction.DEBIT) " to " else " from ")
            append(t.payee ?: t.vpa ?: "unknown")
            if (t.vpa != null && t.payee != null) append(" (${t.vpa})")
            append(" in $app")
            if (t.upiLite) append(" using UPI Lite")
            t.ref?.let { append(". UPI ref $it") }
        }
        val cat = Categorizer.categorize(parsed, body, rules(), ::isContact)
        val v = ContentValues().apply {
            putNull("sms_id")
            put("sender", app)
            put("bank", parsed.bank)
            put("account", parsed.account)
            put("amount", parsed.amountPaise)
            put("dir", dir)
            put("channel", parsed.channel.name)
            put("counterparty", parsed.counterparty)
            put("vpa", parsed.vpa)
            put("ref", parsed.ref)
            putNull("balance")
            put("ts", time)
            put("category", cat.name)
            put("confidence", parsed.confidence)
            put("body", body)
            put("source", Source.SCREEN.name)
        }
        val id = w.insert("txns", null, v)
        if (id > 0) bump()
        return id > 0
    }

    /**
     * The same payment is often reported by more than one SMS (bank + card + UPI app).
     * Same reference number, or same amount/direction within 3 minutes from a different sender.
     */
    private fun findDuplicate(w: SQLiteDatabase, p: ParsedTxn, time: Long): Long? {
        val dir = if (p.direction == Direction.CREDIT) 1 else -1
        if (p.ref != null) {
            w.rawQuery("SELECT id FROM txns WHERE ref=? AND amount=? AND dir=? AND dup_of IS NULL LIMIT 1",
                arrayOf(p.ref, p.amountPaise.toString(), dir.toString())).use { if (it.moveToFirst()) return it.getLong(0) }
        }
        w.rawQuery(
            "SELECT id, account, ref FROM txns WHERE amount=? AND dir=? AND dup_of IS NULL AND ts BETWEEN ? AND ? LIMIT 5",
            arrayOf(p.amountPaise.toString(), dir.toString(), (time - 300_000).toString(), (time + 300_000).toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val acc = c.getString(1)
                val ref = c.getString(2)
                val sameAcc = acc == null || p.account == null || acc == p.account
                val refConflict = ref != null && p.ref != null && ref != p.ref
                if (sameAcc && !refConflict) return c.getLong(0)
            }
        }
        return null
    }

    /** Debit from one of your accounts + matching credit into another of your accounts → self transfer, not spending. */
    private fun markSelfTransfer(w: SQLiteDatabase, id: Long, p: ParsedTxn, time: Long) {
        val acc = p.account ?: return
        val dir = if (p.direction == Direction.CREDIT) 1 else -1
        w.rawQuery(
            "SELECT id FROM txns WHERE amount=? AND dir=? AND account IS NOT NULL AND account<>? AND dup_of IS NULL AND excluded=0 " +
                "AND ts BETWEEN ? AND ? LIMIT 1",
            arrayOf(p.amountPaise.toString(), (-dir).toString(), acc, (time - 900_000).toString(), (time + 900_000).toString()),
        ).use { c ->
            if (c.moveToFirst()) {
                val other = c.getLong(0)
                val v = ContentValues().apply { put("excluded", 1); put("category", Cat.TRANSFER.name) }
                w.update("txns", v, "id IN (?, ?)", arrayOf(id.toString(), other.toString()))
            }
        }
    }

    // ------------------------------------------------------------ reads

    private fun Cursor.toTxn(): Txn = Txn(
        id = getLong(getColumnIndexOrThrow("id")),
        smsId = getColumnIndexOrThrow("sms_id").let { if (isNull(it)) null else getLong(it) },
        sender = getString(getColumnIndexOrThrow("sender")),
        bank = getString(getColumnIndexOrThrow("bank")),
        account = getString(getColumnIndexOrThrow("account")),
        amountPaise = getLong(getColumnIndexOrThrow("amount")),
        direction = if (getInt(getColumnIndexOrThrow("dir")) > 0) Direction.CREDIT else Direction.DEBIT,
        channel = runCatching { Channel.valueOf(getString(getColumnIndexOrThrow("channel"))) }.getOrDefault(Channel.OTHER),
        counterparty = getString(getColumnIndexOrThrow("counterparty")),
        vpa = getString(getColumnIndexOrThrow("vpa")),
        ref = getString(getColumnIndexOrThrow("ref")),
        balancePaise = getColumnIndexOrThrow("balance").let { if (isNull(it)) null else getLong(it) },
        time = getLong(getColumnIndexOrThrow("ts")),
        category = runCatching { Cat.valueOf(getString(getColumnIndexOrThrow("category"))) }.getOrDefault(Cat.OTHER),
        note = getString(getColumnIndexOrThrow("note")),
        excluded = getInt(getColumnIndexOrThrow("excluded")) != 0,
        duplicateOf = getColumnIndexOrThrow("dup_of").let { if (isNull(it)) null else getLong(it) },
        confidence = getFloat(getColumnIndexOrThrow("confidence")),
        body = getString(getColumnIndexOrThrow("body")),
        manual = getInt(getColumnIndexOrThrow("manual")) != 0,
        source = runCatching { Source.valueOf(getString(getColumnIndexOrThrow("source"))) }.getOrDefault(Source.SMS),
    )

    private fun query(where: String, args: Array<String>, order: String = "ts DESC", limit: String? = null): List<Txn> {
        val out = ArrayList<Txn>()
        db.readableDatabase.query("txns", null, where, args, null, null, order, limit).use { c ->
            while (c.moveToNext()) out += c.toTxn()
        }
        return out
    }

    fun bySms(smsId: Long): Txn? = query("sms_id=?", arrayOf(smsId.toString())).firstOrNull()
    fun byId(id: Long): Txn? = query("id=?", arrayOf(id.toString())).firstOrNull()

    /** All transactions (duplicates hidden) in [from, to). */
    fun between(from: Long, to: Long): List<Txn> =
        query("ts >= ? AND ts < ? AND dup_of IS NULL", arrayOf(from.toString(), to.toString()))

    fun recent(limit: Int): List<Txn> = query("dup_of IS NULL", emptyArray(), limit = limit.toString())

    fun search(q: String): List<Txn> {
        val like = "%${q.trim()}%"
        return query("dup_of IS NULL AND (counterparty LIKE ? OR vpa LIKE ? OR body LIKE ? OR note LIKE ?)",
            arrayOf(like, like, like, like), limit = "300")
    }

    fun summarize(txns: List<Txn>, daysInMonth: Int): MonthSummary {
        val counted = txns.filter { it.counts }
        val spent = counted.filter { it.direction == Direction.DEBIT }
        val received = counted.filter { it.direction == Direction.CREDIT }
        val daily = LongArray(daysInMonth)
        val cal = Calendar.getInstance()
        spent.forEach { t ->
            cal.timeInMillis = t.time
            val d = cal.get(Calendar.DAY_OF_MONTH) - 1
            if (d in daily.indices) daily[d] += t.amountPaise
        }
        return MonthSummary(
            spentPaise = spent.sumOf { it.amountPaise },
            receivedPaise = received.sumOf { it.amountPaise },
            byCategory = spent.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amountPaise } }.sortedByDescending { it.second },
            dailySpent = daily,
            topMerchants = spent.groupBy { it.title }.map { (k, v) -> k to v.sumOf { it.amountPaise } }
                .sortedByDescending { it.second }.take(5),
            txnCount = counted.size,
        )
    }

    /** Latest known balance per account (from the newest SMS that mentioned one). */
    fun accounts(): List<AccountSummary> {
        val out = ArrayList<AccountSummary>()
        db.readableDatabase.rawQuery(
            """SELECT bank, account, COUNT(*) FROM txns WHERE account IS NOT NULL AND dup_of IS NULL
               GROUP BY account ORDER BY MAX(ts) DESC""", null,
        ).use { c ->
            while (c.moveToNext()) {
                val acc = c.getString(1)
                var bal: Long? = null; var balTs: Long? = null
                db.readableDatabase.rawQuery(
                    "SELECT balance, ts FROM txns WHERE account=? AND balance IS NOT NULL ORDER BY ts DESC LIMIT 1", arrayOf(acc),
                ).use { b -> if (b.moveToFirst()) { bal = b.getLong(0); balTs = b.getLong(1) } }
                out += AccountSummary(c.getString(0), acc, bal, balTs, c.getInt(2))
            }
        }
        return out
    }

    // ------------------------------------------------------------ edits

    /** Change a category; optionally remember it for every transaction with the same merchant/VPA. */
    fun setCategory(t: Txn, cat: Cat, rememberForMerchant: Boolean) {
        val w = db.writableDatabase
        w.update("txns", ContentValues().apply { put("category", cat.name); put("cat_locked", 1) }, "id=?", arrayOf(t.id.toString()))
        if (rememberForMerchant) {
            val key = (t.vpa ?: t.counterparty)?.lowercase()?.trim()
            if (!key.isNullOrBlank()) {
                w.insertWithOnConflict("rules", null, ContentValues().apply { put("key", key); put("category", cat.name) },
                    SQLiteDatabase.CONFLICT_REPLACE)
                val col = if (t.vpa != null) "vpa" else "counterparty"
                w.update("txns", ContentValues().apply { put("category", cat.name) },
                    "LOWER($col)=? AND cat_locked=0", arrayOf(key))
            }
        }
        bump()
    }

    fun setExcluded(t: Txn, excluded: Boolean) {
        db.writableDatabase.update("txns", ContentValues().apply { put("excluded", if (excluded) 1 else 0) }, "id=?", arrayOf(t.id.toString()))
        bump()
    }

    fun setNote(t: Txn, note: String?) {
        db.writableDatabase.update("txns", ContentValues().apply { put("note", note?.takeIf { it.isNotBlank() }) }, "id=?", arrayOf(t.id.toString()))
        bump()
    }

    /** Wipe parsed data and read all SMS again (keeps category rules). */
    fun rebuild(progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val w = db.writableDatabase
        w.delete("txns", null, null)
        w.delete("meta", "k='last_sms_id'", null)
        contactNames = null
        return importNew(progress)
    }

    companion object {
        @Volatile private var inst: FinanceRepo? = null
        fun get(ctx: Context): FinanceRepo = inst ?: synchronized(this) {
            inst ?: FinanceRepo(ctx.applicationContext).also { inst = it }
        }
    }
}

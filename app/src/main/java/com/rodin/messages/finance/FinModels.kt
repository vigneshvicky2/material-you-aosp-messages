package com.rodin.messages.finance

/** Pure-Kotlin finance models (no Android deps) so the parser can be unit-tested on the JVM. */

enum class Direction { DEBIT, CREDIT }

enum class Channel(val label: String) {
    UPI("UPI"), CARD("Card"), ATM("ATM"), NEFT("NEFT"), IMPS("IMPS"), RTGS("RTGS"),
    AUTOPAY("AutoPay"), NETBANKING("Net banking"), WALLET("Wallet"), OTHER("Bank"),
}

enum class Cat(val label: String, val color: Long) {
    FOOD("Food & dining", 0xFFFF8A65),
    GROCERIES("Groceries", 0xFF81C784),
    SHOPPING("Shopping", 0xFFBA68C8),
    TRAVEL("Travel", 0xFF4FC3F7),
    FUEL("Fuel", 0xFFFFB74D),
    BILLS("Bills & recharge", 0xFF7986CB),
    ENTERTAINMENT("Entertainment", 0xFFF06292),
    HEALTH("Health", 0xFF4DB6AC),
    EDUCATION("Education", 0xFF9575CD),
    RENT("Rent & home", 0xFFA1887F),
    EMI("EMI & loans", 0xFFE57373),
    INVESTMENT("Investments", 0xFF64B5F6),
    INSURANCE("Insurance", 0xFF90A4AE),
    CASH("Cash", 0xFFAED581),
    PEOPLE("People", 0xFF4DD0E1),
    INCOME("Income", 0xFF66BB6A),
    CASHBACK("Cashback & rewards", 0xFFFFD54F),
    REFUND("Refunds", 0xFF26A69A),
    TRANSFER("Self transfer", 0xFFB0BEC5),
    OTHER("Other", 0xFF9E9E9E),
}

/** Result of parsing one SMS. Amounts are in paise to avoid floating-point errors. */
data class ParsedTxn(
    val amountPaise: Long,
    val direction: Direction,
    val channel: Channel,
    val bank: String?,
    val account: String?,          // last digits, e.g. "4821"
    val counterparty: String?,     // merchant / person / VPA-derived name
    val vpa: String?,
    val ref: String?,
    val balancePaise: Long?,
    val confidence: Float,
    val failed: Boolean = false,
)

data class Txn(
    val id: Long,
    val smsId: Long?,
    val sender: String,
    val bank: String?,
    val account: String?,
    val amountPaise: Long,
    val direction: Direction,
    val channel: Channel,
    val counterparty: String?,
    val vpa: String?,
    val ref: String?,
    val balancePaise: Long?,
    val time: Long,
    val category: Cat,
    val note: String?,
    val excluded: Boolean,
    val duplicateOf: Long?,
    val confidence: Float,
    val body: String,
    val manual: Boolean,
) {
    val signedPaise: Long get() = if (direction == Direction.CREDIT) amountPaise else -amountPaise
    val title: String get() = counterparty ?: vpa ?: channel.label
    val counts: Boolean get() = !excluded && duplicateOf == null
}

fun formatRupees(paise: Long, decimals: Boolean = false): String {
    val neg = paise < 0
    val abs = kotlin.math.abs(paise)
    val rupees = abs / 100
    val p = abs % 100
    // Indian digit grouping: 12,34,567
    val s = rupees.toString()
    val grouped = if (s.length <= 3) s else {
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$rest,$last3"
    }
    val frac = if (decimals || (p != 0L && rupees < 1000)) ".%02d".format(p) else ""
    return (if (neg) "-" else "") + "₹" + grouped + frac
}

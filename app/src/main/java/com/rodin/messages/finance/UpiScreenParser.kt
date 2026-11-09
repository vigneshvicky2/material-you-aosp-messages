package com.rodin.messages.finance

import java.text.SimpleDateFormat
import java.util.Locale

/** A completed payment read from a UPI app's success / transaction-details screen. */
data class ScreenTxn(
    val amountPaise: Long,
    val direction: Direction,
    val payee: String?,
    val vpa: String?,
    val ref: String?,
    val bank: String?,
    val account: String?,
    val upiLite: Boolean,
    val time: Long?,          // date shown on screen (history details), null = "just now"
)

/**
 * Parses the visible texts of a GPay / super.money screen (in on-screen order).
 * Only returns a result for completed payments; pending/failed screens are ignored.
 */
object UpiScreenParser {
    private val IC = RegexOption.IGNORE_CASE

    private val success = Regex(
        "\\b(payment successful|paid successfully|transaction successful|transfer successful|sent successfully|" +
            "money sent|payment complete[d]?|successfully paid|received successfully|completed|success(ful)?)\\b", IC,
    )
    private val notDone = Regex("\\b(failed|failure|declined|pending|processing|in progress|cancelled|canceled|expired|requested|request)\\b", IC)

    private val amountOnly = Regex("^\\s*(?:₹|rs\\.?|inr)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*$", IC)
    private val amountAny = Regex("(?:₹|rs\\.?|inr)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)", IC)

    private val debitPayee = Regex("^(?:paid to|sent to|payment to|to)[:\\s]+(.+)$", IC)
    private val creditPayer = Regex("^(?:received from|from)[:\\s]+(.+)$", IC)
    private val refKey = Regex("(upi\\s*(?:transaction|txn|ref(?:erence)?)\\s*(?:id|no\\.?|number)?|utr|rrn|upi ref)", IC)
    private val twelveDigits = Regex("(?<!\\d)(\\d{12})(?!\\d)")
    private val vpaRx = Regex("\\b([a-z0-9._\\-]{2,64}@[a-z][a-z0-9]{1,20})\\b", IC)
    private val accountRx = Regex("(?:••|\\*\\*|xx|XX|\\.\\.\\.)\\s?(\\d{2,4})\\b")
    private val banks = listOf(
        "IOB" to Regex("indian overseas|\\biob\\b", IC),
        "SBI" to Regex("state bank|\\bsbi\\b", IC),
        "Yes Bank" to Regex("yes bank", IC),
        "HDFC" to Regex("\\bhdfc\\b", IC),
        "ICICI" to Regex("\\bicici\\b", IC),
        "Axis" to Regex("\\baxis\\b", IC),
        "Kotak" to Regex("\\bkotak\\b", IC),
    )
    private val dateFormats = listOf(
        "d MMM yyyy, h:mm a", "d MMM yyyy h:mm a", "dd MMM yyyy, hh:mm a", "d MMMM yyyy, h:mm a",
        "d MMM yyyy 'at' h:mm a", "MMM d, yyyy h:mm a", "dd/MM/yyyy, hh:mm a", "dd-MM-yyyy hh:mm a",
    )
    private val dateRx = Regex(
        "(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}(?:,|\\s+at)?\\s+\\d{1,2}:\\d{2}\\s*[APap][Mm])|" +
            "([A-Za-z]{3}\\s+\\d{1,2},\\s+\\d{4}\\s+\\d{1,2}:\\d{2}\\s*[APap][Mm])|" +
            "(\\d{2}[/-]\\d{2}[/-]\\d{4},?\\s+\\d{1,2}:\\d{2}\\s*[APap][Mm])",
    )
    // words that are labels, never payee names
    private val labelWords = Regex(
        "^(you|me|self|bank|upi|google pay|gpay|super\\.?money|account|your account|upi lite|share|done|split|view details|" +
            "pay again|check balance|payment details|transaction details)$", IC,
    )

    fun parse(texts: List<String>): ScreenTxn? {
        val lines = texts.map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val all = lines.joinToString("\n")
        if (!success.containsMatchIn(all)) return null
        // A clear failure/pending status anywhere on the screen means it isn't a completed payment
        if (lines.any { l -> l.length < 40 && notDone.containsMatchIn(l) && !success.containsMatchIn(l) }) return null

        // amount: prefer a standalone "₹350" line (the big hero number), else first ₹ value
        val amountStr = lines.firstNotNullOfOrNull { amountOnly.find(it)?.groupValues?.get(1) }
            ?: amountAny.find(all)?.groupValues?.get(1)
            ?: return null
        val amount = SmsTxnParser.toPaise(amountStr) ?: return null
        if (amount <= 0) return null

        // direction + counterparty
        var direction: Direction? = null
        var payee: String? = null
        // First "To X" / "From X" line whose name is NOT you / your bank account decides the direction.
        for (l in lines) {
            debitPayee.find(l)?.let { m -> cleanName(m.groupValues[1])?.let { direction = Direction.DEBIT; payee = it } }
            if (direction != null) break
            creditPayer.find(l)?.let { m -> cleanName(m.groupValues[1])?.let { direction = Direction.CREDIT; payee = it } }
            if (direction != null) break
        }
        if (direction == null) {
            direction = when {
                Regex("\\b(received|credited)\\b", IC).containsMatchIn(all) -> Direction.CREDIT
                Regex("\\b(paid|sent|debited|payment)\\b", IC).containsMatchIn(all) -> Direction.DEBIT
                else -> return null
            }
        }

        // reference: 12-digit number on/after a "UPI transaction ID" label, else any 12-digit number
        var ref: String? = null
        for ((i, l) in lines.withIndex()) {
            if (refKey.containsMatchIn(l)) {
                ref = twelveDigits.find(l)?.value ?: lines.getOrNull(i + 1)?.let { twelveDigits.find(it)?.value }
                if (ref != null) break
            }
        }
        if (ref == null) ref = twelveDigits.find(all)?.value

        val vpas = vpaRx.findAll(all).map { it.groupValues[1].lowercase() }.toList()
        // The payee's VPA is the one that isn't on a "From: you" line
        val vpa = vpas.firstOrNull()
        val bank = banks.firstOrNull { it.second.containsMatchIn(all) }?.first
        val account = accountRx.find(all)?.groupValues?.get(1)
        val upiLite = Regex("upi\\s*lite", IC).containsMatchIn(all)
        val time = dateRx.find(all)?.value?.let(::parseDate)

        return ScreenTxn(amount, direction, payee ?: vpa?.let(SmsTxnParser::vpaToName), vpa, ref, bank, account, upiLite, time)
    }

    private fun cleanName(raw: String): String? {
        val s = raw.substringBefore("\n").trim().trimEnd('.', ',')
        if (s.length < 2 || s.length > 50) return null
        if (labelWords.matches(s)) return null
        if (amountAny.containsMatchIn(s)) return null
        // "You (Indian Overseas Bank ••62)" / "Your IOB account" = the user's own side
        if (Regex("^(you|your|self)\\b", IC).containsMatchIn(s)) return null
        if (accountRx.containsMatchIn(s) || banks.any { it.second.containsMatchIn(s) } && s.contains("(")) return null
        return s.substringBefore("  ").trim()
    }

    private fun parseDate(s: String): Long? {
        val norm = s.replace(Regex("\\s+"), " ").replace(Regex("(?i)\\b(am|pm)\\b")) { it.value.uppercase() }
        for (f in dateFormats) {
            runCatching {
                val fmt = SimpleDateFormat(f, Locale.ENGLISH).apply { isLenient = false }
                return fmt.parse(norm)?.time
            }
        }
        return null
    }
}

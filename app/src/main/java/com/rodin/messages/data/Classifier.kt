package com.rodin.messages.data

/**
 * Lightweight on-device classifier tuned for Indian SMS traffic.
 * TRAI DLT sender IDs look like "VM-HDFCBK-S" / "JD-AIRTEL-P" / "AX-SBIINB-T" / "CP-GOVTIN-G".
 */
object Classifier {

    private val headerRegex = Regex("^[A-Z]{2}-[A-Z0-9]{3,9}(-[STPG])?$", RegexOption.IGNORE_CASE)
    private val otpKeyword = Regex(
        "\\b(otp|one[ -]?time|verification|verify|passcode|security code|auth(entication)? code|login code|pin)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val otpCode = Regex("(?<![\\d₹.,/])(\\d{4,8})(?![\\d%.,/])")
    private val alnumCode = Regex("\\b([A-Z0-9]{6})\\b")
    private val txnKeyword = Regex(
        "\\b(debited|credited|a/c|acct|account|upi|imps|neft|rtgs|txn|transaction|balance|bal:|spent|refund|emi|paid|payment of|rs\\.?|inr|₹)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val promoKeyword = Regex(
        "\\b(offer|sale|discount|% off|cashback|deal|coupon|free|win|limited time|hurry|shop now|click|buy|exclusive|recharge now|unlimited|plan)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val spamKeyword = Regex(
        "\\b(lottery|you have won|kyc.*(update|expire)|account.*(blocked|suspended).*click|loan approved|pre-approved loan)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun isBusinessSender(address: String): Boolean {
        val a = address.trim()
        if (headerRegex.matches(a)) return true
        // Short alpha sender ids without digits-only
        return a.any { it.isLetter() } && !a.startsWith("+") && a.length in 3..12
    }

    fun classify(address: String, body: String, isContact: Boolean): Category {
        if (isContact) return Category.PERSONAL
        val a = address.trim().uppercase()
        val suffix = if (headerRegex.matches(a) && a.count { it == '-' } == 2) a.last() else null
        if (extractOtp(body) != null) return Category.OTP
        when (suffix) {
            'G' -> return Category.GOVERNMENT
            'P' -> return Category.PROMOTION
            'T', 'S' -> {
                return if (promoKeyword.containsMatchIn(body) && !txnKeyword.containsMatchIn(body))
                    Category.PROMOTION else Category.TRANSACTION
            }
        }
        val business = isBusinessSender(a)
        if (spamKeyword.containsMatchIn(body)) return Category.SPAM
        if (business) {
            if (txnKeyword.containsMatchIn(body)) return Category.TRANSACTION
            return Category.PROMOTION
        }
        return Category.PERSONAL
    }

    /** Returns the OTP code if the message looks like a one-time password. */
    fun extractOtp(body: String): String? {
        if (!otpKeyword.containsMatchIn(body)) return null
        // Prefer a numeric code closest to the keyword
        val kw = otpKeyword.find(body) ?: return null
        val codes = otpCode.findAll(body).map { it.groupValues[1] to it.range.first }.toList()
        if (codes.isNotEmpty()) {
            return codes.minBy { kotlin.math.abs(it.second - kw.range.first) }.first
        }
        return alnumCode.findAll(body).map { it.groupValues[1] }
            .firstOrNull { c -> c.any { it.isDigit() } && c.any { it.isLetter() } }
    }

    /** Strip DLT prefix so "VM-HDFCBK-S" shows as "HDFCBK". */
    fun prettySender(address: String): String {
        val a = address.trim()
        if (headerRegex.matches(a)) {
            val parts = a.split("-")
            if (parts.size >= 2) return parts[1].uppercase()
        }
        return a
    }
}

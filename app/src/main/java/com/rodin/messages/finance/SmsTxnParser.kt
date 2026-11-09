package com.rodin.messages.finance

/**
 * Extracts a transaction from an Indian bank / card / wallet SMS.
 *
 * Strategy: a generic extractor (amount, direction, account, balance, ref, VPA, merchant)
 * that works across banks, plus small bank-specific hints for SBI, IOB and super.money.
 * Returns null for anything that is not a completed money movement
 * (OTPs, offers, collect requests, future mandate notices, statements).
 */
object SmsTxnParser {
    private val IC = RegexOption.IGNORE_CASE

    // ---- gates ----
    private val otp = Regex("\\b(otp|one[ -]?time password|verification code|passcode)\\b", IC)
    private val promo = Regex(
        "\\b(pre-?approved|eligible for|get (a )?loan|apply now|offer|cashback up to|win|lucky|" +
            "click here|limited period|upgrade your|avail|enjoy|exciting)\\b", IC,
    )
    private val request = Regex("\\b(has requested|requested money|collect request|is requesting|payment request)\\b", IC)
    private val future = Regex(
        "\\b(will be debited|will be deducted|is due|due on|due date|scheduled for|upcoming|mandate (has been )?(created|registered)|" +
            "min(imum)? (amt|amount) due|total (amt|amount) due|statement)\\b", IC,
    )
    private val failedRx = Regex("\\b(failed|declined|unsuccessful|could not be processed|rejected)\\b", IC)

    // ---- direction ----
    private val debitRx = Regex(
        "\\b(debited|debit(?:ed)? (?:by|with|for)|spent|withdrawn|withdrawal|paid|sent|deducted|purchase|" +
            "transferred to|trf to|dr\\.?(?=\\s)|txn of|transaction of|payment of|charged)\\b", IC,
    )
    private val creditRx = Regex(
        "\\b(credited|credit(?:ed)? (?:by|with)|received|deposited|refund(?:ed)?|reversed|reversal|cashback|cr\\.?(?=\\s)|added to)\\b", IC,
    )

    // ---- fields ----
    private const val AMT = "([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    private val currencyAmt = Regex("(?:rs\\.?|inr|₹)\\s*$AMT", IC)
    // "debited by 350.0" (SBI UPI style without currency)
    private val verbAmt = Regex("(?:debited|credited|withdrawn|spent|paid|received)\\s+(?:by|with|for|of)?\\s*(?:rs\\.?|inr|₹)?\\s*$AMT", IC)
    private val balanceRx = Regex(
        "(?:avl\\.?\\s*bal(?:ance)?|available\\s+bal(?:ance)?|a/?c\\s+bal(?:ance)?|bal(?:ance)?\\s*(?:is|:)|\\bbal\\b|avbl\\s+bal|clear\\s+bal)" +
            "\\s*(?:is|:|-)?\\s*(?:rs\\.?|inr|₹)?\\s*$AMT", IC,
    )
    private val limitRx = Regex("(?:avl\\.?\\s*(?:credit\\s*)?limit|available\\s+(?:credit\\s+)?limit)\\s*(?:is|:)?\\s*(?:rs\\.?|inr|₹)?\\s*$AMT", IC)
    private val accountRx = Regex(
        "(?:a/?c|acct|account|card|a/c no\\.?|ac)\\s*(?:no\\.?|number|ending(?: with)?|ending in)?\\s*[:\\-]?\\s*[x*•.]*\\s*([0-9]{3,6})\\b", IC,
    )
    private val accountRx2 = Regex("\\b[x*]{2,}([0-9]{3,6})\\b", IC)
    private val refRx = Regex(
        "(?:ref(?:erence)?\\.?\\s*(?:no\\.?|number|id|#)?|utr(?:\\s*no\\.?)?|rrn|txn\\s*(?:id|no\\.?|#)|transaction\\s*(?:id|no\\.?|number)|upi\\s*ref)" +
            "\\s*[:\\-]?\\s*\\(?\\s*([A-Za-z0-9]{6,24})", IC,
    )
    private val vpaRx = Regex("\\b([a-z0-9._\\-]{2,64}@[a-z][a-z0-9]{1,20})\\b", IC)

    private val merchantPatterns = listOf(
        Regex("\\bto\\s+vpa\\s+([a-z0-9._\\-]+@[a-z0-9]+)", IC),
        Regex("\\blinked\\s+to\\s+vpa\\s+([a-z0-9._\\-]+@[a-z0-9]+)", IC),
        Regex("\\bat\\s+([A-Za-z0-9][A-Za-z0-9 &.'*_/\\-]{1,40}?)(?=\\s+(?:on|via|using|ref|txn|avl|\\.|for)\\b|[.,]|$)", IC),
        Regex("\\b(?:trf|transfer(?:red)?|paid|sent)\\s+to\\s+([A-Za-z0-9][A-Za-z0-9 &.'_\\-]{1,40}?)(?=\\s+(?:on|ref|refno|upi|via|from|a/c|\\.)\\b|[.,(]|$)", IC),
        Regex("\\bto\\s+([A-Za-z][A-Za-z0-9 &.'_\\-]{1,40}?)(?=\\s+(?:on|ref|refno|upi|via)\\b|[.,(]|$)", IC),
        Regex("\\b(?:from|by)\\s+([A-Za-z][A-Za-z0-9 &.'_\\-]{1,40}?)(?=\\s+(?:on|ref|refno|upi|via|a/c|\\()|[.,(]|$)", IC),
        Regex("\\btowards\\s+([A-Za-z0-9][A-Za-z0-9 &.'_\\-]{1,40}?)(?=\\s+(?:on|ref|\\.)\\b|[.,]|$)", IC),
        Regex("\\binfo[:\\s]+([A-Za-z0-9][A-Za-z0-9 &.*/_\\-]{1,40}?)(?=\\s+(?:on|ref|avl|\\.)|[.,]|$)", IC),
    )
    private val junkCounterparty = Regex(
        "^(you|your|a/?c|ac|account|card|upi|vpa|rs|inr|the|ur|u|beneficiary|on|self|mobile banking|net banking|atm|xx\\S*|\\d+)$", IC,
    )

    private val bankHints = listOf(
        "SBI" to Regex("\\b(sbi|state bank)\\b|-sbi$|sbiinb|sbiupi|sbicrd|atmsbi|cbssbi", IC),
        "IOB" to Regex("\\b(iob|indian overseas)\\b|iobchn|iobbnk|iobank", IC),
        "super.money" to Regex("super\\.?money|supercard|@superyes|utkarsh", IC),
        "HDFC" to Regex("\\bhdfc\\b|hdfcbk", IC),
        "ICICI" to Regex("\\bicici\\b", IC),
        "Axis" to Regex("\\baxis\\b", IC),
        "Kotak" to Regex("\\bkotak\\b", IC),
        "Paytm" to Regex("\\bpaytm\\b", IC),
        "Amazon Pay" to Regex("amazon pay", IC),
    )

    fun parse(sender: String, body: String): ParsedTxn? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.length < 15) return null
        // Personal numbers are never bank alerts
        if (sender.startsWith("+") && sender.drop(1).all { it.isDigit() } && sender.length > 11) return null

        val hasMoney = currencyAmt.containsMatchIn(text) || verbAmt.containsMatchIn(text)
        if (!hasMoney) return null
        val d = debitRx.find(text)
        val c = creditRx.find(text)
        if (d == null && c == null) return null

        // gates
        if (otp.containsMatchIn(text) && !Regex("\\b(debited|credited)\\b", IC).containsMatchIn(text)) return null
        if (request.containsMatchIn(text)) return null
        if (future.containsMatchIn(text) && !Regex("\\b(has been|is) (debited|credited)\\b|\\b(debited|credited) (by|with|for)\\b", IC).containsMatchIn(text)) return null
        if (promo.containsMatchIn(text) && !Regex("\\b(debited|credited|spent|withdrawn)\\b", IC).containsMatchIn(text)) return null

        // direction = earliest keyword (IOB: "debited for Rs.. and credited to a/c X" → debit)
        val direction = when {
            d != null && c != null -> if (d.range.first <= c.range.first) Direction.DEBIT else Direction.CREDIT
            d != null -> Direction.DEBIT
            else -> Direction.CREDIT
        }
        val anchor = (if (direction == Direction.DEBIT) d else c)!!.range.first

        // amount: prefer the money value closest to the direction keyword, never the balance/limit
        val excluded = (balanceRx.findAll(text) + limitRx.findAll(text)).map { it.groups[1]!!.range }.toList()
        val candidates = (currencyAmt.findAll(text).map { it.groups[1]!! } + verbAmt.findAll(text).map { it.groups[1]!! })
            .filter { g -> excluded.none { it.first == g.range.first } }
            .distinctBy { it.range.first }
            .toList()
        val amountGroup = candidates.minByOrNull { kotlin.math.abs(it.range.first - anchor) } ?: return null
        val amount = toPaise(amountGroup.value) ?: return null
        if (amount <= 0) return null

        val balance = balanceRx.find(text)?.groups?.get(1)?.value?.let(::toPaise)
        val account = (accountRx.find(text)?.groupValues?.get(1) ?: accountRx2.find(text)?.groupValues?.get(1))?.takeLast(4)
        val ref = refRx.findAll(text).map { it.groupValues[1] }.firstOrNull { r -> r.any { it.isDigit() } && r.length >= 6 }
        val vpas = vpaRx.findAll(text).map { it.groupValues[1].lowercase() }
            .filterNot { it.endsWith(".com") || it.endsWith(".in") }.toList()
        val vpa = vpas.firstOrNull()
        val bank = bankHints.firstOrNull { it.second.containsMatchIn(sender) || it.second.containsMatchIn(text) }?.first

        val channel = when {
            Regex("\\b(atm|cash withdrawal|withdrawn)\\b", IC).containsMatchIn(text) -> Channel.ATM
            Regex("\\b(autopay|auto pay|mandate|nach|ecs|standing instruction|\\bsi\\b|e-?mandate)\\b", IC).containsMatchIn(text) -> Channel.AUTOPAY
            Regex("\\bupi\\b", IC).containsMatchIn(text) || vpa != null -> Channel.UPI
            Regex("\\bimps\\b", IC).containsMatchIn(text) -> Channel.IMPS
            Regex("\\bneft\\b", IC).containsMatchIn(text) -> Channel.NEFT
            Regex("\\brtgs\\b", IC).containsMatchIn(text) -> Channel.RTGS
            // "card" may be glued to a brand name (superCard, OneCard), so no leading word boundary
            Regex("(card|\\bpos|\\bswipe|\\becom|\\be-com)\\b", IC).containsMatchIn(text) -> Channel.CARD
            Regex("\\b(wallet)\\b", IC).containsMatchIn(text) -> Channel.WALLET
            Regex("\\b(net ?banking|inb|internet banking)\\b", IC).containsMatchIn(text) -> Channel.NETBANKING
            else -> Channel.OTHER
        }

        val counterparty = extractCounterparty(text, vpa)
        var conf = 0.5f
        if (account != null) conf += 0.15f
        if (ref != null) conf += 0.15f
        if (counterparty != null || vpa != null) conf += 0.1f
        if (bank != null) conf += 0.1f

        return ParsedTxn(
            amountPaise = amount,
            direction = direction,
            channel = channel,
            bank = bank,
            account = account,
            counterparty = counterparty,
            vpa = vpa,
            ref = ref,
            balancePaise = balance,
            confidence = conf.coerceAtMost(1f),
            failed = failedRx.containsMatchIn(text),
        )
    }

    private fun extractCounterparty(text: String, vpa: String?): String? {
        for (p in merchantPatterns) {
            for (m in p.findAll(text)) {
                val raw = m.groupValues[1].trim().trimEnd('.', ',', '-', ' ')
                if (raw.contains("@")) return vpaToName(raw)
                if (raw.length < 2 || junkCounterparty.matches(raw)) continue
                if (Regex("^(a/?c|ac|account|card)\\b", IC).containsMatchIn(raw)) continue
                if (Regex("^[x*]+\\d+$", IC).matches(raw)) continue
                return prettify(raw)
            }
        }
        return vpa?.let(::vpaToName)
    }

    /** "swiggy@icici" → "Swiggy", "9876543210@ybl" → "9876543210", "paytmqr28100505@paytm" → "Paytm merchant". */
    fun vpaToName(vpa: String): String {
        val handle = vpa.substringBefore("@").lowercase()
        return when {
            handle.startsWith("paytmqr") || handle.startsWith("paytm-") -> "Paytm merchant"
            handle.startsWith("q") && handle.drop(1).all { it.isDigit() } && handle.length > 6 -> "PhonePe merchant"
            handle.startsWith("bharatpe") -> "BharatPe merchant"
            handle.matches(Regex("\\d{10}")) -> handle
            else -> prettify(handle.replace(Regex("[._\\-]+"), " ").replace(Regex("\\d{4,}"), "").trim().ifBlank { handle })
        }
    }

    private fun prettify(s: String): String =
        s.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() || it.isDigit() }) w
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }.take(40)

    fun toPaise(s: String): Long? {
        val clean = s.replace(",", "")
        val parts = clean.split(".")
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return rupees * 100 + paise
    }
}

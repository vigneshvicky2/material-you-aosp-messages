package com.rodin.messages.finance

/** Assigns a category from merchant / VPA / SMS text. User rules (by counterparty key) win. */
object Categorizer {
    private val dictionary: List<Pair<Cat, List<String>>> = listOf(
        Cat.FOOD to listOf("swiggy", "zomato", "dominos", "domino", "mcdonald", "kfc", "pizza", "burger", "starbucks", "cafe",
            "coffee", "restaurant", "hotel", "eatsure", "faasos", "behrouz", "biryani", "bakery", "sweets", "chai", "tea ", "a2b",
            "adyar ananda", "saravana", "haldiram", "subway", "barbeque", "dunzo"),
        Cat.GROCERIES to listOf("blinkit", "zepto", "bigbasket", "big basket", "instamart", "dmart", "d mart", "avenue supermarts",
            "jiomart", "reliance fresh", "reliance retail", "more retail", "grocery", "supermarket", "kirana", "nilgiris",
            "spencer", "milk", "aavin", "vegetable", "fruits", "country delight", "licious", "freshtohome"),
        Cat.SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "reliance digital", "tata cliq",
            "decathlon", "ikea", "lenskart", "firstcry", "snapdeal", "shopsy", "trends", "westside", "zudio", "max fashion",
            "pantaloons", "lifestyle", "apple", "samsung", "mi store", "vijay sales", "poorvika", "sangeetha"),
        Cat.TRAVEL to listOf("uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "cleartrip", "indigo",
            "air india", "akasa", "spicejet", "vistara", "metro", "namma yatri", "yatri", "fastag", "toll", "parking", "ixigo",
            "abhibus", "railway", "cab", "auto"),
        Cat.FUEL to listOf("petrol", "fuel", "hpcl", "iocl", "bpcl", "indian oil", "bharat petroleum", "hindustan petroleum",
            "shell", "nayara", "filling station", "service station"),
        Cat.BILLS to listOf("jio", "airtel", "vodafone", "vi ", "vodafone idea", "bsnl", "electricity", "tneb", "tangedco", "bescom",
            "mseb", "tata power", "adani", "broadband", "act fibernet", "hathway", "recharge", "dth", "tata play", "sun direct",
            "gas", "indane", "bharatgas", "hp gas", "water bill", "bill payment", "bbps", "postpaid", "prepaid"),
        Cat.ENTERTAINMENT to listOf("netflix", "prime video", "primevideo", "hotstar", "disney", "spotify", "bookmyshow", "pvr", "inox",
            "youtube", "google play", "sonyliv", "zee5", "jiocinema", "gaana", "apple.com", "steam", "playstation"),
        Cat.HEALTH to listOf("pharmacy", "pharma", "medical", "apollo", "medplus", "1mg", "pharmeasy", "netmeds", "hospital",
            "clinic", "diagnostic", "lab", "healthcare", "dental", "practo", "cult.fit", "cultfit", "gym"),
        Cat.EDUCATION to listOf("school", "college", "university", "tuition", "course", "udemy", "coursera", "byju", "unacademy",
            "exam fee", "books"),
        Cat.RENT to listOf("rent", "nobroker", "housing", "maintenance", "society", "urban company", "urbanclap"),
        Cat.EMI to listOf("emi", "loan", "bajaj finserv", "bajaj finance", "home credit", "kreditbee", "navi", "moneyview",
            "lazypay", "simpl", "slice", "nach", "ecs"),
        Cat.INVESTMENT to listOf("zerodha", "groww", "upstox", "angel one", "angelone", "kuvera", "coin", "sip", "mutual fund",
            "cams", "kfin", "kfintech", "nps", "ppf", "indian clearing", "iccl", "nse clearing", "bse", "smallcase", "gold"),
        Cat.INSURANCE to listOf("lic", "insurance", "policy", "premium", "acko", "digit", "star health", "hdfc ergo", "icici lombard",
            "max life", "policybazaar"),
    )

    private val salary = Regex("\\b(salary|sal for|payroll|sal cr|stipend)\\b", RegexOption.IGNORE_CASE)
    private val interest = Regex("\\b(interest|int\\.? pd|int cr)\\b", RegexOption.IGNORE_CASE)
    private val cashback = Regex("\\b(cashback|cash back|reward|scratch card|super\\.?money)\\b", RegexOption.IGNORE_CASE)
    private val refund = Regex("\\b(refund|reversal|reversed|returned)\\b", RegexOption.IGNORE_CASE)

    /** Short keys ("ola", "jio", "emi") must match as whole words so "cola" or "premium" don't hit. */
    private fun matches(hay: String, word: String): Boolean {
        val w = word.trim()
        if (!hay.contains(w)) return false
        if (w.length > 4) return true
        return Regex("(^|[^a-z])${Regex.escape(w)}([^a-z]|$)").containsMatchIn(hay)
    }

    /** Stable key for "always categorise this counterparty as …" rules. */
    fun ruleKey(t: ParsedTxn): String? = (t.vpa ?: t.counterparty)?.lowercase()?.trim()?.takeIf { it.length >= 2 }

    fun categorize(t: ParsedTxn, body: String, rules: Map<String, Cat>, isContact: (String) -> Boolean = { false }): Cat {
        ruleKey(t)?.let { k -> rules[k]?.let { return it } }
        val hay = listOfNotNull(t.counterparty, t.vpa, body).joinToString(" ").lowercase()

        if (t.direction == Direction.CREDIT) {
            if (refund.containsMatchIn(body)) return Cat.REFUND
            if (cashback.containsMatchIn(body)) return Cat.CASHBACK
            if (salary.containsMatchIn(body)) return Cat.INCOME
            if (interest.containsMatchIn(body)) return Cat.INCOME
        }
        if (t.channel == Channel.ATM) return Cat.CASH

        for ((cat, words) in dictionary) {
            if (words.any { w -> matches(hay, w) }) return cat
        }
        if (t.channel == Channel.AUTOPAY) return Cat.BILLS

        // Person-to-person: phone-number VPA, a saved contact, or a plain personal name
        val cp = t.counterparty ?: ""
        val vpaHandle = t.vpa?.substringBefore("@") ?: ""
        if (vpaHandle.matches(Regex("\\d{10}")) || isContact(cp) ||
            (t.channel == Channel.UPI && cp.split(" ").size in 1..4 && cp.all { it.isLetter() || it == ' ' || it == '.' } && cp.length > 2)
        ) {
            return Cat.PEOPLE
        }
        if (t.direction == Direction.CREDIT) return Cat.INCOME
        return Cat.OTHER
    }
}

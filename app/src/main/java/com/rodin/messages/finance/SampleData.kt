package com.rodin.messages.finance

import java.util.Calendar
import kotlin.random.Random

/** Realistic, in-memory-only sample month used to preview the Money screens. Never saved. */
object SampleData {
    private data class M(val name: String, val vpa: String?, val cat: Cat, val ch: Channel, val min: Int, val max: Int, val acc: String, val bank: String)

    private val merchants = listOf(
        M("Swiggy", "swiggy@icici", Cat.FOOD, Channel.UPI, 180, 650, "4562", "IOB"),
        M("Zomato", "zomato@hdfcbank", Cat.FOOD, Channel.UPI, 220, 720, "4562", "IOB"),
        M("Blinkit", "blinkit@axl", Cat.GROCERIES, Channel.UPI, 140, 900, "4562", "IOB"),
        M("Zepto", "zepto@ybl", Cat.GROCERIES, Channel.UPI, 90, 600, "7781", "SBI"),
        M("Amazon", null, Cat.SHOPPING, Channel.CARD, 299, 2499, "3310", "super.money"),
        M("Rapido", "rapido@ybl", Cat.TRAVEL, Channel.UPI, 45, 180, "4562", "IOB"),
        M("Uber", "uber@icici", Cat.TRAVEL, Channel.UPI, 120, 420, "7781", "SBI"),
        M("Indian Oil", null, Cat.FUEL, Channel.CARD, 500, 1500, "3310", "super.money"),
        M("Apollo Pharmacy", "apollopharmacy@icici", Cat.HEALTH, Channel.UPI, 150, 800, "4562", "IOB"),
        M("Tea shop", "9000000001@superyes", Cat.FOOD, Channel.UPI, 20, 60, "4562", "IOB"),
    )

    fun month(start: Long, days: Int, today: Int): List<Txn> {
        val r = Random(start / 86_400_000)
        val out = ArrayList<Txn>()
        var id = -1L
        val cal = Calendar.getInstance()
        val lastDay = if (today >= 0) today else days - 1
        var bal = 1_842_050L

        fun at(day: Int, hour: Int): Long {
            cal.timeInMillis = start
            cal.add(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, r.nextInt(60))
            return cal.timeInMillis
        }

        fun add(
            day: Int, hour: Int, name: String, vpa: String?, cat: Cat, ch: Channel, paise: Long, dir: Direction,
            acc: String, bank: String, excluded: Boolean = false, balance: Long? = null,
        ) {
            val target = vpa ?: name
            val body = if (dir == Direction.DEBIT) {
                "Your a/c no. XX$acc is debited for Rs.${paise / 100}.00 via ${ch.label} to $target. (Sample)"
            } else {
                "Your a/c no. XX$acc is credited by Rs.${paise / 100}.00 from $target. (Sample)"
            }
            out += Txn(
                id = id--, smsId = null, sender = "SAMPLE", bank = bank, account = acc, amountPaise = paise,
                direction = dir, channel = ch, counterparty = name, vpa = vpa, ref = "SMPL${r.nextInt(100000, 999999)}",
                balancePaise = balance, time = at(day, hour), category = cat, note = null, excluded = excluded,
                duplicateOf = null, confidence = 0.95f, body = body, manual = false,
            )
        }

        add(0, 10, "Acme Tech Pvt Ltd", null, Cat.INCOME, Channel.NEFT, 4_500_000, Direction.CREDIT, "4562", "IOB",
            balance = bal + 4_500_000)
        if (lastDay >= 2) add(2, 9, "Jio Recharge", "jio@okicici", Cat.BILLS, Channel.UPI, 34_900, Direction.DEBIT, "7781", "SBI")
        if (lastDay >= 3) add(3, 21, "Priya", "9876543210@okaxis", Cat.PEOPLE, Channel.UPI, 75_000, Direction.DEBIT, "4562", "IOB")
        if (lastDay >= 4) add(4, 20, "Netflix", null, Cat.ENTERTAINMENT, Channel.AUTOPAY, 19_900, Direction.DEBIT, "3310", "super.money")
        if (lastDay >= 5) add(5, 7, "House rent", "owner.rent@oksbi", Cat.RENT, Channel.UPI, 1_200_000, Direction.DEBIT, "4562", "IOB")
        if (lastDay >= 6) add(6, 18, "ATM withdrawal", null, Cat.CASH, Channel.ATM, 200_000, Direction.DEBIT, "7781", "SBI")
        if (lastDay >= 7) {
            add(7, 12, "IOB to SBI", null, Cat.TRANSFER, Channel.IMPS, 500_000, Direction.DEBIT, "4562", "IOB", excluded = true)
            add(7, 12, "IOB to SBI", null, Cat.TRANSFER, Channel.IMPS, 500_000, Direction.CREDIT, "7781", "SBI", excluded = true)
        }
        for (d in 0..lastDay) {
            repeat(r.nextInt(0, 4)) {
                val m = merchants[r.nextInt(merchants.size)]
                val amt = r.nextInt(m.min, m.max + 1) * 100L
                bal -= amt
                add(d, r.nextInt(8, 23), m.name, m.vpa, m.cat, m.ch, amt, Direction.DEBIT, m.acc, m.bank,
                    balance = if (m.bank == "IOB") bal else null)
                if (r.nextInt(6) == 0) {
                    add(d, 23, "super.money cashback", "cashback@superyes", Cat.CASHBACK, Channel.UPI,
                        r.nextInt(2, 40) * 100L, Direction.CREDIT, "4562", "IOB")
                }
            }
        }
        return out.sortedByDescending { it.time }
    }

    fun accounts(txns: List<Txn>): List<AccountSummary> =
        txns.groupBy { it.account ?: "" }.filterKeys { it.isNotEmpty() }.map { (acc, list) ->
            val withBal = list.filter { it.balancePaise != null }.maxByOrNull { it.time }
            AccountSummary(list.first().bank, acc, withBal?.balancePaise, withBal?.time, list.size)
        }
}

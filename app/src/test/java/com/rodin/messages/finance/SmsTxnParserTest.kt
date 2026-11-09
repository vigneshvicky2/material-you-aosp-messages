package com.rodin.messages.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Representative samples of Indian bank SMS formats (SBI, IOB, cards, wallets).
 * These approximate real formats; real messages from the device should be added as fixtures.
 */
class SmsTxnParserTest {

    private fun p(sender: String, body: String) = SmsTxnParser.parse(sender, body)

    // ------------------------------------------------------------------ SBI
    @Test fun sbiUpiDebit() {
        val t = p("VM-SBIUPI-S", "Dear UPI user A/C X1234 debited by 350.0 on date 08Nov26 trf to SWIGGY Refno 431298765012. If not u? call 1800111109. -SBI")!!
        assertEquals(35000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("1234", t.account)
        assertEquals("431298765012", t.ref)
        assertEquals("SBI", t.bank)
        assertEquals("Swiggy", t.counterparty)
        assertEquals(Cat.FOOD, Categorizer.categorize(t, "trf to SWIGGY", emptyMap()))
    }

    @Test fun sbiUpiCredit() {
        val t = p("AD-SBIUPI-S", "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 08Nov26 by  (Ref no 431212345678)")!!
        assertEquals(50000, t.amountPaise)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("1234", t.account)
        assertEquals("431212345678", t.ref)
    }

    @Test fun sbiAccountDebitWithBalance() {
        val t = p("JD-SBIINB-S", "Your A/C XXXXX1234 Debited INR 2,000.00 on 08/11/26 -Transferred to Mr. RAMESH KUMAR. Avl Balance INR 12,345.67-SBI")!!
        assertEquals(200000, t.amountPaise)
        assertEquals(1234567L, t.balancePaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("1234", t.account)
    }

    @Test fun sbiAtm() {
        val t = p("VK-ATMSBI-S", "Dear Customer, Rs 500.00 withdrawn at SBI ATM S1BW000123 from A/cX1234 on 08Nov26 Transaction Number 2345. Available Balance Rs 10,000.00. -SBI")!!
        assertEquals(50000, t.amountPaise)
        assertEquals(Channel.ATM, t.channel)
        assertEquals(1000000L, t.balancePaise)
        assertEquals(Cat.CASH, Categorizer.categorize(t, "withdrawn at SBI ATM", emptyMap()))
    }

    // ------------------------------------------------------------------ IOB
    @Test fun iobUpiDebitThenCreditedToOther() {
        val t = p("AX-IOBCHN-S", "Your a/c no. XXXXXXXX5678 is debited for Rs.1,250.00 on 08-11-2026 and credited to a/c no. XXXXXX9012 (UPI Ref no 431277778888) -IOB")!!
        assertEquals(125000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("5678", t.account)
        assertEquals("431277778888", t.ref)
        assertEquals("IOB", t.bank)
    }

    @Test fun iobCreditFromVpa() {
        val t = p("AX-IOBCHN-S", "Your a/c no. XX5678 is credited by Rs.5.00 on 08-11-26 by a/c linked to VPA cashback@superyes (UPI Ref no 431266665555) -IOB")!!
        assertEquals(500, t.amountPaise)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("cashback@superyes", t.vpa)
        assertEquals(Channel.UPI, t.channel)
        assertEquals(Cat.CASHBACK, Categorizer.categorize(t, "VPA cashback@superyes", emptyMap()))
    }

    @Test fun iobDebitWithBalance() {
        val t = p("BP-IOBBNK-S", "A/c XX5678 debited Rs.200.00 on 08-11-2026 via UPI to zomato@hdfcbank. Avl Bal Rs.8,450.25 -IOB")!!
        assertEquals(20000, t.amountPaise)
        assertEquals(845025L, t.balancePaise)
        assertEquals("Zomato", t.counterparty)
    }

    // ------------------------------------------------------------------ cards / others
    @Test fun creditCardSpend() {
        val t = p("AX-SUPRCD-S", "INR 349.00 spent on your superCard XX4321 at NETFLIX on 08-11-26. Avl limit: INR 48,651.00")!!
        assertEquals(34900, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals(Channel.CARD, t.channel)
        assertEquals("4321", t.account)
        assertEquals("super.money", t.bank)
        assertEquals("Netflix", t.counterparty)
        assertEquals(Cat.ENTERTAINMENT, Categorizer.categorize(t, "at NETFLIX", emptyMap()))
    }

    @Test fun hdfcStyleUpi() {
        val t = p("VM-HDFCBK-T", "Rs.350.00 debited from A/c XX4821 on 08-11-26 to VPA swiggy@icici (UPI Ref 431298765012). Avl Bal Rs.12,840.55 -HDFC Bank")!!
        assertEquals(35000, t.amountPaise)
        assertEquals("swiggy@icici", t.vpa)
        assertEquals("Swiggy", t.counterparty)
        assertEquals(1284055L, t.balancePaise)
    }

    @Test fun salaryCredit() {
        val t = p("JD-SBIINB-S", "Your A/c XX1234 is credited with INR 45,000.00 on 01-11-26 by NEFT SALARY ACME TECH PVT LTD. Avl Bal INR 57,345.67")!!
        assertEquals(4500000, t.amountPaise)
        assertEquals(Channel.NEFT, t.channel)
        assertEquals(Cat.INCOME, Categorizer.categorize(t, "NEFT SALARY ACME", emptyMap()))
    }

    @Test fun peopleTransfer() {
        val t = p("VM-SBIUPI-S", "Dear UPI user A/C X1234 debited by 200.0 on date 08Nov26 trf to PRIYA S Refno 431211112222. If not u? call 1800111109. -SBI")!!
        assertEquals(Cat.PEOPLE, Categorizer.categorize(t, "trf to PRIYA S", emptyMap()))
    }

    @Test fun userRuleWins() {
        val t = p("VM-SBIUPI-S", "Dear UPI user A/C X1234 debited by 350.0 on date 08Nov26 trf to SWIGGY Refno 431298765012. -SBI")!!
        assertEquals(Cat.GROCERIES, Categorizer.categorize(t, "", mapOf("swiggy" to Cat.GROCERIES)))
    }

    // ------------------------------------------------------------------ must be ignored
    @Test fun ignoresOtp() {
        assertNull(p("AX-SBIOTP-S", "482913 is your OTP for a transaction of Rs. 2,499.00 at AMAZON on your SBI card ending 4821. Valid for 5 mins."))
    }

    @Test fun ignoresPromo() {
        assertNull(p("VK-SBICRD-P", "Get a pre-approved personal loan of up to Rs 5,00,000 at attractive rates. Apply now!"))
    }

    @Test fun ignoresCollectRequest() {
        assertNull(p("VM-SBIUPI-S", "RAHUL has requested money of Rs 500 from you on Google Pay. Approve only if you know them."))
    }

    @Test fun ignoresDueReminder() {
        assertNull(p("AX-SUPRCD-S", "Your superCard statement is generated. Total amount due Rs 4,560.00, minimum due Rs 230.00, due on 15-11-26."))
    }

    @Test fun ignoresPersonalSms() {
        assertNull(p("+919876543210", "I paid Rs 500 to the shop, will send you the rest"))
    }

    @Test fun flagsFailedTxn() {
        val t = p("VM-SBIUPI-S", "Your UPI txn of Rs 999.00 to amazon@apl failed. Amount debited, if any, will be reversed. Ref 431200001111 -SBI")
        assertNotNull(t)
        assertTrue(t!!.failed)
    }

    // ------------------------------------------------------------------ real non-transaction SMS seen on the device
    // (account digits masked). None of these may produce a transaction.
    @Test fun ignoresRealRegistrationAndOtpMessages() {
        val samples = listOf(
            "JD-YESBNK-S" to "Dear Customer, a cooling period limit of Rs. 5000 is applicable for the first 24 hours. Never share UPI PIN, CVV & OTP with anyone - YES BANK",
            "JM-YESBNK-S" to "Dear Customer, registration for SUPERMONEY has started for YES BANK. If it was not you, report to your bank. Do not share card details/OTP/CVV with anyone.",
            "JK-YESBNK-S" to "<#> 916282 is your OTP for SUPERMONEY UPI Registration. Valid for 45 secs. Do not share with anyone. Report to your bank if not you - Yes Bank ovdh8HabmIB",
            "VM-SUPRMY-S" to "546526 is your one time password to proceed on SuperMoney. Do not share your OTP with anyone.  Dxxi6XlXp7g",
            "JD-HDFCBK-S" to "UPI Activated!",
            "BV-IOBCHN-S" to "Dear Customer, UPI linking request received for XXXXX00. Do not share Debit Card/OTP/Expiry details. If not initiated by you, call 18008904445/18004254445.-IOB",
            "JD-AXISBK-S" to "UPI registration on Google Pay has started. Do not share Debit Card details/OTP/expiry date to avoid financial loss. Not you? Report to your bank - Axis Bank",
            "AX-HDFCBK-S" to "UPI Registration Initiated!",
            "JD-ICICIT-S" to "Google Pay registration is initiated for ICICI Bank. If not done by you, report to your bank. Card details/OTP/CVV are SECRET. DO NOT disclose it to anyone.",
            "CP-ZOMATO-S" to "Hello, 717592 is the  OTP  for Zomato App login using your phone number. Do not share it to anyone. AlOe4TVDpqm -ZOMATO",
            "TX-SWIGGY-S" to "Use OTP 390837 to log into your Swiggy account. Do not share the OTP or your number with anyone including Swiggy personnel. ASkSzFWMk3x",
            "AX-RAPIDO-S" to "<#> 531377 is the OTP for Rapido app verification. Keep it confidential for your security. Vku3h7qjnN2 - Rapido",
        )
        samples.forEach { (sender, body) -> assertNull("should ignore: $body", p(sender, body)) }
    }

    // ------------------------------------------------------------------ helpers
    @Test fun rupeeFormatting() {
        assertEquals("₹12,34,567", formatRupees(123456700))
        assertEquals("₹350", formatRupees(35000))
        assertEquals("₹5.50", formatRupees(550))
        assertEquals("-₹1,250", formatRupees(-125000))
    }

    @Test fun vpaNames() {
        assertEquals("Swiggy", SmsTxnParser.vpaToName("swiggy@icici"))
        assertEquals("Paytm merchant", SmsTxnParser.vpaToName("paytmqr281005050101@paytm"))
        assertEquals("9876543210", SmsTxnParser.vpaToName("9876543210@ybl"))
    }

}

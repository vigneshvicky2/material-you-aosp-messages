package com.rodin.messages.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Approximate screen layouts of GPay / super.money; replace with real captures from the device. */
class UpiScreenParserTest {

    @Test fun gpayPaymentDetails() {
        val t = UpiScreenParser.parse(listOf(
            "To Swiggy", "₹350", "Completed", "8 Nov 2026, 7:49 pm",
            "Indian Overseas Bank 4562", "UPI transaction ID", "431298765012",
            "To: SWIGGY", "swiggy@icici", "From: You (Indian Overseas Bank ••62)", "Google transaction ID", "CICAgMDA1234",
        ))!!
        assertEquals(35000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("Swiggy", t.payee)
        assertEquals("431298765012", t.ref)
        assertEquals("swiggy@icici", t.vpa)
        assertEquals("IOB", t.bank)
        assertNotNull(t.time)
    }

    @Test fun gpayReceived() {
        val t = UpiScreenParser.parse(listOf(
            "From Rahul Kumar", "₹500", "Completed", "7 Nov 2026, 9:02 am",
            "UPI transaction ID", "431211112222", "To: You (State Bank of India ••81)", "From: RAHUL KUMAR", "rahul.k@okaxis",
        ))!!
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("Rahul Kumar", t.payee)
        assertEquals(50000, t.amountPaise)
        assertEquals("SBI", t.bank)
    }

    @Test fun superMoneySuccess() {
        val t = UpiScreenParser.parse(listOf(
            "Payment Successful", "₹1,250.00", "Paid to Blinkit", "blinkit@axl", "UPI Ref No: 431277778888", "Paid via UPI Lite", "Done",
        ))!!
        assertEquals(125000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("Blinkit", t.payee)
        assertEquals("431277778888", t.ref)
        assertTrue(t.upiLite)
    }

    @Test fun ignoresPending() {
        assertNull(UpiScreenParser.parse(listOf("To Swiggy", "₹350", "Pending", "Payment is processing", "UPI transaction ID", "431298765012")))
    }

    @Test fun ignoresFailed() {
        assertNull(UpiScreenParser.parse(listOf("Payment failed", "₹350", "To Swiggy", "Money will be refunded if debited")))
    }

    @Test fun ignoresHomeScreen() {
        assertNull(UpiScreenParser.parse(listOf("Pay anyone", "Scan any QR code", "Pay phone number", "Bank transfer", "Check balance", "People", "Businesses")))
    }

    @Test fun ignoresPayingScreenBeforeConfirm() {
        assertNull(UpiScreenParser.parse(listOf("Paying Swiggy", "₹350", "Add a note", "Proceed to pay", "IOB ••62")))
    }
}

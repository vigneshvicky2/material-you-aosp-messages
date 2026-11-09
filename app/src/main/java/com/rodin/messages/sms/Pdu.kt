package com.rodin.messages.sms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Minimal OMA MMS encapsulation (WAP binary) encoder/decoder.
 * Supports what a messaging client needs: m-send-req (encode),
 * m-notification-ind and m-retrieve-conf (decode).
 */
object Pdu {
    // Header field codes (already OR'ed with 0x80)
    const val H_CC = 0x82
    const val H_CONTENT_LOCATION = 0x83
    const val H_CONTENT_TYPE = 0x84
    const val H_DATE = 0x85
    const val H_FROM = 0x89
    const val H_MESSAGE_ID = 0x8B
    const val H_MESSAGE_TYPE = 0x8C
    const val H_MMS_VERSION = 0x8D
    const val H_MESSAGE_SIZE = 0x8E
    const val H_SUBJECT = 0x96
    const val H_TO = 0x97
    const val H_TRANSACTION_ID = 0x98

    const val MT_SEND_REQ = 0x80
    const val MT_NOTIFICATION_IND = 0x82
    const val MT_RETRIEVE_CONF = 0x84

    private val wellKnownMedia = mapOf(
        0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain",
        0x06 to "text/x-vcalendar", 0x07 to "text/x-vcard", 0x0C to "multipart/mixed",
        0x0F to "multipart/alternative", 0x1C to "image/*", 0x1D to "image/gif", 0x1E to "image/jpeg",
        0x1F to "image/tiff", 0x20 to "image/png", 0x21 to "image/vnd.wap.wbmp",
        0x23 to "application/vnd.wap.multipart.mixed", 0x26 to "application/vnd.wap.multipart.alternative",
        0x27 to "application/xml", 0x28 to "text/xml", 0x33 to "application/vnd.wap.multipart.related",
    )
    private val mediaCode = wellKnownMedia.entries.associate { (k, v) -> v to k }

    class Part(val contentType: String, val data: ByteArray, val name: String? = null, val cid: String? = null, val charset: Int = 106) {
        val text: String? get() = if (contentType.startsWith("text/")) String(data, charsetFor(charset)) else null
    }

    class Msg(
        val type: Int,
        val headers: Map<Int, Any>,
        val parts: List<Part>,
        val contentType: String?,
    ) {
        val transactionId get() = headers[H_TRANSACTION_ID] as? String
        val contentLocation get() = headers[H_CONTENT_LOCATION] as? String
        val from get() = (headers[H_FROM] as? String)?.let(::cleanAddress)
        val subject get() = headers[H_SUBJECT] as? String
        val messageId get() = headers[H_MESSAGE_ID] as? String
        val date get() = headers[H_DATE] as? Long
        val to get() = (headers[H_TO] as? List<*>)?.filterIsInstance<String>()?.map(::cleanAddress).orEmpty()
    }

    fun cleanAddress(a: String) = a.substringBefore("/TYPE=").trim()

    fun charsetFor(mib: Int): Charset = when (mib) {
        3 -> Charsets.US_ASCII
        4 -> Charsets.ISO_8859_1
        1000, 1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    // ------------------------------------------------------------ encode

    fun encodeSendReq(to: List<String>, parts: List<Part>, subject: String? = null): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(H_MESSAGE_TYPE); o.write(MT_SEND_REQ)
        o.write(H_TRANSACTION_ID); text(o, "T" + java.lang.Long.toHexString(System.currentTimeMillis()))
        o.write(H_MMS_VERSION); o.write(0x92) // 1.2
        o.write(H_FROM); o.write(0x01); o.write(0x81) // insert-address-token
        to.forEach { o.write(H_TO); text(o, formatAddress(it)) }
        if (!subject.isNullOrBlank()) {
            o.write(H_SUBJECT)
            val enc = ByteArrayOutputStream().apply { write(0xEA); text(this, subject) }.toByteArray()
            valueLength(o, enc.size); o.write(enc)
        }
        o.write(H_CONTENT_TYPE); o.write(0x80 or 0x23) // application/vnd.wap.multipart.mixed
        uintvar(o, parts.size)
        parts.forEach { p ->
            val h = ByteArrayOutputStream()
            // content type
            val code = mediaCode[p.contentType]
            if (p.contentType.startsWith("text/")) {
                val ct = ByteArrayOutputStream()
                if (code != null) ct.write(0x80 or code) else text(ct, p.contentType)
                ct.write(0x81); ct.write(0xEA) // charset=utf-8
                valueLength(h, ct.size()); h.write(ct.toByteArray())
            } else if (code != null) {
                h.write(0x80 or code)
            } else {
                text(h, p.contentType)
            }
            p.name?.let { h.write(0x8E); text(h, it) } // Content-Location
            p.cid?.let { h.write(0xC0); h.write(0x22); h.write(it.toByteArray()); h.write(0) } // Content-ID
            val hb = h.toByteArray()
            uintvar(o, hb.size); uintvar(o, p.data.size)
            o.write(hb); o.write(p.data)
        }
        return o.toByteArray()
    }

    private fun formatAddress(a: String): String =
        if (a.contains("@")) a else a.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"

    private fun text(o: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) o.write(0x7F)
        o.write(b); o.write(0)
    }

    private fun valueLength(o: ByteArrayOutputStream, len: Int) {
        if (len <= 30) o.write(len) else { o.write(31); uintvar(o, len) }
    }

    private fun uintvar(o: ByteArrayOutputStream, v: Int) {
        var value = v
        val tmp = ArrayList<Int>()
        tmp += value and 0x7F
        value = value ushr 7
        while (value > 0) { tmp += (value and 0x7F) or 0x80; value = value ushr 7 }
        tmp.reversed().forEach { o.write(it) }
    }

    // ------------------------------------------------------------ decode

    private class Reader(val b: ByteArray, var p: Int = 0) {
        fun more() = p < b.size
        fun peek() = b[p].toInt() and 0xFF
        fun u8() = b[p++].toInt() and 0xFF
        fun uintvar(): Int {
            var r = 0
            while (true) { val x = u8(); r = (r shl 7) or (x and 0x7F); if (x and 0x80 == 0) return r }
        }
        fun valueLength(): Int { val x = u8(); return if (x < 31) x else uintvar() }
        fun text(): String {
            if (peek() == 0x7F || peek() == 0x22) p++
            val s = p
            while (p < b.size && b[p].toInt() != 0) p++
            val r = String(b, s, p - s, Charsets.UTF_8)
            if (p < b.size) p++
            return r
        }
        fun longInt(): Long {
            val n = u8(); var r = 0L
            repeat(n) { r = (r shl 8) or u8().toLong() }
            return r
        }
        fun integer(): Long = if (peek() >= 0x80) (u8() and 0x7F).toLong() else longInt()
        /** Skip any WSP value using the generic first-octet rule. */
        fun skipValue() {
            val x = peek()
            when {
                x < 31 -> { p++; p += x }
                x == 31 -> { p++; val l = uintvar(); p += l }
                x < 128 -> text()
                else -> p++
            }
        }
        fun encodedString(): String {
            val x = peek()
            if (x < 32) {
                val len = valueLength(); val end = p + len
                val cs = integer().toInt()
                if (peek() == 0x7F) p++
                val s = p
                while (p < end && b[p].toInt() != 0) p++
                val r = String(b, s, p - s, charsetFor(cs))
                p = end
                return r
            }
            return text()
        }
    }

    private class CType(val type: String, val charset: Int, val name: String?)

    private fun contentType(r: Reader): CType {
        val x = r.peek()
        if (x >= 0x80) { r.p++; return CType(wellKnownMedia[x and 0x7F] ?: "application/octet-stream", 106, null) }
        if (x >= 32) return CType(r.text(), 106, null)
        val len = r.valueLength(); val end = r.p + len
        val type = if (r.peek() >= 0x80) wellKnownMedia[r.u8() and 0x7F] ?: "application/octet-stream" else r.text()
        var cs = 106; var name: String? = null
        while (r.p < end) {
            val param = r.peek()
            if (param >= 0x80) {
                r.p++
                when (param and 0x7F) {
                    0x01 -> cs = r.integer().toInt()            // charset
                    0x05, 0x17 -> name = r.text()               // name
                    0x06, 0x18 -> name = name ?: r.text()       // filename
                    else -> r.skipValue()
                }
            } else {
                r.text(); if (r.p < end) r.skipValue()      // untyped param
            }
        }
        r.p = end
        return CType(type, cs, name)
    }

    fun parse(bytes: ByteArray): Msg? = runCatching {
        val r = Reader(bytes)
        val headers = HashMap<Int, Any>()
        var type = -1
        var ctype: String? = null
        val to = ArrayList<String>()
        while (r.more()) {
            val h = r.u8()
            if (h < 0x80) { r.p--; r.text(); r.skipValue(); continue } // application header
            when (h) {
                H_MESSAGE_TYPE -> type = r.u8()
                H_TRANSACTION_ID, H_CONTENT_LOCATION, H_MESSAGE_ID -> headers[h] = r.text()
                H_SUBJECT -> headers[h] = r.encodedString()
                H_TO, H_CC -> to += r.encodedString()
                H_FROM -> {
                    val len = r.valueLength(); val end = r.p + len
                    if (r.peek() == 0x80) { r.p++; headers[h] = r.encodedString() }
                    r.p = end
                }
                H_DATE -> headers[h] = r.longInt()
                H_MESSAGE_SIZE -> headers[h] = r.longInt()
                H_CONTENT_TYPE -> { ctype = contentType(r).type; break }
                else -> r.skipValue()
            }
        }
        headers[H_TO] = to
        val parts = ArrayList<Part>()
        if (type == MT_RETRIEVE_CONF && r.more()) {
            if (ctype?.contains("multipart") == true) {
                val n = r.uintvar()
                repeat(n) {
                    val hl = r.uintvar(); val dl = r.uintvar()
                    val hEnd = r.p + hl
                    val ct = contentType(r)
                    var name = ct.name; var cid: String? = null
                    while (r.p < hEnd) {
                        val ph = r.peek()
                        if (ph >= 0x80) {
                            r.p++
                            when (ph) {
                                0x8E -> name = name ?: r.text()
                                0xC0 -> cid = r.text().trim('<', '>')
                                else -> r.skipValue()
                            }
                        } else { r.text(); if (r.p < hEnd) r.text() }
                    }
                    r.p = hEnd
                    val data = bytes.copyOfRange(r.p, minOf(r.p + dl, bytes.size))
                    r.p += dl
                    parts += Part(ct.type, data, name, cid, ct.charset)
                }
            } else {
                parts += Part(ctype ?: "text/plain", bytes.copyOfRange(r.p, bytes.size))
            }
        }
        Msg(type, headers, parts, ctype)
    }.getOrNull()
}

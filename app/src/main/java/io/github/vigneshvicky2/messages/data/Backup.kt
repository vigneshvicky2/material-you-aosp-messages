package io.github.vigneshvicky2.messages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON backup of all SMS + app metadata (pins, stars, reactions, settings). */
object Backup {
    data class Result(val messages: Int, val skipped: Int)

    fun export(ctx: Context, dest: Uri): Int {
        val arr = JSONArray()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("address", "body", "date", "date_sent", "type", "read", "status", "sub_id"),
            null, null, "date ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("address", c.getString(0) ?: "")
                    put("body", c.getString(1) ?: "")
                    put("date", c.getLong(2))
                    put("date_sent", c.getLong(3))
                    put("type", c.getInt(4))
                    put("read", c.getInt(5))
                    put("status", c.getInt(6))
                    put("sub_id", c.getInt(7))
                })
            }
        }
        val root = JSONObject()
            .put("app", "io.github.vigneshvicky2.messages")
            .put("version", 1)
            .put("exported", System.currentTimeMillis())
            .put("sms", arr)
            .put("prefs", Prefs.get(ctx).exportJson())
        ctx.contentResolver.openOutputStream(dest, "wt")?.use { it.write(root.toString().toByteArray()) }
        return arr.length()
    }

    fun import(ctx: Context, src: Uri): Result {
        val text = ctx.contentResolver.openInputStream(src)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return Result(0, 0)
        val root = JSONObject(text)
        val existing = HashSet<String>()
        ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("address", "date", "body"), null, null, null)?.use { c ->
            while (c.moveToNext()) existing += "${c.getString(0)}|${c.getLong(1)}|${c.getString(2)?.hashCode()}"
        }
        val arr = root.optJSONArray("sms") ?: JSONArray()
        var added = 0; var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = "${o.getString("address")}|${o.getLong("date")}|${o.getString("body").hashCode()}"
            if (key in existing) { skipped++; continue }
            val v = ContentValues().apply {
                put("address", o.getString("address"))
                put("body", o.getString("body"))
                put("date", o.getLong("date"))
                put("date_sent", o.optLong("date_sent"))
                put("type", o.getInt("type"))
                put("read", o.optInt("read", 1))
                put("seen", 1)
                put("status", o.optInt("status", -1))
                put("sub_id", o.optInt("sub_id", -1))
            }
            if (runCatching { ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, v) }.getOrNull() != null) added++ else skipped++
        }
        root.optJSONObject("prefs")?.let { Prefs.get(ctx).importJson(it) }
        return Result(added, skipped)
    }
}

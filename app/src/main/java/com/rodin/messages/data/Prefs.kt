package com.rodin.messages.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class Palette(val label: String, val seed: Long) {
    DYNAMIC("Wallpaper", 0xFFAFC6FF),
    OCEAN("Ocean", 0xFF4F8DFF),
    ROSE("Rose", 0xFFE8557A),
    MINT("Mint", 0xFF2FBF8F),
    AMBER("Amber", 0xFFF2A43A),
    VIOLET("Violet", 0xFF8B6CFF),
}
enum class SwipeAction(val label: String) { ARCHIVE("Archive"), DELETE("Delete"), READ("Mark read"), PIN("Pin"), NONE("None") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.DYNAMIC,
    val defaultSubId: Int = -1, // -1 = ask every time
    val deliveryReports: Boolean = true,
    val swipeLeft: SwipeAction = SwipeAction.ARCHIVE,
    val swipeRight: SwipeAction = SwipeAction.READ,
    val otpCopyAction: Boolean = true,
    val otpAutoCopy: Boolean = true,
    val sendOnEnter: Boolean = false,
    val bubbles: Boolean = true,
    val textScale: Float = 1f,
)

/** Local-only metadata the telephony provider has no columns for. */
class Prefs private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("rodin_messages", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _version = MutableStateFlow(0)
    /** Bumps whenever local metadata changes, so lists can refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun bump() { _version.value++ }

    // ---- sets keyed by thread / message ----
    private fun set(key: String): MutableSet<String> = sp.getStringSet(key, emptySet())!!.toMutableSet()
    private fun toggle(key: String, item: String, on: Boolean) {
        val s = set(key); if (on) s.add(item) else s.remove(item)
        sp.edit().putStringSet(key, s).apply(); bump()
    }

    fun pinned(): Set<Long> = set("pinned").mapNotNull { it.toLongOrNull() }.toSet()
    fun setPinned(threadId: Long, on: Boolean) = toggle("pinned", threadId.toString(), on)

    fun archived(): Set<Long> = set("archived").mapNotNull { it.toLongOrNull() }.toSet()
    fun setArchived(threadId: Long, on: Boolean) = toggle("archived", threadId.toString(), on)

    fun muted(): Set<Long> = set("muted").mapNotNull { it.toLongOrNull() }.toSet()
    fun setMuted(threadId: Long, on: Boolean) = toggle("muted", threadId.toString(), on)

    fun starred(): Set<String> = set("starred")
    fun setStarred(key: String, on: Boolean) = toggle("starred", key, on)

    /** Per-thread manual category override (e.g. "not spam"). */
    fun categoryOverrides(): Map<Long, Category> {
        val o = JSONObject(sp.getString("cat_over", "{}")!!)
        return o.keys().asSequence().mapNotNull { k ->
            runCatching { k.toLong() to Category.valueOf(o.getString(k)) }.getOrNull()
        }.toMap()
    }
    fun setCategory(threadId: Long, c: Category?) {
        val o = JSONObject(sp.getString("cat_over", "{}")!!)
        if (c == null) o.remove(threadId.toString()) else o.put(threadId.toString(), c.name)
        sp.edit().putString("cat_over", o.toString()).apply(); bump()
    }

    fun reactions(): Map<String, String> {
        val o = JSONObject(sp.getString("reactions", "{}")!!)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
    fun setReaction(key: String, emoji: String?) {
        val o = JSONObject(sp.getString("reactions", "{}")!!)
        if (emoji == null) o.remove(key) else o.put(key, emoji)
        sp.edit().putString("reactions", o.toString()).apply(); bump()
    }

    fun draft(threadId: Long): String = sp.getString("draft_$threadId", "") ?: ""
    fun setDraft(threadId: Long, text: String) {
        if (text.isBlank()) sp.edit().remove("draft_$threadId").apply()
        else sp.edit().putString("draft_$threadId", text).apply()
    }

    // ---- scheduled ----
    fun scheduled(): List<ScheduledMessage> {
        val arr = JSONArray(sp.getString("scheduled", "[]")!!)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val addrs = o.getJSONArray("a")
            ScheduledMessage(
                id = o.getLong("id"),
                threadId = o.getLong("t"),
                addresses = (0 until addrs.length()).map { addrs.getString(it) },
                body = o.getString("b"),
                time = o.getLong("time"),
                subId = o.optInt("sub", -1),
            )
        }
    }
    private fun saveScheduled(list: List<ScheduledMessage>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().put("id", m.id).put("t", m.threadId).put("a", JSONArray(m.addresses))
                .put("b", m.body).put("time", m.time).put("sub", m.subId))
        }
        sp.edit().putString("scheduled", arr.toString()).apply(); bump()
    }
    fun addScheduled(m: ScheduledMessage) = saveScheduled(scheduled() + m)
    fun removeScheduled(id: Long) = saveScheduled(scheduled().filterNot { it.id == id })

    // ---- settings ----
    private fun loadSettings(): Settings = Settings(
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        palette = runCatching { Palette.valueOf(sp.getString("palette", "DYNAMIC")!!) }.getOrDefault(Palette.DYNAMIC),
        defaultSubId = sp.getInt("default_sub", -1),
        deliveryReports = sp.getBoolean("delivery", true),
        swipeLeft = runCatching { SwipeAction.valueOf(sp.getString("swipe_l", "ARCHIVE")!!) }.getOrDefault(SwipeAction.ARCHIVE),
        swipeRight = runCatching { SwipeAction.valueOf(sp.getString("swipe_r", "READ")!!) }.getOrDefault(SwipeAction.READ),
        otpCopyAction = sp.getBoolean("otp_copy", true),
        otpAutoCopy = sp.getBoolean("otp_auto", true),
        sendOnEnter = sp.getBoolean("send_enter", false),
        bubbles = sp.getBoolean("bubbles", true),
        textScale = sp.getFloat("text_scale", 1f),
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putString("palette", s.palette.name)
            .putInt("default_sub", s.defaultSubId)
            .putBoolean("delivery", s.deliveryReports)
            .putString("swipe_l", s.swipeLeft.name)
            .putString("swipe_r", s.swipeRight.name)
            .putBoolean("otp_copy", s.otpCopyAction)
            .putBoolean("otp_auto", s.otpAutoCopy)
            .putBoolean("send_enter", s.sendOnEnter)
            .putBoolean("bubbles", s.bubbles)
            .putFloat("text_scale", s.textScale)
            .apply()
        _settings.value = s
    }

    /** Full dump for backup. */
    fun exportJson(): JSONObject {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            when (v) {
                is Set<*> -> o.put(k, JSONArray(v.toList()))
                else -> o.put(k, v)
            }
        }
        return o
    }

    fun importJson(o: JSONObject) {
        val e = sp.edit()
        o.keys().forEach { k ->
            when (val v = o.get(k)) {
                is JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
            }
        }
        e.apply()
        _settings.value = loadSettings(); bump()
    }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext).also { inst = it }
        }
    }
}

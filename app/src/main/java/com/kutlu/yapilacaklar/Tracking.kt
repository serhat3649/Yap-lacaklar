package com.kutlu.yapilacaklar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

val PRAYERS = listOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı")
const val ALL_PRAYERS = 0b11111

data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val total: Int,
    val current: Int = 0,
    val started: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0L,
) {
    val done: Boolean get() = finishedAt > 0L
    val percent: Int get() = if (total <= 0) 0 else (current * 100 / total).coerceIn(0, 100)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("author", author); put("total", total)
        put("current", current); put("started", started); put("finishedAt", finishedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = Book(
            id = o.getString("id"),
            title = o.optString("title"),
            author = o.optString("author"),
            total = o.optInt("total", 1),
            current = o.optInt("current", 0),
            started = o.optLong("started", 0L),
            finishedAt = o.optLong("finishedAt", 0L),
        )
    }
}

/** Namaz ve kitap takibi verileri (telefonda saklanır). */
object TrackStore {
    private const val PREFS = "track"

    fun dayKey(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    /** Bugünden [offset] gün önceki/sonraki günün öğle saati (ms). */
    fun dayMs(offset: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, offset)
    }.timeInMillis

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadMap(ctx: Context, key: String): Map<String, Int> {
        val raw = prefs(ctx).getString(key, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val m = HashMap<String, Int>()
            o.keys().forEach { k -> m[k] = o.optInt(k, 0) }
            m
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveMap(ctx: Context, key: String, map: Map<String, Int>) {
        val o = JSONObject()
        map.forEach { (k, v) -> if (v != 0) o.put(k, v) }
        prefs(ctx).edit().putString(key, o.toString()).apply()
    }

    /** gün → 5 bitlik maske (bit 0 = Sabah … bit 4 = Yatsı) */
    fun loadPrayers(ctx: Context) = loadMap(ctx, "prayers")
    fun savePrayers(ctx: Context, m: Map<String, Int>) = saveMap(ctx, "prayers", m)

    /** gün → o gün okunan sayfa */
    fun loadReadLog(ctx: Context) = loadMap(ctx, "readlog")
    fun saveReadLog(ctx: Context, m: Map<String, Int>) = saveMap(ctx, "readlog", m)

    fun loadBooks(ctx: Context): List<Book> {
        val raw = prefs(ctx).getString("books", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            List(a.length()) { Book.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveBooks(ctx: Context, books: List<Book>) {
        val a = JSONArray()
        books.forEach { a.put(it.toJson()) }
        prefs(ctx).edit().putString("books", a.toString()).apply()
    }

    fun loadGoal(ctx: Context): Int = prefs(ctx).getInt("goal", 20)
    fun saveGoal(ctx: Context, g: Int) = prefs(ctx).edit().putInt("goal", g).apply()

    /**
     * Seri: bugünden geriye doğru koşulu sağlayan ardışık gün sayısı.
     * Bugün henüz sağlanmadıysa seri dünden sayılır (gün bitmeden bozulmuş sayılmaz).
     */
    fun streak(map: Map<String, Int>, ok: (Int) -> Boolean): Int {
        var offset = if (ok(map[dayKey(dayMs(0))] ?: 0)) 0 else -1
        var n = 0
        while (n < 3650 && ok(map[dayKey(dayMs(offset))] ?: 0)) {
            n++; offset--
        }
        return n
    }
}

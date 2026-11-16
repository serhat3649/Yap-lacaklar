package com.kutlu.yapilacaklar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val CATEGORIES = listOf("İş", "Proje", "Şantiye", "Teklif", "Kişisel")

/** 0 = Acil, 1 = Normal, 2 = Düşük */
val PRIORITY_LABELS = listOf("Acil", "Normal", "Düşük")

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val cat: String = "İş",
    val pri: Int = 1,
    /** Son tarih (epoch ms). 0 = tarih yok. */
    val due: Long = 0L,
    /** Kullanıcı saat seçti mi? Seçmediyse saat 09:00 kabul edilir. */
    val hasTime: Boolean = false,
    /** Son tarihte bildirim gönderilsin mi? */
    val remind: Boolean = false,
    val done: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val doneAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("note", note); put("cat", cat)
        put("pri", pri); put("due", due); put("hasTime", hasTime); put("remind", remind)
        put("done", done); put("created", created); put("doneAt", doneAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = Task(
            id = o.getString("id"),
            title = o.optString("title"),
            note = o.optString("note"),
            cat = o.optString("cat", "İş"),
            pri = o.optInt("pri", 1),
            due = o.optLong("due", 0L),
            hasTime = o.optBoolean("hasTime", false),
            remind = o.optBoolean("remind", false),
            done = o.optBoolean("done", false),
            created = o.optLong("created", 0L),
            doneAt = o.optLong("doneAt", 0L),
        )
    }
}

/** İşleri telefonda saklar (SharedPreferences içinde JSON). */
object TaskStore {
    private const val PREFS = "tasks"
    private const val KEY = "list"
    private val lock = Any()

    fun load(ctx: Context): List<Task> {
        synchronized(lock) {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                List(arr.length()) { Task.fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun save(ctx: Context, tasks: List<Task>) {
        synchronized(lock) {
            val arr = JSONArray()
            tasks.forEach { arr.put(it.toJson()) }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, arr.toString()).commit()
        }
    }

    fun update(ctx: Context, id: String, change: (Task) -> Task): Task? {
        synchronized(lock) {
            val list = load(ctx).toMutableList()
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) return null
            list[i] = change(list[i])
            save(ctx, list)
            return list[i]
        }
    }

    fun find(ctx: Context, id: String): Task? = load(ctx).firstOrNull { it.id == id }
}

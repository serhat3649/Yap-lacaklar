package com.kutlu.yapilacaklar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.UUID

val EXPENSE_CATS = listOf("Yakıt", "Yemek", "Market", "Ulaşım", "Fatura", "Şantiye", "Ev", "Sağlık", "Kişisel", "Diğer")

val EXPENSE_EMOJI = mapOf(
    "Yakıt" to "⛽", "Yemek" to "🍽", "Market" to "🛒", "Ulaşım" to "🚗", "Fatura" to "🧾",
    "Şantiye" to "🏗", "Ev" to "🏠", "Sağlık" to "💊", "Kişisel" to "👤", "Diğer" to "📦",
)

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val cat: String = "Diğer",
    val note: String = "",
    val time: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("amount", amount); put("cat", cat); put("note", note); put("time", time)
    }

    companion object {
        fun fromJson(o: JSONObject) = Expense(
            id = o.getString("id"),
            amount = o.optDouble("amount", 0.0),
            cat = o.optString("cat", "Diğer"),
            note = o.optString("note"),
            time = o.optLong("time", 0L),
        )
    }
}

object ExpenseStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("expenses", Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(ctx: Context): List<Expense> {
        synchronized(lock) {
            return try {
                val a = JSONArray(prefs(ctx).getString("list", "[]"))
                List(a.length()) { Expense.fromJson(a.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun save(ctx: Context, list: List<Expense>) {
        synchronized(lock) {
            val a = JSONArray()
            list.forEach { a.put(it.toJson()) }
            prefs(ctx).edit().putString("list", a.toString()).commit()
        }
    }

    /** Ayın başlangıcı ve bir sonraki ayın başlangıcı (ms). offset: 0 = bu ay, -1 = geçen ay */
    fun monthRange(offset: Int): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, offset)
        }
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return start to c.timeInMillis
    }

    fun dayRange(offset: Int): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, offset)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        return start to c.timeInMillis
    }

    fun sum(list: List<Expense>, range: Pair<Long, Long>): Double =
        list.filter { it.time >= range.first && it.time < range.second }.sumOf { it.amount }
}

private val TL_FORMAT = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.forLanguageTag("tr-TR")))

fun tl(v: Double): String = TL_FORMAT.format(v) + " ₺"

/** "1.500", "1500,50", "1.250,75", "12.5" gibi girişleri sayıya çevirir. */
fun parseAmount(s: String): Double? {
    var v = s.trim().replace(" ", "").replace("₺", "").replace("TL", "", ignoreCase = true)
    if (v.isEmpty()) return null
    if (v.contains(',')) {
        v = v.replace(".", "").replace(',', '.')
    } else if (v.contains('.')) {
        val after = v.substringAfterLast('.')
        if (after.length == 3 || v.count { it == '.' } > 1) v = v.replace(".", "")
    }
    return v.toDoubleOrNull()?.takeIf { it > 0 }
}

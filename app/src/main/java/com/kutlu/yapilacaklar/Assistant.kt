package com.kutlu.yapilacaklar

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TRX: Locale = Locale.forLanguageTag("tr-TR")

// =====================================================================
// Ayarlar
// =====================================================================

object AssistantSettings {
    const val MODEL_SMART = "claude-sonnet-5-5"
    const val MODEL_FAST = "claude-haiku-5-5"

    const val DEFAULT_ABOUT =
        "Makine mühendisiyim. Kutlu Elektromekanik adında kendi şirketim var; mimarlara mekanik " +
            "tesisat projesi (sıhhi tesisat, ısıtma, klima, havalandırma, yangın) hazırlıyoruz ve " +
            "müteahhitlik yapıyoruz. Kocaeli'de yaşıyorum. Kısa, net ve madde madde cevapları severim."

    private fun plain(ctx: Context) = ctx.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    private fun secure(ctx: Context): SharedPreferences = try {
        val mk = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "assistant_secure", mk, ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        plain(ctx)
    }

    fun apiKey(ctx: Context): String = secure(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, k: String) = secure(ctx).edit().putString("api_key", k.trim()).apply()

    fun model(ctx: Context): String = plain(ctx).getString("model", MODEL_FAST) ?: MODEL_FAST
    fun setModel(ctx: Context, m: String) = plain(ctx).edit().putString("model", m).apply()

    fun about(ctx: Context): String = plain(ctx).getString("about", DEFAULT_ABOUT) ?: DEFAULT_ABOUT
    fun setAbout(ctx: Context, a: String) = plain(ctx).edit().putString("about", a).apply()

    fun briefOn(ctx: Context): Boolean = plain(ctx).getBoolean("brief_on", true)
    fun briefHour(ctx: Context): Int = plain(ctx).getInt("brief_h", 7)
    fun briefMinute(ctx: Context): Int = plain(ctx).getInt("brief_m", 30)
    fun setBrief(ctx: Context, on: Boolean, h: Int, m: Int) =
        plain(ctx).edit().putBoolean("brief_on", on).putInt("brief_h", h).putInt("brief_m", m).apply()
}

// =====================================================================
// Sohbet geçmişi
// =====================================================================

data class ChatMsg(val role: String, val text: String, val time: Long = System.currentTimeMillis(), val error: Boolean = false)

object ChatStore {
    private const val MAX = 120
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("chat", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<ChatMsg> = try {
        val a = JSONArray(prefs(ctx).getString("list", "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            ChatMsg(o.getString("role"), o.getString("text"), o.optLong("time"), o.optBoolean("error"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(ctx: Context, list: List<ChatMsg>) {
        val a = JSONArray()
        list.takeLast(MAX).forEach {
            a.put(JSONObject().put("role", it.role).put("text", it.text).put("time", it.time).put("error", it.error))
        }
        prefs(ctx).edit().putString("list", a.toString()).apply()
    }
}

// =====================================================================
// Claude API
// =====================================================================

class AssistantException(msg: String) : Exception(msg)

object Assistant {

    private fun nowText(): String =
        SimpleDateFormat("d MMMM yyyy EEEE, HH:mm", TRX).format(Date())

    private fun systemPrompt(ctx: Context) = """
Adın Pusula; kullanıcının telefonundaki kişisel asistanısın. Uygulamada dört bölüm var: İşler (yapılacaklar ve hatırlatmalar), Namaz takibi (5 vakit), Kitap okuma takibi ve Harcamalar. Araçlarla bu verileri okuyup değiştirebilirsin.

Kullanıcı hakkında: ${AssistantSettings.about(ctx)}

Şu an: ${nowText()} (Türkiye saati).

Kurallar:
- Her zaman Türkçe, samimi, kısa ve net yaz. Gerekirse madde madde.
- Kullanıcı bir iş, hatırlatma, randevu veya "şunu yapmam lazım" dediğinde add_task ile ekle. "Yarın", "cuma", "akşam" gibi ifadeleri şu anki tarihe göre gerçek tarihe çevir. Saat söylenmezse sadece tarih ver (bildirim 09:00'da gelir). Tarih varsa hatırlatmayı aç.
- Öncelik söylenmezse "normal", kategori söylenmezse içeriğe göre seç (İş, Proje, Şantiye, Teklif, Kişisel).
- "Öğleyi kıldım", "bugün 30 sayfa okudum" gibi cümlelerde ilgili kaydı yap.
- "Mazota 1500 lira verdim", "öğle yemeği 350" gibi harcamaları add_expense ile kaydet; kategoriyi içerikten seç (${EXPENSE_CATS.joinToString()}). Tutar TL'dir. Harcama soruları için expense_summary kullan; tutarları 1.250,50 ₺ biçiminde yaz.
- İşi tamamladığını söylerse önce list_tasks ile bul, sonra update_task ile done=true yap. Emin değilsen sor.
- Brifing veya gün planı istenince önce get_overview çağır; planı saat aralıklarıyla, acil ve geciken işler önde olacak şekilde öner.
- Yaptığın değişikliği tek cümleyle teyit et (ör. "✅ Yarın 09:00 için eklendi: Murat'ı ara").
- Verileri uydurma; bilmediğin şeyi araçla kontrol et. Genel sorulara (teknik hesap, yönetmelik vb.) bilgin dahilinde cevap ver, emin olmadığın yerde belirt.
""".trim()

    // ---------------- araç tanımları ----------------

    private fun prop(type: String, desc: String, enum: List<String>? = null) = JSONObject().apply {
        put("type", type); put("description", desc)
        if (enum != null) put("enum", JSONArray(enum))
    }

    private fun tool(name: String, desc: String, props: JSONObject = JSONObject(), required: List<String> = emptyList()) =
        JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("input_schema", JSONObject().apply {
                put("type", "object")
                put("properties", props)
                if (required.isNotEmpty()) put("required", JSONArray(required))
            })
        }

    private val tools: JSONArray by lazy {
        JSONArray().apply {
            put(tool("get_overview", "Bugünün özeti: aktif/bugünkü/geciken işler, bugün ve dün namaz durumu, okuma hedefi ve serisi, okunan kitaplar."))
            put(tool("list_tasks", "İşleri listeler.", JSONObject().apply {
                put("filter", prop("string", "Hangi işler", listOf("aktif", "bugun", "geciken", "tamamlanan", "tumu")))
                put("category", prop("string", "İsteğe bağlı kategori filtresi", CATEGORIES))
            }))
            put(tool("add_task", "Yeni iş/hatırlatma ekler.", JSONObject().apply {
                put("title", prop("string", "İşin kısa başlığı"))
                put("note", prop("string", "İsteğe bağlı not"))
                put("category", prop("string", "Kategori", CATEGORIES))
                put("priority", prop("string", "Öncelik", listOf("acil", "normal", "dusuk")))
                put("due", prop("string", "Son tarih: 'YYYY-MM-DDTHH:mm' (saatli) veya 'YYYY-MM-DD' (saatsiz). Yoksa boş bırak."))
                put("remind", prop("boolean", "Son tarihte bildirim gönderilsin mi (varsayılan: tarih varsa true)"))
            }, listOf("title")))
            put(tool("update_task", "Var olan işi değiştirir veya tamamlar. Sadece değişecek alanları gönder.", JSONObject().apply {
                put("id", prop("string", "İşin id'si (list_tasks'tan)"))
                put("title", prop("string", "Yeni başlık"))
                put("note", prop("string", "Yeni not"))
                put("category", prop("string", "Kategori", CATEGORIES))
                put("priority", prop("string", "Öncelik", listOf("acil", "normal", "dusuk")))
                put("due", prop("string", "Yeni son tarih ('YYYY-MM-DDTHH:mm' veya 'YYYY-MM-DD'); tarihi kaldırmak için boş string"))
                put("remind", prop("boolean", "Hatırlatma açık/kapalı"))
                put("done", prop("boolean", "Tamamlandı mı"))
            }, listOf("id")))
            put(tool("delete_task", "İşi siler.", JSONObject().apply {
                put("id", prop("string", "İşin id'si"))
            }, listOf("id")))
            put(tool("set_prayers", "Namaz vakitlerini kılındı/kılınmadı olarak işaretler.", JSONObject().apply {
                put("date", prop("string", "Gün 'YYYY-MM-DD' (varsayılan bugün)"))
                put("prayers", JSONObject().apply {
                    put("type", "array"); put("description", "Vakitler")
                    put("items", JSONObject().put("type", "string").put("enum", JSONArray(PRAYERS)))
                })
                put("done", prop("boolean", "true = kılındı, false = kılınmadı (varsayılan true)"))
            }, listOf("prayers")))
            put(tool("get_prayers", "Son günlerin namaz durumunu verir.", JSONObject().apply {
                put("days", prop("integer", "Kaç gün geriye (varsayılan 7, en çok 60)"))
            }))
            put(tool("add_expense", "Harcama kaydeder (TL).", JSONObject().apply {
                put("amount", prop("number", "Tutar (TL)"))
                put("category", prop("string", "Kategori", EXPENSE_CATS))
                put("note", prop("string", "Kısa açıklama (ör. 'Shell mazot', 'öğle yemeği')"))
                put("date", prop("string", "Ne zaman: 'YYYY-MM-DDTHH:mm' veya 'YYYY-MM-DD'; boşsa şimdi"))
            }, listOf("amount", "category")))
            put(tool("expense_summary", "Bir ayın harcama özeti: toplam, günlük ortalama, kategori dağılımı, son kayıtlar (id'leriyle).", JSONObject().apply {
                put("month", prop("string", "Ay 'YYYY-MM' (varsayılan bu ay)"))
            }))
            put(tool("delete_expense", "Harcama kaydını siler.", JSONObject().apply {
                put("id", prop("string", "Harcama id'si (expense_summary'den)"))
            }, listOf("id")))
            put(tool("list_books", "Okunan ve biten kitapları listeler."))
            put(tool("add_book", "Okuma listesine kitap ekler.", JSONObject().apply {
                put("title", prop("string", "Kitap adı"))
                put("author", prop("string", "Yazar"))
                put("total_pages", prop("integer", "Toplam sayfa"))
                put("current_page", prop("integer", "Şu anki sayfa (varsayılan 0)"))
            }, listOf("title", "total_pages")))
            put(tool("log_reading", "Bugün okunan sayfa sayısını kaydeder. book_id verilirse kitabın sayfası da ilerler.", JSONObject().apply {
                put("pages", prop("integer", "Okunan sayfa sayısı"))
                put("book_id", prop("string", "İsteğe bağlı kitap id'si"))
            }, listOf("pages")))
            put(tool("set_book_page", "Kitapta gelinen sayfayı ayarlar; aradaki fark bugünkü okumaya eklenir.", JSONObject().apply {
                put("book_id", prop("string", "Kitap id'si"))
                put("page", prop("integer", "Gelinen sayfa"))
            }, listOf("book_id", "page")))
        }
    }

    // ---------------- HTTP ----------------

    private fun post(key: String, body: JSONObject): JSONObject {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.doOutput = true
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(text).getJSONObject("error").optString("message") } catch (e: Exception) { text.take(200) }
                throw AssistantException(
                    when {
                        code == 401 -> "API anahtarı geçersiz. Ayarlardan kontrol et."
                        code == 403 -> "Bu anahtarın yetkisi yok: $msg"
                        code == 429 -> "Kullanım sınırına takıldı, biraz sonra tekrar dene."
                        code == 529 || code == 503 -> "Sunucu şu an yoğun, biraz sonra tekrar dene."
                        msg.contains("credit", ignoreCase = true) -> "API bakiyen yetersiz. console.anthropic.com'dan bakiye yükle."
                        else -> "Hata ($code): $msg"
                    }
                )
            }
            return JSONObject(text)
        } catch (e: IOException) {
            throw AssistantException("İnternet bağlantısı kurulamadı.")
        } finally {
            c.disconnect()
        }
    }

    /**
     * Kullanıcının mesajını gönderir, araç çağrılarını telefonda çalıştırır ve son cevabı döndürür.
     * Arka planda (IO) çağrılmalı. [changed] veri değiştiyse true olur.
     */
    fun ask(ctx: Context, history: List<ChatMsg>, userText: String): Pair<String, Boolean> {
        val key = AssistantSettings.apiKey(ctx)
        if (key.isBlank()) throw AssistantException("Önce ayarlardan API anahtarını gir.")

        // Geçmişi (son 20 mesaj) sırayla birleştir: user/assistant dönüşümlü olmalı
        val msgs = JSONArray()
        var lastRole: String? = null
        var buf = StringBuilder()
        fun flush() {
            if (lastRole != null && buf.isNotBlank()) {
                msgs.put(JSONObject().put("role", lastRole).put("content", buf.toString()))
            }
            buf = StringBuilder()
        }
        val recent = history.filter { !it.error }.takeLast(20).dropWhile { it.role != "user" }
        for (m in recent + ChatMsg("user", userText)) {
            if (m.role != lastRole) { flush(); lastRole = m.role }
            if (buf.isNotEmpty()) buf.append("\n\n")
            buf.append(m.text)
        }
        flush()

        var changed = false
        repeat(8) {
            val body = JSONObject().apply {
                put("model", AssistantSettings.model(ctx))
                put("max_tokens", 4096)
                put("system", systemPrompt(ctx))
                put("tools", tools)
                put("messages", msgs)
            }
            val resp = post(key, body)
            val content = resp.getJSONArray("content")
            msgs.put(JSONObject().put("role", "assistant").put("content", content))

            if (resp.optString("stop_reason") == "tool_use") {
                val results = JSONArray()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") != "tool_use") continue
                    val (out, mod) = try {
                        Tools.run(ctx, b.getString("name"), b.optJSONObject("input") ?: JSONObject())
                    } catch (e: Exception) {
                        ("Hata: ${e.message}") to false
                    }
                    if (mod) changed = true
                    results.put(JSONObject().put("type", "tool_result").put("tool_use_id", b.getString("id")).put("content", out))
                }
                msgs.put(JSONObject().put("role", "user").put("content", results))
            } else {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.optString("text"))
                }
                return sb.toString().trim().ifEmpty { "Tamam." } to changed
            }
        }
        return "İşlem çok uzun sürdü, isteğini biraz daha basit yazar mısın?" to changed
    }
}

// =====================================================================
// Araçların telefondaki karşılıkları
// =====================================================================

object Tools {
    private val PRI_KEYS = listOf("acil", "normal", "dusuk")

    private fun fmtDue(t: Task): String {
        if (t.due <= 0L) return ""
        val p = if (t.hasTime) "yyyy-MM-dd'T'HH:mm" else "yyyy-MM-dd"
        return SimpleDateFormat(p, Locale.US).format(Date(t.due))
    }

    private fun taskJson(t: Task) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("category", t.cat)
        put("priority", PRI_KEYS.getOrElse(t.pri) { "normal" })
        put("due", fmtDue(t)); put("remind", t.remind); put("done", t.done)
        if (t.note.isNotBlank()) put("note", t.note)
    }

    /** "YYYY-MM-DDTHH:mm" veya "YYYY-MM-DD" → (ms, saatli mi) */
    private fun parseDue(s: String): Pair<Long, Boolean>? {
        val v = s.trim()
        if (v.isEmpty()) return null
        return try {
            if (v.contains("T")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(v.take(16))!!.time to true
            } else {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(v.take(10))!!
                Calendar.getInstance().apply { time = d; set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis to false
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Tarih biçimi anlaşılamadı: $s")
        }
    }

    private fun startOfTomorrow(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    private fun startOfToday(): Long = startOfTomorrow() - 86_400_000L

    private fun prayerText(mask: Int): String {
        val done = PRAYERS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
        val missing = PRAYERS.filterIndexed { i, _ -> mask and (1 shl i) == 0 }
        return "kılınan: ${done.joinToString().ifEmpty { "yok" }}; eksik: ${missing.joinToString().ifEmpty { "yok" }}"
    }

    fun run(ctx: Context, name: String, input: JSONObject): Pair<String, Boolean> = when (name) {
        "get_overview" -> overview(ctx) to false

        "list_tasks" -> {
            val f = input.optString("filter", "aktif")
            val cat = input.optString("category", "")
            val tom = startOfTomorrow(); val today = startOfToday()
            val list = TaskStore.load(ctx).filter { t ->
                (cat.isEmpty() || t.cat == cat) && when (f) {
                    "bugun" -> !t.done && t.due in 1 until tom
                    "geciken" -> !t.done && t.due in 1 until today
                    "tamamlanan" -> t.done
                    "tumu" -> true
                    else -> !t.done
                }
            }.sortedWith(compareBy<Task> { it.pri }.thenBy { if (it.due == 0L) Long.MAX_VALUE else it.due })
            val a = JSONArray(); list.take(60).forEach { a.put(taskJson(it)) }
            a.toString() to false
        }

        "add_task" -> {
            val due = parseDue(input.optString("due", ""))
            val t = Task(
                title = input.getString("title").trim().take(300),
                note = input.optString("note", "").trim(),
                cat = input.optString("category", "İş").takeIf { it in CATEGORIES } ?: "İş",
                pri = PRI_KEYS.indexOf(input.optString("priority", "normal")).takeIf { it >= 0 } ?: 1,
                due = due?.first ?: 0L,
                hasTime = due?.second ?: false,
                remind = due != null && input.optBoolean("remind", true),
            )
            TaskStore.save(ctx, TaskStore.load(ctx) + t)
            Reminders.schedule(ctx, t)
            "Eklendi: ${taskJson(t)}" to true
        }

        "update_task" -> {
            val id = input.getString("id")
            val updated = TaskStore.update(ctx, id) { old ->
                var t = old
                if (input.has("title")) t = t.copy(title = input.getString("title").trim())
                if (input.has("note")) t = t.copy(note = input.getString("note").trim())
                if (input.has("category")) input.getString("category").takeIf { it in CATEGORIES }?.let { t = t.copy(cat = it) }
                if (input.has("priority")) PRI_KEYS.indexOf(input.getString("priority")).takeIf { it >= 0 }?.let { t = t.copy(pri = it) }
                if (input.has("due")) {
                    val d = parseDue(input.getString("due"))
                    t = if (d == null) t.copy(due = 0L, hasTime = false, remind = false)
                    else t.copy(due = d.first, hasTime = d.second, remind = if (input.has("remind")) t.remind else true)
                }
                if (input.has("remind")) t = t.copy(remind = input.getBoolean("remind") && t.due > 0L)
                if (input.has("done")) {
                    val dn = input.getBoolean("done")
                    t = t.copy(done = dn, doneAt = if (dn) System.currentTimeMillis() else 0L)
                }
                t
            } ?: throw IllegalArgumentException("Bu id ile iş bulunamadı: $id")
            if (updated.done) Reminders.cancelAll(ctx, updated.id) else Reminders.schedule(ctx, updated)
            "Güncellendi: ${taskJson(updated)}" to true
        }

        "delete_task" -> {
            val id = input.getString("id")
            val list = TaskStore.load(ctx)
            val t = list.firstOrNull { it.id == id } ?: throw IllegalArgumentException("İş bulunamadı: $id")
            TaskStore.save(ctx, list.filterNot { it.id == id })
            Reminders.cancelAll(ctx, id)
            "Silindi: ${t.title}" to true
        }

        "set_prayers" -> {
            val date = input.optString("date", "").ifBlank { TrackStore.dayKey() }
            val names = input.optJSONArray("prayers") ?: JSONArray()
            val done = input.optBoolean("done", true)
            var bits = 0
            for (i in 0 until names.length()) {
                val idx = PRAYERS.indexOf(names.getString(i))
                if (idx >= 0) bits = bits or (1 shl idx)
            }
            val map = TrackStore.loadPrayers(ctx)
            val cur = map[date] ?: 0
            val nm = if (done) cur or bits else cur and bits.inv()
            TrackStore.savePrayers(ctx, map + (date to nm))
            "$date → ${prayerText(nm)}" to true
        }

        "get_prayers" -> {
            val days = input.optInt("days", 7).coerceIn(1, 60)
            val map = TrackStore.loadPrayers(ctx)
            val sb = StringBuilder()
            for (d in 0 until days) {
                val k = TrackStore.dayKey(TrackStore.dayMs(-d))
                sb.append(k).append(": ").append(prayerText(map[k] ?: 0)).append('\n')
            }
            sb.append("5 vakit tam seri: ${TrackStore.streak(map) { it == ALL_PRAYERS }} gün")
            sb.toString() to false
        }

        "list_books" -> {
            val a = JSONArray()
            TrackStore.loadBooks(ctx).forEach { b ->
                a.put(JSONObject().apply {
                    put("id", b.id); put("title", b.title); put("author", b.author)
                    put("total_pages", b.total); put("current_page", b.current); put("done", b.done)
                })
            }
            a.toString() to false
        }

        "add_book" -> {
            val total = input.getInt("total_pages").coerceAtLeast(1)
            val cur = input.optInt("current_page", 0).coerceIn(0, total)
            val b = Book(
                title = input.getString("title").trim(), author = input.optString("author", "").trim(),
                total = total, current = cur, finishedAt = if (cur >= total) System.currentTimeMillis() else 0L,
            )
            TrackStore.saveBooks(ctx, TrackStore.loadBooks(ctx) + b)
            "Kitap eklendi: ${b.title} (id ${b.id})" to true
        }

        "log_reading" -> {
            val pages = input.getInt("pages")
            val bookId = input.optString("book_id", "")
            var bookNote = ""
            if (bookId.isNotBlank()) {
                val books = TrackStore.loadBooks(ctx).toMutableList()
                val i = books.indexOfFirst { it.id == bookId }
                if (i >= 0) {
                    val b = books[i]
                    val np = (b.current + pages).coerceIn(0, b.total)
                    books[i] = b.copy(current = np, finishedAt = if (np >= b.total) System.currentTimeMillis() else 0L)
                    TrackStore.saveBooks(ctx, books)
                    bookNote = " · ${b.title}: sayfa $np/${b.total}"
                }
            }
            val k = TrackStore.dayKey()
            val log = TrackStore.loadReadLog(ctx)
            val total = (log[k] ?: 0) + pages
            TrackStore.saveReadLog(ctx, log + (k to total))
            "Bugün toplam $total sayfa (hedef ${TrackStore.loadGoal(ctx)})$bookNote" to true
        }

        "set_book_page" -> {
            val id = input.getString("book_id")
            val page = input.getInt("page")
            val books = TrackStore.loadBooks(ctx).toMutableList()
            val i = books.indexOfFirst { it.id == id }
            if (i < 0) throw IllegalArgumentException("Kitap bulunamadı: $id")
            val b = books[i]
            val np = page.coerceIn(0, b.total)
            books[i] = b.copy(current = np, finishedAt = if (np >= b.total) System.currentTimeMillis() else 0L)
            TrackStore.saveBooks(ctx, books)
            val delta = np - b.current
            if (delta > 0) {
                val k = TrackStore.dayKey()
                val log = TrackStore.loadReadLog(ctx)
                TrackStore.saveReadLog(ctx, log + (k to ((log[k] ?: 0) + delta)))
            }
            "${b.title}: sayfa $np/${b.total}" to true
        }

        "add_expense" -> {
            val amount = input.getDouble("amount")
            if (amount <= 0) throw IllegalArgumentException("Tutar sıfırdan büyük olmalı")
            val whenMs = parseDue(input.optString("date", ""))?.let { (ms, timed) ->
                if (timed) ms else {
                    // sadece tarih verildiyse: bugünse şimdiki saat, değilse öğlen
                    val now = Calendar.getInstance()
                    val c = Calendar.getInstance().apply { timeInMillis = ms }
                    if (c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR))
                        System.currentTimeMillis()
                    else c.apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
                }
            } ?: System.currentTimeMillis()
            val e = Expense(
                amount = amount,
                cat = input.optString("category", "Diğer").takeIf { it in EXPENSE_CATS } ?: "Diğer",
                note = input.optString("note", "").trim().take(200),
                time = whenMs,
            )
            val list = ExpenseStore.load(ctx) + e
            ExpenseStore.save(ctx, list)
            "Kaydedildi: ${tl(e.amount)} ${e.cat} (id ${e.id}). Bugün toplam ${tl(ExpenseStore.sum(list, ExpenseStore.dayRange(0)))}, bu ay ${tl(ExpenseStore.sum(list, ExpenseStore.monthRange(0)))}" to true
        }

        "expense_summary" -> {
            val m = input.optString("month", "")
            var offset = 0
            if (m.matches(Regex("\\d{4}-\\d{2}"))) {
                val now = Calendar.getInstance()
                offset = (m.take(4).toInt() - now.get(Calendar.YEAR)) * 12 + (m.substring(5, 7).toInt() - 1 - now.get(Calendar.MONTH))
            }
            val all = ExpenseStore.load(ctx)
            val r = ExpenseStore.monthRange(offset)
            val inMonth = all.filter { it.time >= r.first && it.time < r.second }.sortedByDescending { it.time }
            val total = inMonth.sumOf { it.amount }
            val days = if (offset == 0) Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                else Calendar.getInstance().apply { timeInMillis = r.first }.getActualMaximum(Calendar.DAY_OF_MONTH)
            JSONObject().apply {
                put("ay", SimpleDateFormat("yyyy-MM", Locale.US).format(Date(r.first)))
                put("toplam", tl(total))
                put("gunluk_ortalama", tl(if (days > 0) total / days else 0.0))
                put("bugun", tl(ExpenseStore.sum(all, ExpenseStore.dayRange(0))))
                put("gecen_ay_toplam", tl(ExpenseStore.sum(all, ExpenseStore.monthRange(offset - 1))))
                put("kategoriler", JSONObject().apply {
                    inMonth.groupBy { it.cat }.mapValues { (_, v) -> v.sumOf { it.amount } }
                        .toList().sortedByDescending { it.second }.forEach { (c, v) -> put(c, tl(v)) }
                })
                put("kayitlar", JSONArray().apply {
                    inMonth.take(40).forEach {
                        put(JSONObject().put("id", it.id).put("tutar", tl(it.amount)).put("kategori", it.cat)
                            .put("not", it.note).put("zaman", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it.time))))
                    }
                })
            }.toString() to false
        }

        "delete_expense" -> {
            val id = input.getString("id")
            val list = ExpenseStore.load(ctx)
            val e = list.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Harcama bulunamadı: $id")
            ExpenseStore.save(ctx, list.filterNot { it.id == id })
            "Silindi: ${tl(e.amount)} ${e.cat}" to true
        }

        else -> "Bilinmeyen araç: $name" to false
    }

    fun overview(ctx: Context): String {
        val tasks = TaskStore.load(ctx)
        val tom = startOfTomorrow(); val today = startOfToday()
        val active = tasks.filter { !it.done }
        val todays = active.filter { it.due in today until tom }
        val late = active.filter { it.due in 1 until today }
        val noDate = active.filter { it.due == 0L }
        val upcoming = active.filter { it.due >= tom }.sortedBy { it.due }.take(10)
        val prayers = TrackStore.loadPrayers(ctx)
        val log = TrackStore.loadReadLog(ctx)
        val books = TrackStore.loadBooks(ctx).filter { !it.done }

        fun arr(l: List<Task>) = JSONArray().apply { l.forEach { put(taskJson(it)) } }
        return JSONObject().apply {
            put("simdi", SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", TRX).format(Date()))
            put("bugunku_isler", arr(todays))
            put("geciken_isler", arr(late))
            put("tarihsiz_aktif_isler", arr(noDate.take(20)))
            put("yaklasan_isler", arr(upcoming))
            put("aktif_is_sayisi", active.size)
            put("namaz_bugun", prayerText(prayers[TrackStore.dayKey()] ?: 0))
            put("namaz_dun", prayerText(prayers[TrackStore.dayKey(TrackStore.dayMs(-1))] ?: 0))
            put("namaz_seri_gun", TrackStore.streak(prayers) { it == ALL_PRAYERS })
            put("okuma_bugun_sayfa", log[TrackStore.dayKey()] ?: 0)
            put("okuma_hedef_sayfa", TrackStore.loadGoal(ctx))
            put("okuma_seri_gun", TrackStore.streak(log) { it > 0 })
            val ex = ExpenseStore.load(ctx)
            put("harcama_bugun", tl(ExpenseStore.sum(ex, ExpenseStore.dayRange(0))))
            put("harcama_dun", tl(ExpenseStore.sum(ex, ExpenseStore.dayRange(-1))))
            put("harcama_bu_ay", tl(ExpenseStore.sum(ex, ExpenseStore.monthRange(0))))
            put("okunan_kitaplar", JSONArray().apply {
                books.forEach { put("${it.title} (${it.current}/${it.total}, id ${it.id})") }
            })
        }.toString()
    }
}

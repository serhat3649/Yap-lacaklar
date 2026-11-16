@file:OptIn(ExperimentalMaterial3Api::class)

package com.kutlu.yapilacaklar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TRL: Locale = Locale.forLanguageTag("tr-TR")
private val OK_GREEN = Color(0xFF2F9E6A)
private val DEL_RED = Color(0xFFD64545)

@Composable
private fun dimColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

@Composable
private fun ScreenTitle(title: String, sub: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 13.sp, color = dimColor())
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

private fun dayLabel(offset: Int): String = when (offset) {
    0 -> "Bugün"
    -1 -> "Dün"
    else -> SimpleDateFormat("d MMMM EEEE", TRL).format(Date(TrackStore.dayMs(offset)))
}

private fun shortDay(offset: Int): String =
    SimpleDateFormat("EEE", TRL).format(Date(TrackStore.dayMs(offset)))

// =====================================================================
// NAMAZ
// =====================================================================

@Composable
fun PrayerScreen() {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(TrackStore.loadPrayers(ctx)) }
    var offset by remember { mutableIntStateOf(0) }

    val key = TrackStore.dayKey(TrackStore.dayMs(offset))
    val mask = data[key] ?: 0

    fun toggle(i: Int) {
        val nm = mask xor (1 shl i)
        data = data + (key to nm)
        TrackStore.savePrayers(ctx, data)
    }

    val streak = TrackStore.streak(data) { it == ALL_PRAYERS }

    // Bu ay: kılınan vakit / (geçen gün × 5)
    val cal = Calendar.getInstance()
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    var monthCount = 0
    for (d in 0 until dayOfMonth) {
        monthCount += Integer.bitCount(data[TrackStore.dayKey(TrackStore.dayMs(-d))] ?: 0)
    }
    val monthPct = monthCount * 100 / (dayOfMonth * 5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Namaz Takibi", SimpleDateFormat("d MMMM EEEE", TRL).format(Date())) }

        // Gün seçici
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset-- }) { Text("‹", fontSize = 26.sp) }
                Text(
                    dayLabel(offset), modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                    Text("›", fontSize = 26.sp)
                }
            }
        }

        // 5 vakit
        items(PRAYERS.size, key = { "p$it" }) { i ->
            val on = mask and (1 shl i) != 0
            Card(
                onClick = { toggle(i) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (on) OK_GREEN.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, if (on) OK_GREEN else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).background(
                            if (on) OK_GREEN else MaterialTheme.colorScheme.surfaceVariant, CircleShape
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(PRAYERS[i], fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(if (on) "Kılındı" else "", fontSize = 13.sp, color = OK_GREEN)
                }
            }
        }

        // Özet
        item {
            SectionCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${Integer.bitCount(mask)} / 5", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (offset == 0) "bugün" else "bu gün", fontSize = 12.sp, color = dimColor())
                    }
                    Column(Modifier.weight(1f)) {
                        Text("🔥 $streak gün", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("5 vakit tam seri", fontSize = 12.sp, color = dimColor())
                    }
                    Column(Modifier.weight(1f)) {
                        Text("%$monthPct", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("bu ay", fontSize = 12.sp, color = dimColor())
                    }
                }
            }
        }

        // Son 7 gün
        item {
            SectionCard {
                Text("Son 7 gün", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Spacer(Modifier.width(36.dp))
                    PRAYERS.forEach {
                        Text(it.take(3), fontSize = 11.sp, color = dimColor(),
                            modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                    }
                }
                for (d in -6..0) {
                    val m = data[TrackStore.dayKey(TrackStore.dayMs(d))] ?: 0
                    Row(
                        Modifier.fillMaxWidth().clickable { offset = d }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(shortDay(d), fontSize = 12.sp, modifier = Modifier.width(36.dp),
                            fontWeight = if (d == offset) FontWeight.Bold else FontWeight.Normal)
                        for (i in 0 until 5) {
                            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.size(18.dp).background(
                                        if (m and (1 shl i) != 0) OK_GREEN else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(5.dp)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// =====================================================================
// KİTAP
// =====================================================================

@Composable
fun BookScreen() {
    val ctx = LocalContext.current
    var books by remember { mutableStateOf(TrackStore.loadBooks(ctx)) }
    var log by remember { mutableStateOf(TrackStore.loadReadLog(ctx)) }
    var goal by remember { mutableIntStateOf(TrackStore.loadGoal(ctx)) }

    var editing by remember { mutableStateOf<Book?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var progressFor by remember { mutableStateOf<Book?>(null) }
    var showPages by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }

    val todayKey = TrackStore.dayKey()
    val todayPages = log[todayKey] ?: 0

    fun addPages(n: Int) {
        if (n <= 0) return
        val k = TrackStore.dayKey()
        log = log + (k to ((log[k] ?: 0) + n))
        TrackStore.saveReadLog(ctx, log)
    }

    fun saveBook(b: Book) {
        val list = books.toMutableList()
        val i = list.indexOfFirst { it.id == b.id }
        if (i >= 0) list[i] = b else list.add(b)
        books = list
        TrackStore.saveBooks(ctx, books)
    }

    fun deleteBook(b: Book) {
        books = books.filterNot { it.id == b.id }
        TrackStore.saveBooks(ctx, books)
    }

    val streak = TrackStore.streak(log) { it > 0 }
    val week = (-6..0).map { log[TrackStore.dayKey(TrackStore.dayMs(it))] ?: 0 }
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val finishedThisYear = books.count {
        it.done && Calendar.getInstance().apply { timeInMillis = it.finishedAt }.get(Calendar.YEAR) == year
    }
    val reading = books.filter { !it.done }.sortedByDescending { it.started }
    val finished = books.filter { it.done }.sortedByDescending { it.finishedAt }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Kitap ekle") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenTitle("Kitap Okuma", "Bu yıl biten: $finishedThisYear kitap") }

            // Bugün özeti
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Bugün", fontSize = 12.sp, color = dimColor())
                            Text("$todayPages / $goal sayfa", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("🔥 $streak gün", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("okuma serisi", fontSize = 12.sp, color = dimColor())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (goal <= 0) 0f else (todayPages.toFloat() / goal).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = OK_GREEN,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showPages = true }) { Text("+ Sayfa gir") }
                        TextButton(onClick = { showGoal = true }) { Text("Hedef: $goal sayfa") }
                    }
                }
            }

            // Son 7 gün grafiği
            item {
                SectionCard {
                    Row {
                        Text("Son 7 gün", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${week.sum()} sayfa", fontSize = 13.sp, color = dimColor())
                    }
                    Spacer(Modifier.height(10.dp))
                    val maxV = maxOf(week.maxOrNull() ?: 0, goal, 1)
                    Row(
                        Modifier.fillMaxWidth().height(90.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        week.forEachIndexed { idx, v ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                if (v > 0) Text("$v", fontSize = 10.sp, color = dimColor())
                                Box(
                                    Modifier.width(18.dp)
                                        .height(maxOf(60f * v / maxV, 3f).dp)
                                        .background(
                                            if (v >= goal) OK_GREEN else if (v > 0) OK_GREEN.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                                Text(shortDay(idx - 6), fontSize = 10.sp, color = dimColor())
                            }
                        }
                    }
                }
            }

            item {
                Text("OKUDUKLARIM", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = dimColor(), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
            if (reading.isEmpty()) {
                item {
                    Text("Okuduğun kitabı \"Kitap ekle\" ile ekle.", color = dimColor(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center)
                }
            }
            items(reading, key = { it.id }) { b ->
                BookRow(b, onClick = { progressFor = b })
            }

            if (finished.isNotEmpty()) {
                item {
                    Text("BİTENLER (${finished.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = dimColor(), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                items(finished, key = { it.id }) { b ->
                    BookRow(b, onClick = { editing = b; showEditor = true })
                }
            }
        }
    }

    // ---- pencereler ----
    if (showEditor) {
        BookEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { saveBook(it); showEditor = false },
            onDelete = { deleteBook(it); showEditor = false },
        )
    }

    progressFor?.let { b ->
        ProgressDialog(
            book = b,
            onDismiss = { progressFor = null },
            onEdit = { progressFor = null; editing = b; showEditor = true },
            onSave = { newPage ->
                val p = newPage.coerceIn(0, b.total)
                addPages(p - b.current)
                saveBook(b.copy(current = p, finishedAt = if (p >= b.total) System.currentTimeMillis() else 0L))
                progressFor = null
            },
        )
    }

    if (showPages) {
        NumberDialog(
            title = "Bugün kaç sayfa okudun?",
            initial = "",
            onDismiss = { showPages = false },
            onSave = { addPages(it); showPages = false },
        )
    }

    if (showGoal) {
        NumberDialog(
            title = "Günlük hedef (sayfa)",
            initial = goal.toString(),
            onDismiss = { showGoal = false },
            onSave = { if (it > 0) { goal = it; TrackStore.saveGoal(ctx, it) }; showGoal = false },
        )
    }
}

@Composable
private fun BookRow(b: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (b.author.isNotBlank()) Text(b.author, fontSize = 13.sp, color = dimColor())
                }
                Text("%${b.percent}", fontWeight = FontWeight.Bold,
                    color = if (b.done) OK_GREEN else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { b.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = if (b.done) OK_GREEN else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (b.done) "Bitti · " + SimpleDateFormat("d MMM yyyy", TRL).format(Date(b.finishedAt))
                else "Sayfa ${b.current} / ${b.total} · kalan ${b.total - b.current}",
                fontSize = 12.sp, color = dimColor(),
            )
        }
    }
}

private fun digits(s: String) = s.filter { it.isDigit() }.take(5)

@Composable
private fun NumberDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v, onValueChange = { v = digits(it) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = v.isNotEmpty(), onClick = { onSave(v.toIntOrNull() ?: 0) }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ProgressDialog(book: Book, onDismiss: () -> Unit, onEdit: () -> Unit, onSave: (Int) -> Unit) {
    var v by remember { mutableStateOf(book.current.toString()) }
    fun bump(n: Int) {
        v = ((v.toIntOrNull() ?: book.current) + n).coerceIn(0, book.total).toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Şu an kaçıncı sayfadasın? (toplam ${book.total})", fontSize = 14.sp)
                OutlinedTextField(
                    value = v, onValueChange = { v = digits(it) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 20, 50).forEach { n ->
                        AssistChip(onClick = { bump(n) }, label = { Text("+$n") })
                    }
                }
                Text("Okuduğun sayfalar bugünkü hedefine eklenir.", fontSize = 12.sp, color = dimColor())
            }
        },
        confirmButton = {
            TextButton(enabled = v.isNotEmpty(), onClick = { onSave(v.toIntOrNull() ?: book.current) }) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Düzenle") }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

@Composable
private fun BookEditor(initial: Book?, onDismiss: () -> Unit, onSave: (Book) -> Unit, onDelete: (Book) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var author by remember { mutableStateOf(initial?.author ?: "") }
    var total by remember { mutableStateOf(initial?.total?.toString() ?: "") }
    var current by remember { mutableStateOf(initial?.current?.toString() ?: "0") }
    val totalN = total.toIntOrNull() ?: 0
    val valid = title.isNotBlank() && totalN > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kitap ekle" else "Kitabı düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(200) }, singleLine = true,
                    label = { Text("Kitap adı") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = author, onValueChange = { author = it.take(200) }, singleLine = true,
                    label = { Text("Yazar (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = total, onValueChange = { total = digits(it) }, singleLine = true,
                        label = { Text("Toplam sayfa") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = current, onValueChange = { current = digits(it) }, singleLine = true,
                        label = { Text("Şu anki sayfa") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val cur = (current.toIntOrNull() ?: 0).coerceIn(0, totalN)
                val base = initial ?: Book(title = title.trim(), total = totalN)
                val fin = when {
                    cur >= totalN -> if (base.finishedAt > 0L) base.finishedAt else System.currentTimeMillis()
                    else -> 0L
                }
                onSave(base.copy(title = title.trim(), author = author.trim(), total = totalN, current = cur, finishedAt = fin))
            }) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("Sil", color = DEL_RED) }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

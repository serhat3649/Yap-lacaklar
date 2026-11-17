@file:OptIn(ExperimentalMaterial3Api::class)

package com.kutlu.yapilacaklar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TRE: Locale = Locale.forLanguageTag("tr-TR")
private val EX_RED = Color(0xFFD64545)
private val EX_GREEN = Color(0xFF2F9E6A)

@Composable
fun ExpenseScreen() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(ExpenseStore.load(ctx)) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    var catsVersion by remember { mutableIntStateOf(0) }
    val emojiMap = remember(catsVersion) { ExpenseCats.emojiMap(ctx) }
    var showAllMonths by remember { mutableStateOf(false) }
    var catFilter by remember { mutableStateOf<String?>(null) }

    fun saveExpense(e: Expense) {
        val l = list.toMutableList()
        val i = l.indexOfFirst { it.id == e.id }
        if (i >= 0) l[i] = e else l.add(e)
        list = l
        ExpenseStore.save(ctx, list)
    }

    fun deleteExpense(e: Expense) {
        list = list.filterNot { it.id == e.id }
        ExpenseStore.save(ctx, list)
    }

    val range = ExpenseStore.monthRange(monthOffset)
    val month = list.filter { it.time >= range.first && it.time < range.second }.sortedByDescending { it.time }
    val total = month.sumOf { it.amount }
    val prevTotal = ExpenseStore.sum(list, ExpenseStore.monthRange(monthOffset - 1))
    val today = ExpenseStore.sum(list, ExpenseStore.dayRange(0))

    val cal = Calendar.getInstance().apply { timeInMillis = range.first }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysElapsed = if (monthOffset == 0) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) else daysInMonth
    val avg = if (daysElapsed > 0) total / daysElapsed else 0.0
    val monthName = SimpleDateFormat("MMMM yyyy", TRE).format(Date(range.first))
        .replaceFirstChar { it.titlecase(TRE) }

    // gün gün toplam
    val daily = DoubleArray(daysInMonth)
    month.forEach {
        val d = Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.DAY_OF_MONTH)
        daily[d - 1] += it.amount
    }
    val byCat = month.groupBy { it.cat }.mapValues { (_, v) -> v.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Harcama ekle") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("Harcamalar", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Bugün: ${tl(today)}", fontSize = 13.sp, color = dim)
                }
            }

            // Kategori filtresi
            val usedCats = list.groupBy { it.cat }.mapValues { (_, v) -> v.sumOf { it.amount } }
                .toList().sortedByDescending { it.second }.map { it.first }
            if (usedCats.isNotEmpty()) item(key = "catchips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = catFilter == null, onClick = { catFilter = null }, label = { Text("Tümü") })
                    }
                    items(usedCats) { c ->
                        FilterChip(
                            selected = catFilter == c,
                            onClick = { catFilter = if (catFilter == c) null else c },
                            label = { Text("${emojiMap[c] ?: "🏷"} $c") },
                        )
                    }
                }
            }

            if (catFilter == null) {
            // Ay seçici
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { monthOffset-- }) { Text("‹", fontSize = 26.sp) }
                    Text(monthName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { if (monthOffset < 0) monthOffset++ }, enabled = monthOffset < 0) {
                        Text("›", fontSize = 26.sp)
                    }
                }
            }

            // Özet
            item {
                ExCard {
                    Text("Bu ay toplam", fontSize = 12.sp, color = dim)
                    Text(tl(total), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(tl(avg), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("günlük ortalama", fontSize = 12.sp, color = dim)
                        }
                        Column(Modifier.weight(1f)) {
                            if (prevTotal > 0) {
                                val diff = (total - prevTotal) / prevTotal * 100
                                Text(
                                    (if (diff >= 0) "▲ %" else "▼ %") + "%.0f".format(kotlin.math.abs(diff)),
                                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (diff > 0) EX_RED else EX_GREEN,
                                )
                                Text("geçen aya göre (${tl(prevTotal)})", fontSize = 12.sp, color = dim)
                            } else {
                                Text("${month.size}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("harcama kaydı", fontSize = 12.sp, color = dim)
                            }
                        }
                    }
                }
            }

            // Aylık özet listesi
            val nowCal = Calendar.getInstance()
            val months = list.groupBy {
                val c = Calendar.getInstance().apply { timeInMillis = it.time }
                c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
            }.mapValues { (_, v) -> v.sumOf { it.amount } to v.size }
                .toList().sortedByDescending { it.first }
            if (months.isNotEmpty()) item {
                ExCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aylık özet", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Toplam: ${tl(list.sumOf { it.amount })}", fontSize = 12.sp, color = dim)
                    }
                    Spacer(Modifier.height(6.dp))
                    val maxM = months.maxOf { it.second.first }.takeIf { it > 0 } ?: 1.0
                    val shown = if (showAllMonths) months else months.take(6)
                    shown.forEachIndexed { i, (key, v) ->
                        val (sum, count) = v
                        val off = key - (nowCal.get(Calendar.YEAR) * 12 + nowCal.get(Calendar.MONTH))
                        val prev = months.firstOrNull { it.first == key - 1 }?.second?.first
                        val label = SimpleDateFormat("MMMM yyyy", TRE).format(Date(ExpenseStore.monthRange(off).first))
                            .replaceFirstChar { it.titlecase(TRE) }
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { monthOffset = off }
                                .background(
                                    if (off == monthOffset) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f),
                                    fontWeight = if (off == monthOffset) FontWeight.Bold else FontWeight.Normal)
                                if (prev != null && prev > 0) {
                                    val d = (sum - prev) / prev * 100
                                    Text((if (d >= 0) "▲" else "▼") + "%.0f%%".format(kotlin.math.abs(d)) + "  ",
                                        fontSize = 11.sp, color = if (d > 0) EX_RED else EX_GREEN)
                                }
                                Text(tl(sum), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).height(5.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                                    Box(Modifier.fillMaxWidth((sum / maxM).toFloat()).fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                                }
                                Text("  $count kayıt", fontSize = 11.sp, color = dim)
                            }
                        }
                    }
                    if (months.size > 6) {
                        TextButton(onClick = { showAllMonths = !showAllMonths }) {
                            Text(if (showAllMonths) "Daha az göster" else "Tüm ayları göster (${months.size})")
                        }
                    }
                }
            }

            // Gün gün grafik
            if (total > 0) item {
                ExCard {
                    Text("Gün gün", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    val maxV = daily.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                    Row(
                        Modifier.fillMaxWidth().height(80.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        daily.forEach { v ->
                            Box(
                                Modifier.weight(1f)
                                    .height(maxOf((70.0 * v / maxV).toFloat(), 2f).dp)
                                    .background(
                                        if (v > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("1", fontSize = 10.sp, color = dim, modifier = Modifier.weight(1f))
                        Text("${(daysInMonth + 1) / 2}", fontSize = 10.sp, color = dim,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("$daysInMonth", fontSize = 10.sp, color = dim,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    Spacer(Modifier.height(4.dp))
                    val maxDay = daily.indices.maxByOrNull { daily[it] } ?: 0
                    Text("En çok: ${maxDay + 1}. gün · ${tl(daily[maxDay])}", fontSize = 12.sp, color = dim)
                }
            }

            // Kategori dağılımı
            if (byCat.isNotEmpty()) item {
                ExCard {
                    Text("Kategoriler", fontWeight = FontWeight.SemiBold)
                    Text("Ayrıntı için kategoriye dokun", fontSize = 11.sp, color = dim)
                    Spacer(Modifier.height(8.dp))
                    byCat.forEach { (c, v) ->
                        val frac = (v / total).toFloat()
                        Row(Modifier.clickable { catFilter = c }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${emojiMap[c] ?: "🏷"} $c", fontSize = 14.sp, modifier = Modifier.width(110.dp))
                            Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
                            }
                            Text(tl(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }

            // Liste (gün gün)
            if (month.isEmpty()) {
                item {
                    Text("Bu ay harcama yok. \"Harcama ekle\" ile ya da Pusula'ya \"mazota 1.500 lira verdim\" diyerek ekle.",
                        color = dim, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
                }
            } else {
                val groups = month.groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.time)) }
                groups.forEach { (_, dayItems) ->
                    item(key = "d_" + dayItems.first().id) {
                        Row(Modifier.padding(start = 4.dp, top = 8.dp)) {
                            Text(SimpleDateFormat("d MMMM EEEE", TRE).format(Date(dayItems.first().time)).uppercase(TRE),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dim, modifier = Modifier.weight(1f))
                            Text(tl(dayItems.sumOf { it.amount }), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dim)
                        }
                    }
                    items(dayItems, key = { it.id }) { e ->
                        Card(
                            onClick = { editing = e; showEditor = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emojiMap[e.cat] ?: "🏷", fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.cat + " · " + SimpleDateFormat("HH:mm", TRE).format(Date(e.time)), fontSize = 15.sp)
                                    if (e.note.isNotBlank()) Text(e.note, fontSize = 13.sp, color = dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Text(tl(e.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            } else {
                // ---------- Tek kategori görünümü: tüm zamanlar, tarih tarih ----------
                val cf = catFilter!!
                val catItems = list.filter { it.cat == cf }.sortedByDescending { it.time }
                val catTotal = catItems.sumOf { it.amount }
                item(key = "cat_head") {
                    ExCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${emojiMap[cf] ?: "🏷"} $cf", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { catFilter = null }) { Text("Kapat") }
                        }
                        Text("Tüm zamanlar", fontSize = 12.sp, color = dim)
                        Text(tl(catTotal), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(tl(ExpenseStore.sum(catItems, ExpenseStore.monthRange(0))), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("bu ay", fontSize = 12.sp, color = dim)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${catItems.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("kayıt", fontSize = 12.sp, color = dim)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(tl(if (catItems.isEmpty()) 0.0 else catTotal / catItems.size), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("ortalama", fontSize = 12.sp, color = dim)
                            }
                        }
                    }
                }
                val byMonth = catItems.groupBy { SimpleDateFormat("yyyy-MM", Locale.US).format(Date(it.time)) }
                byMonth.forEach { (mk, mItems) ->
                    item(key = "cm_$mk") {
                        Row(Modifier.padding(start = 4.dp, top = 10.dp)) {
                            Text(SimpleDateFormat("MMMM yyyy", TRE).format(Date(mItems.first().time)).uppercase(TRE),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dim, modifier = Modifier.weight(1f))
                            Text(tl(mItems.sumOf { it.amount }), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dim)
                        }
                    }
                    items(mItems, key = { "c_" + it.id }) { e ->
                        Card(
                            onClick = { editing = e; showEditor = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(SimpleDateFormat("d MMMM yyyy, EEEE", TRE).format(Date(e.time)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(SimpleDateFormat("HH:mm", TRE).format(Date(e.time)), fontSize = 12.sp, color = dim)
                                    if (e.note.isNotBlank()) Text(e.note, fontSize = 13.sp, color = dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Text(tl(e.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ExpenseEditor(
            initial = editing,
            onDismiss = { showEditor = false; catsVersion++ },
            onSave = { saveExpense(it); showEditor = false; catsVersion++ },
            onDelete = { deleteExpense(it); showEditor = false; catsVersion++ },
        )
    }
}

@Composable
private fun ExCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun ExpenseEditor(initial: Expense?, onDismiss: () -> Unit, onSave: (Expense) -> Unit, onDelete: (Expense) -> Unit) {
    val ctx = LocalContext.current
    var amount by remember {
        mutableStateOf(initial?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it).replace('.', ',') } ?: "")
    }
    var cat by remember { mutableStateOf(initial?.cat ?: "Yemek") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var cats by remember { mutableStateOf(ExpenseCats.all(ctx)) }
    var emojis by remember { mutableStateOf(ExpenseCats.emojiMap(ctx)) }
    var showNewCat by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    fun refreshCats() { cats = ExpenseCats.all(ctx); emojis = ExpenseCats.emojiMap(ctx) }
    var whenMs by remember { mutableLongStateOf(initial?.time ?: System.currentTimeMillis()) }
    val parsed = parseAmount(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Harcama ekle" else "Harcamayı düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tutar (₺)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { if (parsed != null) Text(tl(parsed)) },
                )
                Text("Kategori", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (cats + "+").chunked(4).forEach { row ->
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c ->
                                if (c == "+") {
                                    AssistChip(onClick = { showNewCat = true }, label = { Text("➕ Yeni") })
                                    if (ExpenseCats.custom(ctx).isNotEmpty()) {
                                        AssistChip(onClick = { showManage = true }, label = { Text("✏️ Düzenle") })
                                    }
                                } else {
                                    FilterChip(selected = cat == c, onClick = { cat = c },
                                        label = { Text("${emojis[c] ?: "🏷"} $c") })
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(500) },
                    minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Açıklama") },
                    placeholder = { Text("Ne için, nereden, kime… (ör. Shell, Gebze şantiyesi dönüşü)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = whenMs }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            val cur = whenMs
                            whenMs = Calendar.getInstance().apply {
                                timeInMillis = cur; set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                            }.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text(SimpleDateFormat("d MMM yyyy", TRE).format(Date(whenMs))) }
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = whenMs }
                        TimePickerDialog(ctx, { _, h, mi ->
                            val cur = whenMs
                            whenMs = Calendar.getInstance().apply {
                                timeInMillis = cur; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi)
                            }.timeInMillis
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    }) { Text(SimpleDateFormat("HH:mm", TRE).format(Date(whenMs))) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                val base = initial ?: Expense(amount = parsed!!)
                onSave(base.copy(amount = parsed!!, cat = cat, note = note.trim(), time = whenMs))
            }) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("Sil", color = EX_RED) }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )

    if (showNewCat) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("") }
        var err by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showNewCat = false },
            title = { Text("Yeni kategori") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(24); err = false }, singleLine = true,
                        label = { Text("Kategori adı") }, modifier = Modifier.fillMaxWidth(),
                        isError = err,
                        supportingText = { if (err) Text("Bu isimde kategori zaten var") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    OutlinedTextField(
                        value = emoji, onValueChange = { emoji = it.take(4) }, singleLine = true,
                        label = { Text("Emoji (isteğe bağlı, ör. ☕)") }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    if (ExpenseCats.add(ctx, name, emoji)) {
                        refreshCats()
                        cat = cats.firstOrNull { it.equals(name.trim(), ignoreCase = true) } ?: cat
                        showNewCat = false
                    } else err = true
                }) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { showNewCat = false }) { Text("Vazgeç") } },
        )
    }

    if (showManage) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            title = { Text("Kategorilerim") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val mine = ExpenseCats.custom(ctx)
                    if (mine.isEmpty()) Text("Eklediğin kategori yok.")
                    mine.forEach { (n, e) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$e $n", modifier = Modifier.weight(1f), fontSize = 15.sp)
                            TextButton(onClick = {
                                ExpenseCats.remove(ctx, n); refreshCats()
                                if (cat == n) cat = "Diğer"
                                if (ExpenseCats.custom(ctx).isEmpty()) showManage = false
                            }) { Text("Sil", color = EX_RED) }
                        }
                    }
                    Text("Silinen kategorideki eski harcamalar silinmez, adıyla kalır.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            confirmButton = { TextButton(onClick = { showManage = false }) { Text("Tamam") } },
        )
    }

}

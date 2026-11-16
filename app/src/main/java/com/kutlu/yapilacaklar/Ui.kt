@file:OptIn(ExperimentalMaterial3Api::class)

package com.kutlu.yapilacaklar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TR: Locale = Locale.forLanguageTag("tr-TR")
private val RED = Color(0xFFD64545)
private val AMBER = Color(0xFFD9902F)
private val GREEN = Color(0xFF2F9E6A)
private val PRI_COLORS = listOf(RED, AMBER, GREEN)

// ---------------- Tema ----------------

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF7FB3E0),
        onPrimary = Color(0xFF0B1A2A),
        secondaryContainer = Color(0xFF2A3A4C),
        background = Color(0xFF111418),
        surface = Color(0xFF1A1F25),
        surfaceVariant = Color(0xFF242A31),
    ) else lightColorScheme(
        primary = Color(0xFF1F4E79),
        onPrimary = Color.White,
        secondaryContainer = Color(0xFFD6E4F2),
        background = Color(0xFFF4F5F7),
        surface = Color.White,
        surfaceVariant = Color(0xFFEDEFF2),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------- Tarih yardımcıları ----------------

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfTomorrow(): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(System.currentTimeMillis())
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private enum class DueState { LATE, TODAY, NORMAL }

private fun dueInfo(t: Task): Pair<String, DueState>? {
    if (t.due <= 0L) return null
    val today = startOfDay(System.currentTimeMillis())
    val day = startOfDay(t.due)
    val diff = Math.round((day - today) / 86_400_000.0).toInt()
    val time = if (t.hasTime) " " + SimpleDateFormat("HH:mm", TR).format(Date(t.due)) else ""
    return when {
        diff == 0 -> "Bugün$time" to DueState.TODAY
        diff == 1 -> "Yarın$time" to DueState.NORMAL
        diff < 0 -> "${-diff} gün gecikti" to DueState.LATE
        else -> SimpleDateFormat("d MMM", TR).format(Date(t.due)) + time to DueState.NORMAL
    }
}

private fun sortTasks(list: List<Task>): List<Task> = list.sortedWith(
    compareBy<Task> { it.done }
        .thenBy { if (it.done) -it.doneAt else 0L }
        .thenBy { if (it.done) 0 else it.pri }
        .thenBy { if (it.done || it.due == 0L) Long.MAX_VALUE else it.due }
        .thenBy { it.created }
)

// ---------------- Ana ekran ----------------

@Composable
fun TodoScreen(
    tasks: List<Task>,
    onUpsert: (Task) -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: (List<Task>) -> Unit,
    onRestore: (List<Task>) -> Unit,
    onClearDone: () -> List<Task>,
) {
    var filter by remember { mutableStateOf("aktif") }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun undoable(msg: String, items: List<Task>) {
        if (items.isEmpty()) return
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(msg, actionLabel = "Geri al", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onRestore(items)
        }
    }

    val tomorrow = startOfTomorrow()
    val q = query.trim().lowercase(TR)
    val visible = sortTasks(tasks.filter { t ->
        if (q.isNotEmpty() && !t.title.lowercase(TR).contains(q) && !t.note.lowercase(TR).contains(q)) return@filter false
        when {
            filter == "aktif" -> !t.done
            filter == "bugun" -> !t.done && t.due > 0L && t.due < tomorrow
            filter == "tamam" -> t.done
            filter.startsWith("cat:") -> t.cat == filter.removePrefix("cat:") && !t.done
            else -> true
        }
    })

    val total = tasks.size
    val doneCount = tasks.count { it.done }
    val progress = if (total == 0) 0f else doneCount.toFloat() / total

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Yeni iş") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Başlık
            item(key = "header") {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Yapılacaklar", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                SimpleDateFormat("d MMMM EEEE", TR).format(Date()),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "$doneCount / $total", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = GREEN,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Ara…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Temizle")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Filtreler
            item(key = "filters") {
                val defs = listOf(
                    "aktif" to "Aktif (${tasks.count { !it.done }})",
                    "bugun" to "Bugün / Geciken (${tasks.count { !it.done && it.due > 0L && it.due < tomorrow }})",
                    "tamam" to "Tamamlanan ($doneCount)",
                    "tumu" to "Tümü ($total)",
                ) + CATEGORIES.map { c -> "cat:$c" to "$c (${tasks.count { it.cat == c && !it.done }})" }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(defs, key = { it.first }) { (k, label) ->
                        FilterChip(
                            selected = filter == k,
                            onClick = { filter = k },
                            label = { Text(label) },
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (tasks.isEmpty()) "Henüz iş yok.\nAşağıdaki \"Yeni iş\" ile ilk işini ekle."
                        else "Bu filtrede iş yok 👌",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                val rows = buildList<Any> {
                    var last: String? = null
                    visible.forEach { t ->
                        val g = if (t.done) "Tamamlanan" else PRIORITY_LABELS.getOrElse(t.pri) { "Normal" }
                        if (filter != "tamam" && g != last) { add(g); last = g }
                        add(t)
                    }
                }
                items(rows, key = { if (it is Task) it.id else "h_$it" }) { row ->
                    when (row) {
                        is String -> Text(
                            row.uppercase(TR),
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                        is Task -> TaskRow(
                            t = row,
                            onToggle = { onToggle(row) },
                            onClick = { editing = row; showEditor = true },
                        )
                    }
                }
            }

            if (doneCount > 0 && (filter == "tamam" || filter == "tumu")) {
                item(key = "clear") {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = {
                            val removed = onClearDone()
                            undoable("${removed.size} iş temizlendi", removed)
                        }) { Text("Tamamlananları temizle") }
                    }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { onUpsert(it); showEditor = false },
            onDelete = { t ->
                showEditor = false
                onDelete(listOf(t))
                undoable("İş silindi", listOf(t))
            },
        )
    }
}

// ---------------- Satır ----------------

@Composable
private fun TaskRow(t: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(5.dp).fillMaxHeight()
                    .background(if (t.done) MaterialTheme.colorScheme.surfaceVariant else PRI_COLORS.getOrElse(t.pri) { AMBER })
            )
            Checkbox(
                checked = t.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = GREEN),
            )
            Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 12.dp)) {
                Text(
                    t.title,
                    fontSize = 15.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) dim else MaterialTheme.colorScheme.onSurface,
                )
                if (t.note.isNotBlank()) {
                    Text(t.note, fontSize = 13.sp, color = dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(t.cat, dim)
                    dueInfo(t)?.let { (label, state) ->
                        val c = when {
                            t.done -> dim
                            state == DueState.LATE -> RED
                            state == DueState.TODAY -> AMBER
                            else -> dim
                        }
                        Tag("📅 $label", c)
                    }
                    if (t.remind && t.due > 0L && !t.done) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Hatırlatma var",
                            modifier = Modifier.size(15.dp), tint = dim)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(text, fontSize = 11.sp, color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

// ---------------- Ekle / düzenle penceresi ----------------

@Composable
private fun TaskEditor(
    initial: Task?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var cat by remember { mutableStateOf(initial?.cat ?: "İş") }
    var pri by remember { mutableIntStateOf(initial?.pri ?: 1) }
    var due by remember { mutableLongStateOf(initial?.due ?: 0L) }
    var hasTime by remember { mutableStateOf(initial?.hasTime ?: false) }
    var remind by remember { mutableStateOf(initial?.remind ?: false) }

    fun pickDate() {
        val c = Calendar.getInstance().apply { if (due > 0L) timeInMillis = due }
        DatePickerDialog(ctx, { _, y, m, d ->
            val n = Calendar.getInstance().apply {
                if (due > 0L) timeInMillis = due
                else { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }
                set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (due == 0L) remind = true
            due = n.timeInMillis
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun pickTime() {
        val c = Calendar.getInstance().apply { if (due > 0L) timeInMillis = due }
        TimePickerDialog(ctx, { _, h, min ->
            val n = Calendar.getInstance().apply {
                if (due > 0L) timeInMillis = due
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (due == 0L) remind = true
            due = n.timeInMillis
            hasTime = true
        }, if (due > 0L) c.get(Calendar.HOUR_OF_DAY) else 9, if (due > 0L) c.get(Calendar.MINUTE) else 0, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni iş" else "İşi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ne yapılacak?") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(1000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Not (isteğe bağlı)") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Text("Kategori", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
                    }
                }

                Text("Öncelik", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRIORITY_LABELS.forEachIndexed { i, l ->
                        FilterChip(
                            selected = pri == i, onClick = { pri = i },
                            label = { Text(l) },
                            leadingIcon = {
                                Box(Modifier.size(10.dp).background(PRI_COLORS[i], RoundedCornerShape(5.dp)))
                            },
                        )
                    }
                }

                Text("Son tarih", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = ::pickDate, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (due > 0L) SimpleDateFormat("d MMM yyyy", TR).format(Date(due)) else "Tarih seç")
                    }
                    OutlinedButton(onClick = ::pickTime, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(if (due > 0L && hasTime) SimpleDateFormat("HH:mm", TR).format(Date(due)) else "Saat")
                    }
                    if (due > 0L) {
                        IconButton(onClick = { due = 0L; hasTime = false; remind = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Tarihi kaldır")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hatırlat", fontSize = 15.sp)
                        val hint = when {
                            due == 0L -> "Önce tarih seç"
                            !hasTime -> "Saat seçilmezse 09:00'da bildirim gelir"
                            remind && due <= System.currentTimeMillis() -> "Bu zaman geçmiş, bildirim gelmez"
                            else -> "Son tarihte bildirim gelir"
                        }
                        Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(checked = remind && due > 0L, enabled = due > 0L, onCheckedChange = { remind = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: Task(title = title.trim())
                    onSave(
                        base.copy(
                            title = title.trim(), note = note.trim(), cat = cat, pri = pri,
                            due = due, hasTime = hasTime && due > 0L, remind = remind && due > 0L,
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) { Text("Sil", color = RED) }
                }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

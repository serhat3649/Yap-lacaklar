@file:OptIn(ExperimentalMaterial3Api::class)

package com.kutlu.yapilacaklar

import android.app.Activity
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QUICK = listOf(
    "☀️ Sabah brifingi" to "Bana kısa bir sabah brifingi ver: bugünün ve geciken işlerim, dünkü namaz durumum, okuma hedefim.",
    "🗓 Günümü planla" to "Bugünkü ve geciken işlerime bakıp günümü saat saat planla. Acil olanları öne al.",
    "📋 Bu hafta neler var?" to "Bu haftaki işlerimi gün gün özetle.",
    "⏰ Geciken işler" to "Geciken işlerimi listele ve hangisine önce bakmam gerektiğini söyle.",
)

@Composable
fun AssistantScreen(onDataChanged: () -> Unit, autoPrompt: String?, onAutoPromptUsed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(ChatStore.load(ctx)) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(AssistantSettings.apiKey(ctx).isNotBlank()) }
    val listState = rememberLazyListState()

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (!hasKey) { showSettings = true; return }
        val history = messages
        messages = messages + ChatMsg("user", t)
        ChatStore.save(ctx, messages)
        input = ""
        busy = true
        scope.launch {
            val reply = try {
                val (ans, changed) = withContext(Dispatchers.IO) { Assistant.ask(ctx.applicationContext, history, t) }
                if (changed) onDataChanged()
                ChatMsg("assistant", ans)
            } catch (e: AssistantException) {
                ChatMsg("assistant", "⚠️ ${e.message}", error = true)
            } catch (e: Exception) {
                ChatMsg("assistant", "⚠️ Beklenmeyen hata: ${e.message}", error = true)
            }
            messages = messages + reply
            ChatStore.save(ctx, messages)
            busy = false
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) send(said)
        }
    }

    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dinliyorum…")
        }
        try {
            speech.launch(i)
        } catch (e: ActivityNotFoundException) {
            messages = messages + ChatMsg("assistant", "⚠️ Telefonda ses tanıma bulunamadı. Google uygulamasının yüklü olduğundan emin ol.", error = true)
        }
    }

    // Sabah bildiriminden gelindiyse hazır soruyu gönder
    LaunchedEffect(autoPrompt) {
        if (autoPrompt != null) {
            onAutoPromptUsed()
            send(autoPrompt)
        }
    }

    LaunchedEffect(messages.size, busy) {
        val n = messages.size + (if (busy) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Başlık
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pusula", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (AssistantSettings.model(ctx) == AssistantSettings.MODEL_FAST) "Claude Haiku · hızlı" else "Claude Sonnet",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (messages.isNotEmpty()) IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Sohbeti temizle")
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Ayarlar") }
        }

        // Mesajlar
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item { Welcome(hasKey) { showSettings = true } }
            }
            items(messages.size) { i -> Bubble(messages[i]) }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Düşünüyor…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }

        // Hızlı sorular
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QUICK.forEach { (label, prompt) ->
                AssistChip(onClick = { send(prompt) }, label = { Text(label) }, enabled = !busy)
            }
        }

        // Giriş satırı
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Yaz ya da 🎤 ile söyle…") },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.width(6.dp))
            if (input.isBlank()) {
                FilledIconButton(onClick = { listen() }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Text("🎤", fontSize = 20.sp)
                }
            } else {
                FilledIconButton(onClick = { send(input) }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Send, contentDescription = "Gönder")
                }
            }
        }
    }

    if (showSettings) {
        AssistantSettingsDialog(onClose = {
            showSettings = false
            hasKey = AssistantSettings.apiKey(ctx).isNotBlank()
            Brief.schedule(ctx)
        })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Sohbet temizlensin mi?") },
            text = { Text("Sadece konuşma geçmişi silinir; işler, namaz ve kitap kayıtları kalır.") },
            confirmButton = {
                TextButton(onClick = {
                    messages = emptyList(); ChatStore.save(ctx, messages); confirmClear = false
                }) { Text("Temizle") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Welcome(hasKey: Boolean, openSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Merhaba, ben Pusula 🧭", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Bana yazarak ya da 🎤 ile konuşarak şunları yaptırabilirsin:", fontSize = 14.sp)
            Text(
                "• \"Yarın 10'da Berat'la Gebze projesini konuş, hatırlat\"\n" +
                    "• \"Öğleyi ve ikindiyi kıldım\"\n" +
                    "• \"Bugün 25 sayfa okudum\"\n" +
                    "• \"Günümü planla\"\n" +
                    "• \"DN25 boruda 1 m/s hızla debi ne olur?\"",
                fontSize = 14.sp,
            )
            if (!hasKey) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Başlamak için ayarlardan Claude API anahtarını girmen gerekiyor.",
                    fontSize = 13.sp, color = Color(0xFFD9902F),
                )
                Button(onClick = openSettings) { Text("Ayarları aç") }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            color = when {
                mine -> MaterialTheme.colorScheme.primary
                m.error -> Color(0x22D64545)
                else -> MaterialTheme.colorScheme.surface
            },
            border = if (mine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(
                    m.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AssistantSettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val oldKey = remember { AssistantSettings.apiKey(ctx) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(AssistantSettings.model(ctx)) }
    var about by remember { mutableStateOf(AssistantSettings.about(ctx)) }
    var briefOn by remember { mutableStateOf(AssistantSettings.briefOn(ctx)) }
    var bh by remember { mutableIntStateOf(AssistantSettings.briefHour(ctx)) }
    var bm by remember { mutableIntStateOf(AssistantSettings.briefMinute(ctx)) }
    var lockOn by remember { mutableStateOf(AppLock.enabled(ctx)) }
    val lockAvailable = remember { AppLock.available(ctx) }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    fun save() {
        if (key.isNotBlank()) AssistantSettings.setApiKey(ctx, key)
        AssistantSettings.setModel(ctx, model)
        AssistantSettings.setAbout(ctx, about.trim().ifEmpty { AssistantSettings.DEFAULT_ABOUT })
        AssistantSettings.setBrief(ctx, briefOn, bh, bm)
        AppLock.setEnabled(ctx, lockOn)
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ayarlar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Claude API anahtarı", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = key, onValueChange = { key = it.trim() }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(if (oldKey.isBlank()) "sk-ant-…" else "Kayıtlı: …" + oldKey.takeLast(4))
                    },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    "console.anthropic.com → API Keys bölümünden alınır. Anahtar sadece bu telefonda, şifreli saklanır.",
                    fontSize = 12.sp, color = dim,
                )
                if (oldKey.isNotBlank()) {
                    TextButton(onClick = { AssistantSettings.setApiKey(ctx, ""); onClose() }) {
                        Text("Kayıtlı anahtarı sil", color = Color(0xFFD64545))
                    }
                }

                Text("Model", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = model == AssistantSettings.MODEL_SMART,
                        onClick = { model = AssistantSettings.MODEL_SMART },
                        label = { Text("Sonnet (akıllı)") },
                    )
                    FilterChip(
                        selected = model == AssistantSettings.MODEL_FAST,
                        onClick = { model = AssistantSettings.MODEL_FAST },
                        label = { Text("Haiku (ucuz, hızlı)") },
                    )
                }

                Text("Asistan seni nasıl tanısın?", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = about, onValueChange = { about = it.take(1500) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sabah brifingi", fontWeight = FontWeight.SemiBold)
                        Text("Her gün özet bildirimi", fontSize = 12.sp, color = dim)
                    }
                    Switch(checked = briefOn, onCheckedChange = { briefOn = it })
                }
                if (briefOn) {
                    OutlinedButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m -> bh = h; bm = m }, bh, bm, true).show()
                    }) { Text("Saat: %02d:%02d".format(bh, bm)) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Parmak izi kilidi", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (lockAvailable) "Açılışta ve 1 dakikadan uzun arka planda kalınca sorar"
                            else "Telefonda parmak izi veya ekran kilidi tanımlı değil",
                            fontSize = 12.sp, color = dim,
                        )
                    }
                    Switch(checked = lockOn && lockAvailable, enabled = lockAvailable, onCheckedChange = { lockOn = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Vazgeç") } },
    )
}

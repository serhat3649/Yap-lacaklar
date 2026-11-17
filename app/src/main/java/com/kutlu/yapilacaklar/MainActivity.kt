package com.kutlu.yapilacaklar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

class MainActivity : FragmentActivity() {

    private var tasks by mutableStateOf(listOf<Task>())
    private var tab by mutableIntStateOf(0)
    private var autoPrompt by mutableStateOf<String?>(null)
    private var locked by mutableStateOf(false)
    private var promptShowing = false
    private var lastStop = 0L

    private fun unlock() {
        if (promptShowing) return
        promptShowing = true
        AppLock.prompt(this,
            onSuccess = { promptShowing = false; locked = false },
            onFail = { promptShowing = false },
        )
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.active(this)) {
            if (lastStop > 0L && System.currentTimeMillis() - lastStop > AppLock.RELOCK_MS) locked = true
            if (locked) unlock()
        } else {
            locked = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) lastStop = System.currentTimeMillis()
    }

    private fun readIntent(i: Intent?) {
        if (i == null) return
        if (i.hasExtra(Brief.EXTRA_TAB)) tab = i.getIntExtra(Brief.EXTRA_TAB, 0)
        i.getStringExtra(Brief.EXTRA_PROMPT)?.let { autoPrompt = it }
        i.removeExtra(Brief.EXTRA_PROMPT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.ensureChannel(this)
        tasks = TaskStore.load(this)
        locked = savedInstanceState == null && AppLock.active(this)
        if (savedInstanceState == null) readIntent(intent)
        Brief.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(this)) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppTheme {
                if (locked) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("🧭", fontSize = 64.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Pusula kilitli", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { unlock() }) { Text("Parmak iziyle aç") }
                    }
                    return@AppTheme
                }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            listOf("🤖" to "Asistan", "✅" to "İşler", "🕌" to "Namaz", "📖" to "Kitap", "💰" to "Harcama").forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(icon, fontSize = 20.sp) },
                                    label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        when (tab) {
                            0 -> AssistantScreen(
                                onDataChanged = { tasks = TaskStore.load(this@MainActivity) },
                                autoPrompt = autoPrompt,
                                onAutoPromptUsed = { autoPrompt = null },
                            )
                            1 -> TodoScreen(
                                tasks = tasks,
                                onUpsert = ::upsert,
                                onToggle = ::toggle,
                                onDelete = ::delete,
                                onRestore = ::restore,
                                onClearDone = ::clearDone,
                            )
                            2 -> PrayerScreen()
                            3 -> BookScreen()
                            else -> ExpenseScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listener = { tasks = TaskStore.load(this) }
        tasks = TaskStore.load(this)
    }

    override fun onPause() {
        super.onPause()
        listener = null
    }

    private fun commit(list: List<Task>) {
        tasks = list
        TaskStore.save(this, list)
    }

    private fun upsert(t: Task) {
        val list = tasks.toMutableList()
        val i = list.indexOfFirst { it.id == t.id }
        if (i >= 0) list[i] = t else list.add(t)
        commit(list)
        Reminders.schedule(this, t)
    }

    private fun toggle(t: Task) {
        val nt = t.copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else 0L)
        upsert(nt)
        if (nt.done) Reminders.cancelAll(this, nt.id)
    }

    private fun delete(items: List<Task>) {
        val ids = items.map { it.id }.toSet()
        commit(tasks.filterNot { it.id in ids })
        items.forEach { Reminders.cancelAll(this, it.id) }
    }

    private fun restore(items: List<Task>) {
        val ids = tasks.map { it.id }.toSet()
        commit(tasks + items.filterNot { it.id in ids })
        items.forEach { Reminders.schedule(this, it) }
    }

    private fun clearDone(): List<Task> {
        val done = tasks.filter { it.done }
        delete(done)
        return done
    }

    companion object {
        private var listener: (() -> Unit)? = null

        /** Bildirimden "Tamamlandı" basılınca açık ekranı yeniler. */
        fun notifyChanged() {
            listener?.invoke()
        }
    }
}

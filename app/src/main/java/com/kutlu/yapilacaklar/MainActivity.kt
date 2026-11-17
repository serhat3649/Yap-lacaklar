package com.kutlu.yapilacaklar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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

class MainActivity : ComponentActivity() {

    private var tasks by mutableStateOf(listOf<Task>())
    private var tab by mutableIntStateOf(0)
    private var autoPrompt by mutableStateOf<String?>(null)

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
        if (savedInstanceState == null) readIntent(intent)
        Brief.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(this)) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            listOf("🤖" to "Asistan", "✅" to "İşler", "🕌" to "Namaz", "📖" to "Kitap").forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(icon, fontSize = 20.sp) },
                                    label = { Text(label) },
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
                            else -> BookScreen()
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

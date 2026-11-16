package com.kutlu.yapilacaklar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var tasks by mutableStateOf(listOf<Task>())

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.ensureChannel(this)
        tasks = TaskStore.load(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(this)) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppTheme {
                TodoScreen(
                    tasks = tasks,
                    onUpsert = ::upsert,
                    onToggle = ::toggle,
                    onDelete = ::delete,
                    onRestore = ::restore,
                    onClearDone = ::clearDone,
                )
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

package com.kutlu.yapilacaklar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        when (intent.action) {
            Reminders.ACTION_FIRE -> {
                val t = TaskStore.find(ctx, id) ?: return
                if (!t.done) Reminders.showNotification(ctx, t)
            }
            Reminders.ACTION_DONE -> {
                TaskStore.update(ctx, id) { it.copy(done = true, doneAt = System.currentTimeMillis()) }
                Reminders.cancelAll(ctx, id)
                MainActivity.notifyChanged()
            }
            Reminders.ACTION_SNOOZE -> {
                NotificationManagerCompat.from(ctx).cancel(id.hashCode())
                Reminders.setAlarm(ctx, id, System.currentTimeMillis() + 60 * 60 * 1000L)
            }
        }
    }
}

/** Telefon yeniden başlayınca, uygulama güncellenince veya saat değişince alarmları yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.ensureChannel(ctx)
        Reminders.rescheduleAll(ctx)
        Brief.schedule(ctx)
    }
}

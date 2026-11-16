package com.kutlu.yapilacaklar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.kutlu.yapilacaklar.FIRE"
    const val ACTION_DONE = "com.kutlu.yapilacaklar.DONE"
    const val ACTION_SNOOZE = "com.kutlu.yapilacaklar.SNOOZE"
    const val EXTRA_ID = "id"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Hatırlatmalar", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "İşlerin son tarih hatırlatmaları"
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
    }

    private fun firePending(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Hatırlatma zamanı: saat seçildiyse o saat, seçilmediyse günün 09:00'u (due zaten öyle kaydedilir). */
    fun schedule(ctx: Context, t: Task) {
        cancelAlarm(ctx, t.id)
        if (!t.remind || t.done || t.due <= 0L) return
        if (t.due <= System.currentTimeMillis()) return
        setAlarm(ctx, t.id, t.due)
    }

    fun setAlarm(ctx: Context, id: String, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePending(ctx, id)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelAlarm(ctx: Context, id: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(firePending(ctx, id))
    }

    fun cancelAll(ctx: Context, id: String) {
        cancelAlarm(ctx, id)
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }

    fun rescheduleAll(ctx: Context) {
        TaskStore.load(ctx).forEach { schedule(ctx, it) }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showNotification(ctx: Context, t: Task) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return

        val openIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(
            ctx, t.id.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(a: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, t.id.hashCode() + code,
            Intent(ctx, ReminderReceiver::class.java).setAction(a).putExtra(EXTRA_ID, t.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pri = PRIORITY_LABELS.getOrElse(t.pri) { "Normal" }
        val text = buildString {
            append(t.cat).append(" · ").append(pri)
            if (t.note.isNotBlank()) append("\n").append(t.note)
        }

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t.title)
            .setContentText("${t.cat} · $pri")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "Tamamlandı", action(ACTION_DONE, 1))
            .addAction(0, "1 saat ertele", action(ACTION_SNOOZE, 2))
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(t.id.hashCode(), n)
        } catch (e: SecurityException) {
            // izin yoksa sessizce geç
        }
    }
}

package com.kutlu.yapilacaklar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

/** Her sabah seçilen saatte, internetsiz hazırlanan özet bildirimi. */
object Brief {
    private const val ACTION = "com.kutlu.yapilacaklar.BRIEF"
    private const val NOTIF_ID = 777001
    const val EXTRA_TAB = "open_tab"
    const val EXTRA_PROMPT = "auto_prompt"

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, NOTIF_ID, Intent(ctx, BriefReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        am.cancel(pi)
        if (!AssistantSettings.briefOn(ctx)) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, AssistantSettings.briefHour(ctx))
            set(Calendar.MINUTE, AssistantSettings.briefMinute(ctx))
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 5_000) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun summary(ctx: Context): Pair<String, String> {
        val tasks = TaskStore.load(ctx).filter { !it.done }
        val tom = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val today = tom - 86_400_000L
        val todays = tasks.filter { it.due in today until tom }.sortedWith(compareBy<Task>({ it.pri }, { it.due }))
        val late = tasks.filter { it.due in 1 until today }
        val urgent = tasks.count { it.pri == 0 }

        val title = buildString {
            append("Günaydın! ")
            if (todays.isEmpty() && late.isEmpty()) append("Bugün için planlı iş yok")
            else {
                append("Bugün ${todays.size} iş")
                if (late.isNotEmpty()) append(", ${late.size} geciken")
            }
        }

        val prayers = TrackStore.loadPrayers(ctx)
        val y = Integer.bitCount(prayers[TrackStore.dayKey(TrackStore.dayMs(-1))] ?: 0)
        val log = TrackStore.loadReadLog(ctx)
        val readStreak = TrackStore.streak(log) { it > 0 }

        val body = buildString {
            todays.take(5).forEach { append("• ").append(it.title).append(if (it.pri == 0) " (acil)" else "").append('\n') }
            if (todays.size > 5) append("• +${todays.size - 5} iş daha\n")
            if (late.isNotEmpty()) append("⏰ Geciken: ").append(late.take(3).joinToString { it.title }).append('\n')
            if (urgent > 0) append("🔴 Toplam acil iş: $urgent\n")
            append("🕌 Dün namaz: $y/5 · 📖 Okuma serisi: $readStreak gün (hedef ${TrackStore.loadGoal(ctx)} sayfa)")
            val ex = ExpenseStore.load(ctx)
            append("\n💰 Dün: ${tl(ExpenseStore.sum(ex, ExpenseStore.dayRange(-1)))} · Bu ay: ${tl(ExpenseStore.sum(ex, ExpenseStore.monthRange(0)))}")
        }
        return title to body
    }

    fun show(ctx: Context) {
        Reminders.ensureChannel(ctx)
        if (!Reminders.canNotify(ctx)) return
        val (title, body) = summary(ctx)

        fun open(code: Int, prompt: String?): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_TAB, 0)
            if (prompt != null) i.putExtra(EXTRA_PROMPT, prompt)
            return PendingIntent.getActivity(ctx, NOTIF_ID + code, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open(1, null))
            .addAction(0, "🗓 Günümü planla", open(2, "Bugünkü ve geciken işlerime bakıp günümü saat saat planla. Acil olanları öne al."))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
        }
    }
}

class BriefReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Brief.show(ctx)
        Brief.schedule(ctx) // ertesi gün için
    }
}

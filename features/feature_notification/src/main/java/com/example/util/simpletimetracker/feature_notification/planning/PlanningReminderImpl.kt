package com.example.util.simpletimetracker.feature_notification.planning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.util.simpletimetracker.core.utils.PendingIntents
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.feature_notification.R
import com.example.util.simpletimetracker.feature_notification.core.AlarmManagerController
import com.example.util.simpletimetracker.feature_notification.core.GetDoNotDisturbHandledScheduleInteractor
import com.example.util.simpletimetracker.feature_notification.recevier.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PlanningReminderImpl @Inject constructor(
    @ApplicationContext private val context: Context, private val repo: PlanningRepo,
    private val alarms: AlarmManagerController, private val prefs: PrefsInteractor,
    private val quiet: GetDoNotDisturbHandledScheduleInteractor,
) : PlanningReminder {
    private val delivery = Mutex()
    private val delivered by lazy { context.getSharedPreferences("life_clock_plan_reminders", Context.MODE_PRIVATE) }
    override suspend fun reschedule() {
        val now = System.currentTimeMillis()
        val next = PlanCalendar.occurrences(repo.schedules(), repo.exceptions(), now, now + 366L * 86_400_000)
            .firstOrNull { it.start > now && it.schedule.remind && it.status == PlanStatus.OPEN }
        if (next == null) alarms.cancelSchedule(pending(null)) else alarms.scheduleAtTime(next.start, pending(next))
    }
    override suspend fun show(occurrenceId: String, start: Long) = delivery.withLock {
        withContext(Dispatchers.IO) { showOnce(occurrenceId, start) }
    }
    private suspend fun showOnce(occurrenceId: String, start: Long) {
        val now = System.currentTimeMillis()
        val items = PlanCalendar.occurrences(repo.schedules(), repo.exceptions(), start, start + 1)
            .filter { it.start == start && it.schedule.remind && it.status == PlanStatus.OPEN && now < it.end }
        if (items.any { it.id == occurrenceId } && now >= start &&
            quiet.execute(now, prefs.getActivityReminderDoNotDisturbStart(), prefs.getActivityReminderDoNotDisturbEnd()) == now) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) { reschedule(); return }
            delivered.edit().apply {
                delivered.all.filterValues { it is Long && it <= now }.keys.forEach(::remove)
            }.commit()
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "计划开始提醒", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val open = intent?.let { PendingIntent.getActivity(context, 5832, it, PendingIntents.getFlags()) }
            items.forEach { item ->
                val key = "${item.id}#$start"
                if (delivered.contains(key)) return@forEach
                if (!delivered.edit().putLong(key, item.end).commit()) return@forEach
                try {
                    manager.notify(item.id, 5832, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(item.schedule.title).setContentText("到了预留的时间。可以打开规划，决定是否开始。")
                        .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).build())
                } catch (_: SecurityException) { delivered.edit().remove(key).commit() }
            }
        }
        reschedule()
    }
    private fun pending(value: PlanOccurrence?): PendingIntent = PendingIntent.getBroadcast(context, 5832,
        Intent(context, NotificationReceiver::class.java).setAction(ACTION).putExtra("occurrenceId", value?.id).putExtra("start", value?.start ?: 0), PendingIntents.getFlags())
    companion object { const val ACTION = "app.lifetimeclock.PLAN_REMINDER"; private const val CHANNEL = "PLAN_REMINDER" }
}

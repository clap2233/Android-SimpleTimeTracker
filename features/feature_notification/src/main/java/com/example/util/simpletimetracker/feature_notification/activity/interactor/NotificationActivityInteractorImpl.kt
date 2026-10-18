package com.example.util.simpletimetracker.feature_notification.activity.interactor

import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.notifications.interactor.NotificationActivityInteractor
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.feature_notification.R
import com.example.util.simpletimetracker.feature_notification.activity.manager.NotificationActivityManager
import com.example.util.simpletimetracker.feature_notification.activity.manager.NotificationActivityParams
import com.example.util.simpletimetracker.feature_notification.activity.scheduler.NotificationActivityScheduler
import com.example.util.simpletimetracker.feature_notification.core.GetDoNotDisturbHandledScheduleInteractor
import javax.inject.Inject

class NotificationActivityInteractorImpl @Inject constructor(
    private val resourceRepo: ResourceRepo,
    private val manager: NotificationActivityManager,
    private val scheduler: NotificationActivityScheduler,
    private val prefsInteractor: PrefsInteractor,
    private val recordTypeInteractor: RecordTypeInteractor,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val getDoNotDisturbHandledScheduleInteractor: GetDoNotDisturbHandledScheduleInteractor,
    private val currentTimestampProvider: CurrentTimestampProvider,
    private val foregroundPrompt: com.example.util.simpletimetracker.core.interactor.TimerReminderPrompt,
) : NotificationActivityInteractor {
    private suspend fun next(record: RunningRecord, now: Long): Long? {
        val session = record.timerSession
        if (session?.muted == true) return null
        val policy = session?.policy ?: TimerPolicy()
        val start = session?.startedAt ?: record.timeStarted
        val next = policy.nextReminder(start, now, 0) ?: return null
        val allowed = getDoNotDisturbHandledScheduleInteractor.execute(next,
            prefsInteractor.getActivityReminderDoNotDisturbStart(), prefsInteractor.getActivityReminderDoNotDisturbEnd())
        return policy.nextReminder(start, allowed - 1, 0)
    }

    override suspend fun checkAndSchedule() {
        val now = currentTimestampProvider.get()
        val next = runningRecordInteractor.getAll().mapNotNull { record -> next(record, now)?.let { record to it } }.minByOrNull { it.second }
        if (next == null) cancel() else scheduler.schedule(next.second, next.first.timerSession?.id)
    }

    override fun cancel() { scheduler.cancelSchedule(); manager.hide() }

    override suspend fun show(expectedSessionId: String?, scheduledAt: Long?) {
        val now = currentTimestampProvider.get()
        if (scheduledAt == null || scheduledAt <= 0 || now < scheduledAt) return
        val record = runningRecordInteractor.getAll().singleOrNull() ?: return
        if (record.timerSession?.id != expectedSessionId) return
        // A cancelled, replaced, muted or quiet-hours alarm must not become a reminder for a new timer.
        if (next(record, scheduledAt - 1) != scheduledAt) return
        // Inexact alarms may arrive late in Doze. Deliver at most the current phase, never a backlog.
        if (next(record, scheduledAt)?.let { now >= it } == true) return
        if (getDoNotDisturbHandledScheduleInteractor.execute(now,
                prefsInteractor.getActivityReminderDoNotDisturbStart(), prefsInteractor.getActivityReminderDoNotDisturbEnd()) != now) return
        val name = recordTypeInteractor.getAll().firstOrNull { it.id == record.id }?.name.orEmpty()
        val periodic = record.timerSession?.policy?.mode == TimerPolicy.Mode.INTERVAL
        foregroundPrompt.show("$name · 可以考虑收尾，计时仍在继续。")
        manager.show(NotificationActivityParams(
            title = if (periodic) "可以考虑收尾" else resourceRepo.getString(R.string.notification_activity_title),
            subtitle = if (periodic) "$name · 可以等本局或本章结束后再停止，计时仍在继续。" else resourceRepo.getString(R.string.notification_activity_text, name),
            isDarkTheme = prefsInteractor.getDarkMode(),
            sessionId = record.timerSession?.id,
        ))
    }
}

package com.example.util.simpletimetracker.feature_notification.activity.interactor

import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.notifications.interactor.GetNextActivityReminderInteractor
import com.example.util.simpletimetracker.domain.notifications.interactor.NotificationActivityInteractor
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
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
    private val getNextActivityReminderInteractor: GetNextActivityReminderInteractor,
    private val currentTimestampProvider: CurrentTimestampProvider,
) : NotificationActivityInteractor {

    override suspend fun checkAndSchedule() {
        val durationSeconds = prefsInteractor.getActivityReminderDuration()
        val runningRecords = runningRecordInteractor.getAll()
        if (durationSeconds <= 0 || durationSeconds > Long.MAX_VALUE / 1000L || runningRecords.isEmpty()) {
            cancel()
            return
        }
        val now = currentTimestampProvider.get()
        val dndStart = prefsInteractor.getActivityReminderDoNotDisturbStart()
        val dndEnd = prefsInteractor.getActivityReminderDoNotDisturbEnd()
        val timestamp = runningRecords.mapNotNull { record ->
            val next = getNextActivityReminderInteractor.execute(
                timeStarted = record.timeStarted,
                now = now,
                intervalMillis = durationSeconds * 1000L,
            ) ?: return@mapNotNull null
            val allowed = getDoNotDisturbHandledScheduleInteractor.execute(next, dndStart, dndEnd)
            // Preserve the start-relative phase when quiet hours postpone the alarm.
            getNextActivityReminderInteractor.execute(
                timeStarted = record.timeStarted,
                now = allowed - 1,
                intervalMillis = durationSeconds * 1000L,
            )
        }.minOrNull()
        if (timestamp == null) {
            cancel()
            return
        }
        scheduler.schedule(timestamp)
    }

    override fun cancel() {
        scheduler.cancelSchedule()
        manager.hide()
    }

    override suspend fun show() {
        val recordTypes = recordTypeInteractor.getAll().associateBy { it.id }
        val runningRecords = runningRecordInteractor.getAll()
        if (runningRecords.isEmpty() || prefsInteractor.getActivityReminderDuration() <= 0) {
            cancel()
            return
        }
        val recordNames = runningRecords
            .mapNotNull { recordTypes[it.id] }
            .joinToString(separator = ", ") { it.name }

        NotificationActivityParams(
            title = resourceRepo.getString(R.string.notification_activity_title),
            subtitle = resourceRepo.getString(R.string.notification_activity_text, recordNames),
            isDarkTheme = prefsInteractor.getDarkMode(),
        ).let(manager::show)
    }
}

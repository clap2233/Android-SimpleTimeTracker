package com.example.util.simpletimetracker.feature_notification.activity.controller

import com.example.util.simpletimetracker.core.extension.allowDiskRead
import com.example.util.simpletimetracker.domain.notifications.interactor.NotificationActivityInteractor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationActivityBroadcastController @Inject constructor(
    private val mutations: com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo,
    private val planningReminder: com.example.util.simpletimetracker.domain.planning.PlanningReminder,
    private val notificationActivityInteractor: NotificationActivityInteractor,
) {

    fun onMute(sessionId: String) = allowDiskRead { MainScope() }.launch {
        mutations.muteReminder(sessionId)
        notificationActivityInteractor.checkAndSchedule()
    }

    fun onActivityReminder(sessionId: String?, scheduledAt: Long) = allowDiskRead { MainScope() }.launch {
        notificationActivityInteractor.show(sessionId, scheduledAt)
        checkAndSchedule()
    }

    fun onBootCompleted() = allowDiskRead { MainScope() }.launch {
        planningReminder.reschedule()
        checkAndSchedule()
    }

    private suspend fun checkAndSchedule() {
        notificationActivityInteractor.checkAndSchedule()
    }
}

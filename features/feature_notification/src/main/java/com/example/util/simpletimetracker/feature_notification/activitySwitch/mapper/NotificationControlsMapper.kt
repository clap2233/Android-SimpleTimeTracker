package com.example.util.simpletimetracker.feature_notification.activitySwitch.mapper

import com.example.util.simpletimetracker.feature_notification.activitySwitch.manager.NotificationControlsManager
import javax.inject.Inject

class NotificationControlsMapper @Inject constructor() {

    fun mapFromToExtra(from: NotificationControlsManager.From): Int {
        return when (from) {
            is NotificationControlsManager.From.ActivityNotification -> 1
            is NotificationControlsManager.From.ActivitySwitch -> 2
        }
    }

    fun mapExtraToFrom(
        extra: Int,
        recordTypeId: Long,
        sessionId: String? = null,
    ): NotificationControlsManager.From? {
        return when (extra) {
            1 -> NotificationControlsManager.From.ActivityNotification(recordTypeId, sessionId)
            2 -> NotificationControlsManager.From.ActivitySwitch(sessionId)
            else -> null
        }
    }
}
package com.example.util.simpletimetracker.feature_notification.activitySwitch.manager

import com.example.util.simpletimetracker.feature_views.viewData.RecordTypeIcon

data class NotificationActivitySwitchParams(
    val icon: RecordTypeIcon,
    val color: Int,
    val title: CharSequence,
    val subtitle: String,
    val untrackedStartedTimeStamp: Long?,
    val prevRecordDuration: Long?,
    val controls: NotificationControlsParams,
    val sessionId: String = "idle",
)

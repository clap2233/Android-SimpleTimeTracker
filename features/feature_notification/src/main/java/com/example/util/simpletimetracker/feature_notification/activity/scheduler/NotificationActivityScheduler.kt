package com.example.util.simpletimetracker.feature_notification.activity.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.util.simpletimetracker.core.utils.PendingIntents
import com.example.util.simpletimetracker.feature_notification.core.AlarmManagerController
import com.example.util.simpletimetracker.feature_notification.recevier.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NotificationActivityScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManagerController: AlarmManagerController,
) {

    fun schedule(timestamp: Long, sessionId: String?) {
        alarmManagerController.scheduleAtTime(timestamp, getPendingIntent(sessionId, timestamp))
    }

    fun cancelSchedule() {
        alarmManagerController.cancelSchedule(getPendingIntent())
    }

    private fun getPendingIntent(sessionId: String? = null, timestamp: Long = 0): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_ACTIVITY_REMINDER
            putExtra("timerSessionId", sessionId)
            putExtra("scheduledAt", timestamp)
        }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntents.getFlags(),
        )
    }
}
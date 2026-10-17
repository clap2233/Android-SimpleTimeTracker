package com.example.util.simpletimetracker.domain.notifications.interactor

interface NotificationActivityInteractor {

    suspend fun checkAndSchedule()

    fun cancel()

    suspend fun show(expectedSessionId: String? = null, scheduledAt: Long? = null)
}
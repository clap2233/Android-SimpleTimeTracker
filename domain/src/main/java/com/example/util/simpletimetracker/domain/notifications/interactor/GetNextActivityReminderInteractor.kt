package com.example.util.simpletimetracker.domain.notifications.interactor

import javax.inject.Inject

class GetNextActivityReminderInteractor @Inject constructor() {

    fun execute(timeStarted: Long, now: Long, intervalMillis: Long): Long? {
        if (timeStarted < 0 || now < 0 || intervalMillis <= 0) return null
        val elapsed = (now - timeStarted).coerceAtLeast(0)
        val remaining = intervalMillis - elapsed % intervalMillis
        val base = maxOf(timeStarted, now)
        if (base > Long.MAX_VALUE - remaining) return null
        return base + remaining
    }
}

package com.example.util.simpletimetracker.domain.planning

interface PlanningReminder {
    suspend fun reschedule()
    suspend fun show(occurrenceId: String, start: Long)
}

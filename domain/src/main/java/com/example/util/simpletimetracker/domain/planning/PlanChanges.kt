package com.example.util.simpletimetracker.domain.planning

import java.util.Calendar
import java.util.UUID

object PlanChanges {
    data class Result(val schedules: List<PlanSchedule>, val exceptions: List<PlanException>)

    fun edit(original: PlanSchedule, date: String, replacement: PlanSchedule?, following: Boolean, existing: PlanException? = null): Result {
        require(date >= original.firstDate && (original.untilDate == null || date <= original.untilDate))
        if (replacement != null && following && original.repeat != PlanRepeat.NONE) require(replacement.firstDate >= date)
        if (!following && replacement == null) return Result(emptyList(), listOf(
            (existing ?: PlanException(original.id, date)).copy(status = PlanStatus.CANCELLED)))
        if (!following) return Result(emptyList(), listOf(PlanException(original.id, date,
            status = existing?.status ?: PlanStatus.OPEN,
            startMinute = replacement?.startMinute, durationMinutes = replacement?.durationMinutes,
            movedDate = replacement?.firstDate, title = replacement?.title,
            kind = replacement?.kind, remind = replacement?.remind)))
        if (date == original.firstDate) return if (replacement == null) {
            Result(listOf(original.copy(untilDate = date)), listOf(PlanException(original.id, date, PlanStatus.CANCELLED)))
        } else Result(listOf(replacement.copy(id = original.id)), emptyList())
        val previous = PlanCalendar.date(date, original.zoneId).apply { add(Calendar.DATE, -1) }
        val past = original.copy(untilDate = PlanCalendar.dateText(previous))
        val future = replacement?.copy(id = UUID.randomUUID().toString())
        return Result(listOfNotNull(past, future), emptyList())
    }
}

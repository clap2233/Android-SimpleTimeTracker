package com.example.util.simpletimetracker.domain.planning

import org.junit.Assert.*
import org.junit.Test

class PlanConflictPreviewTest {
    private fun plan(date: String, repeat: PlanRepeat = PlanRepeat.NONE) = PlanSchedule(
        title = "Reading", firstDate = date, startMinute = 540, durationMinutes = 30,
        zoneId = "Asia/Shanghai", repeat = repeat, weekdays = if (repeat == PlanRepeat.WEEKLY) listOf(1) else emptyList(),
    )

    @Test fun laterSeriesStartIsCheckedEvenYearsAfterTheCandidate() {
        val candidate = plan("2026-10-12", PlanRepeat.DAILY)
        val other = plan("2032-10-12", PlanRepeat.WEEKLY).copy(weekdays = listOf(2))
        assertTrue(PlanConflictPreview.find(candidate, listOf(other), emptyList()).isNotEmpty())
    }

    @Test fun movedInstanceOutsideItsSeriesAndCancelledInstanceKeepTheirMeaning() {
        val candidate = plan("2026-10-12")
        val other = plan("2032-10-12")
        val moved = PlanException(other.id, other.firstDate, movedDate = candidate.firstDate)
        assertEquals(listOf(moved.id), PlanConflictPreview.find(candidate, listOf(other), listOf(moved)).map { it.id })
        assertTrue(PlanConflictPreview.find(candidate, listOf(other), listOf(moved.copy(status = PlanStatus.CANCELLED))).isEmpty())
    }

    @Test fun recurrenceAfterFiniteExceptionsAndExcludedInstancesAreChecked() {
        val candidate = plan("2026-10-12", PlanRepeat.DAILY)
        val other = plan("2026-10-12", PlanRepeat.WEEKLY).copy(weekdays = listOf(1))
        val exceptions = (0..60).map { week ->
            val date = PlanCalendar.date(other.firstDate, other.zoneId).apply { add(java.util.Calendar.DATE, week * 7) }
            PlanException(other.id, PlanCalendar.dateText(date), PlanStatus.CANCELLED)
        }
        val conflicts = PlanConflictPreview.find(candidate, listOf(other), exceptions)
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts.all { it.date > exceptions.last().date })
        assertTrue(PlanConflictPreview.find(candidate, listOf(other), exceptions) { true }.isEmpty())
    }
}

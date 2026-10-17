package com.example.util.simpletimetracker.domain.planning

import org.junit.Assert.*
import org.junit.Test

class PlanningTest {
    private fun time(date: String, zone: String = "Asia/Shanghai") = PlanCalendar.date(date, zone).timeInMillis

    @Test fun movedInstanceKeepsIdentityAndOnlyOverridesItsOwnDetails() {
        val plan = PlanSchedule(title = "阅读", firstDate = "2026-10-12", startMinute = 540, durationMinutes = 30,
            zoneId = "Asia/Shanghai", repeat = PlanRepeat.DAILY, untilDate = "2026-10-14", remind = true)
        val replacement = plan.copy(title = "留白", firstDate = "2026-11-01", untilDate = null, startMinute = 1410,
            durationMinutes = 90, kind = PlanKind.BUFFER, remind = false)
        val edit = PlanChanges.edit(plan, "2026-10-13", replacement, false).exceptions.single()
        val originalWeek = PlanCalendar.occurrences(listOf(plan), listOf(edit), time("2026-10-12"), time("2026-10-15"))
        assertEquals(listOf("2026-10-12", "2026-10-14"), originalWeek.map { it.date })
        val moved = PlanCalendar.occurrences(listOf(plan), listOf(edit), time("2026-11-02"), time("2026-11-03")).single()
        assertEquals("${plan.id}@2026-10-13", moved.id)
        assertEquals("留白", moved.schedule.title)
        assertEquals(PlanKind.BUFFER, moved.schedule.kind)
        assertFalse(moved.schedule.remind)
        assertEquals(90 * 60_000L, moved.end - moved.start)
        val cancelled = PlanChanges.edit(plan, edit.date, null, false, edit).exceptions.single()
        assertEquals(edit.copy(status = PlanStatus.CANCELLED), cancelled)
        val completed = PlanChanges.edit(plan, edit.date, replacement, false, edit.copy(status = PlanStatus.DONE)).exceptions.single()
        assertEquals(PlanStatus.DONE, completed.status)
    }

    @Test fun weeklyDaysAndCrossMidnightKeepOccurrenceIdentity() {
        val plan = PlanSchedule(title = "阅读", firstDate = "2026-10-12", startMinute = 23 * 60 + 30,
            durationMinutes = 90, zoneId = "Asia/Shanghai", repeat = PlanRepeat.WEEKLY, weekdays = listOf(1, 3))
        val result = PlanCalendar.occurrences(listOf(plan), emptyList(), time("2026-10-13"), time("2026-10-16"))
        assertEquals(listOf("2026-10-12", "2026-10-14"), result.map { it.date })
        assertEquals(90 * 60_000L, result.first().end - result.first().start)
    }

    @Test fun exceptionChangesOnlyOneDayAndEndDateIsInclusive() {
        val plan = PlanSchedule(title = "缓冲", kind = PlanKind.BUFFER, firstDate = "2026-10-12", startMinute = 540,
            durationMinutes = 30, zoneId = "Asia/Shanghai", repeat = PlanRepeat.DAILY, untilDate = "2026-10-14")
        val change = PlanException(plan.id, "2026-10-13", PlanStatus.CANCELLED)
        val result = PlanCalendar.occurrences(listOf(plan), listOf(change), time("2026-10-12"), time("2026-10-16"))
        assertEquals(3, result.size)
        assertEquals(listOf(PlanStatus.OPEN, PlanStatus.CANCELLED, PlanStatus.OPEN), result.map { it.status })
    }

    @Test fun recurrenceKeepsLocalHourAcrossDst() {
        val plan = PlanSchedule(title = "散步", firstDate = "2026-10-31", startMinute = 540, durationMinutes = 30,
            zoneId = "America/New_York", repeat = PlanRepeat.DAILY)
        val result = PlanCalendar.occurrences(listOf(plan), emptyList(), time("2026-10-31", plan.zoneId), time("2026-11-03", plan.zoneId))
        assertEquals(25 * 3_600_000L, result[1].start - result[0].start)
        assertEquals(24 * 3_600_000L, result[2].start - result[1].start)
    }

    @Test fun occupiedTimeClipsAndCountsOverlapsOnce() {
        assertEquals(100L, PlanCalendar.occupiedMillis(listOf(0L to 70L, 20L to 90L, 80L to 150L), 10, 110))
    }

    @Test fun malformedDatesAndRecurrenceAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PlanTask(title = " ") }
        assertThrows(Exception::class.java) { PlanTask(title = "a", deadline = "2026-02-30") }
        assertThrows(IllegalArgumentException::class.java) {
            PlanSchedule(title = "a", firstDate = "2026-10-12", startMinute = 1440, durationMinutes = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlanSchedule(title = "a", firstDate = "2026-10-12", startMinute = 0, durationMinutes = 30, repeat = PlanRepeat.WEEKLY)
        }
    }

    @Test fun repeatedDstHourUsesLaterInstantAndSpringGapMovesForward() {
        val plan = PlanSchedule(title = "休息", firstDate = "2026-11-01", startMinute = 90, durationMinutes = 30, zoneId = "America/New_York")
        assertEquals(1793514600000L, PlanCalendar.occurrences(listOf(plan), emptyList(), time(plan.firstDate, plan.zoneId), time("2026-11-02", plan.zoneId)).single().start)
        val spring = plan.copy(firstDate = "2026-03-08", startMinute = 150)
        assertEquals(1772955000000L, PlanCalendar.occurrences(listOf(spring), emptyList(), time(spring.firstDate, spring.zoneId), time("2026-03-09", spring.zoneId)).single().start)
    }

    @Test fun followingChangeKeepsPastScheduleAndSingleChangeDoesNotMoveOtherDays() {
        val plan = PlanSchedule(title = "阅读", firstDate = "2026-10-12", startMinute = 540, durationMinutes = 30, repeat = PlanRepeat.DAILY)
        val replacement = plan.copy(firstDate = "2026-10-14", startMinute = 600)
        val one = PlanChanges.edit(plan, "2026-10-14", replacement, false)
        assertTrue(one.schedules.isEmpty())
        assertEquals(600, one.exceptions.single().startMinute)
        val future = PlanChanges.edit(plan, "2026-10-14", replacement, true)
        assertEquals(plan.copy(untilDate = "2026-10-13"), future.schedules.first())
        assertNotEquals(plan.id, future.schedules.last().id)
        assertEquals("2026-10-14", future.schedules.last().firstDate)
        assertEquals(listOf(plan.copy(untilDate = "2026-10-13")), PlanChanges.edit(plan, "2026-10-14", null, true).schedules)
    }

    @Test fun calendarExportEscapesNewlinesAndFoldsUtf8WithoutSplittingCharacters() {
        val plan = PlanSchedule(title = "=formula\r\nBEGIN:VEVENT;," + "阅读🐈".repeat(40), firstDate = "2026-10-12", startMinute = 540, durationMinutes = 30)
        val values = PlanCalendar.occurrences(listOf(plan), emptyList(), time("2026-10-12"), time("2026-10-13"))
        val ics = PlanningExport.ics(values)
        assertEquals(1, ics.split("\r\n").count { it == "BEGIN:VEVENT" })
        assertTrue(ics.split("\r\n").all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(ics.replace("\r\n ", "").contains("\\nBEGIN:VEVENT\\;\\,"))
        assertTrue(PlanningExport.csv(values).contains("\"'=formula"))
    }
}

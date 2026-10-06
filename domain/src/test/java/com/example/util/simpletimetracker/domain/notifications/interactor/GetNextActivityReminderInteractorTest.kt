package com.example.util.simpletimetracker.domain.notifications.interactor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetNextActivityReminderInteractorTest {

    private val subject = GetNextActivityReminderInteractor()
    private val interval = 15 * 60_000L
    private val start = (9 * 60 + 2) * 60_000L

    @Test
    fun `reminder follows start time instead of wall clock quarters`() {
        assertEquals(start + interval, subject.execute(start, start, interval))
    }

    @Test
    fun `rescheduling after an edit keeps original phase`() {
        assertEquals(start + interval, subject.execute(start, start + 7 * 60_000L, interval))
    }

    @Test
    fun `delayed delivery skips missed reminders without shifting phase`() {
        assertEquals(start + 4 * interval, subject.execute(start, start + 46 * 60_000L, interval))
    }

    @Test
    fun `delivery exactly on a boundary schedules the next boundary`() {
        assertEquals(start + 2 * interval, subject.execute(start, start + interval, interval))
    }

    @Test
    fun `midnight does not restart the interval`() {
        val lateStart = (23 * 60 + 58) * 60_000L
        assertEquals(lateStart + interval, subject.execute(lateStart, 24 * 60 * 60_000L, interval))
    }

    @Test
    fun `clock moving back does not schedule before the original first reminder`() {
        assertEquals(start + interval, subject.execute(start, start - 60_000L, interval))
    }

    @Test
    fun `disabled or invalid interval does not schedule`() {
        assertNull(subject.execute(start, start, 0))
        assertNull(subject.execute(start, start, -1))
    }

    @Test
    fun `invalid timestamps and overflowing imported values do not schedule`() {
        assertNull(subject.execute(-1, start, interval))
        assertNull(subject.execute(start, -1, interval))
        assertNull(subject.execute(0, Long.MAX_VALUE, interval))
        assertNull(subject.execute(Long.MAX_VALUE, start, interval))
    }
}

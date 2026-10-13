package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordTimeValidatorTest {
    private val validator = RecordTimeValidator()
    private val start = 1_700_000_000_123L
    private fun record(duration: Long, from: Long = start, state: RecordReviewState = RecordReviewState.PENDING) =
        Record(typeId = 0, timeStarted = from, timeEnded = from + duration, comment = "", tags = emptyList(), reviewState = state)

    @Test
    fun quarterHourRetainsTheOriginalOffset() {
        validator.validate(listOf(record(900_000, state = RecordReviewState.CONFIRMED)), emptyList(), emptyList(), start + 900_000)
        assertEquals(RecordReviewState.CONFIRMED, validator.state(start, start + 900_000))
    }

    @Test
    fun shortRecordRemainsPendingWithoutRounding() {
        validator.validate(listOf(record(760_123)), emptyList(), emptyList(), start + 900_000)
        assertEquals(RecordReviewState.PENDING, validator.state(start, start + 760_123))
        val error = assertThrows(RecordMutationException::class.java) {
            validator.validate(listOf(record(760_123, state = RecordReviewState.CONFIRMED)), emptyList(), emptyList(), start + 900_000)
        }
        assertEquals(RecordMutationError.NOT_QUARTER_HOUR, error.reason)
    }

    @Test
    fun adjacentHalfOpenIntervalsCanBeAdjustedTogether() {
        validator.validate(listOf(record(900_000), record(900_000, start + 900_000)), emptyList(), emptyList(), start + 1_800_000)
    }

    @Test
    fun thirdRecordAndRunningIntervalsPreventOverlap() {
        val original = record(900_000)
        assertEquals(
            RecordMutationError.OVERLAP,
            assertThrows(RecordMutationException::class.java) {
                validator.validate(listOf(original), listOf(record(100, start + 10)), emptyList(), start + 1_800_000)
            }.reason,
        )
        assertEquals(
            RecordMutationError.OVERLAP,
            assertThrows(RecordMutationException::class.java) {
                validator.validate(listOf(original), emptyList(), listOf(RunningRecord(1, start + 10, "", emptyList())), start + 1_800_000)
            }.reason,
        )
    }

    @Test
    fun futureEmptyAndOverflowingIntervalsAreRejected() {
        assertEquals(
            RecordMutationError.FUTURE_TIME,
            assertThrows(RecordMutationException::class.java) {
                validator.validate(listOf(record(900_000)), emptyList(), emptyList(), start + 899_999)
            }.reason,
        )
        assertEquals(
            RecordMutationError.EMPTY_INTERVAL,
            assertThrows(RecordMutationException::class.java) {
                validator.validate(listOf(record(0)), emptyList(), emptyList(), start)
            }.reason,
        )
        assertEquals(
            RecordMutationError.EMPTY_INTERVAL,
            assertThrows(RecordMutationException::class.java) {
                validator.validate(listOf(record(0).copy(timeStarted = Long.MIN_VALUE, timeEnded = start)), emptyList(), emptyList(), start)
            }.reason,
        )
    }
}

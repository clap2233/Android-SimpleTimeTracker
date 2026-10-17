package com.example.util.simpletimetracker.domain.review

import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.review.interactor.WeekReviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekReviewCalculatorTest {
    private val calculator = WeekReviewCalculator()
    private val zone = "Asia/Shanghai"
    private val monday = 1_791_129_600_000L

    @Test fun crossWeekTrialComparisonRejectsOverlapsAndFutureButKeepsExactPendingExperiences() {
        val data = calculator.comparisonRecords(
            listOf(
                record(1, monday, 900_000), record(2, monday + 60_000, 900_000),
                record(3, monday + 2 * 86_400_000, 760_123, false),
                record(4, monday + 8 * 86_400_000, 900_000), record(5, monday + 10 * 86_400_000, 900_000),
            ),
            monday + 9 * 86_400_000,
        )
        assertEquals(listOf(3L, 4L), data.map { it.id })
        assertEquals(760_123L, data.first().duration)
    }
    private fun record(
        id: Long,
        start: Long,
        duration: Long,
        confirmed: Boolean = true,
        type: Long = 1,
        tags: List<RecordBase.Tag> = emptyList(),
    ) = Record(
        id, type, start, start + duration, "", tags,
        if (confirmed) RecordReviewState.CONFIRMED else RecordReviewState.PENDING,
    )
    private fun stats(
        records: List<Record>,
        now: Long = monday + 86_400_000,
        running: List<RunningRecord> = emptyList(),
        people: Set<Long> = emptySet(),
        environments: Set<Long> = emptySet(),
    ) =
        calculator.calculate("2026-10-05", zone, now, records, running, 101, 102, people, environments)

    @Test fun sharedAcceptanceTimesRemainExactAndUnclassifiedTimeIsRecorded() {
        val data = stats(
            listOf(
                record(1, monday + 23 * 3_600_000 + 45 * 60_000, 1_800_000),
                record(2, monday + 25 * 3_600_000, 1_800_000, type = 2),
                record(3, monday + 26 * 3_600_000, 760_000, false),
                record(4, monday + 27 * 3_600_000, 900_000, type = 0),
                record(5, monday + 28 * 3_600_000, 900_000, type = 4),
            ),
            monday + 34 * 3_600_000, listOf(RunningRecord(4, monday + 33 * 3_600_000 + 45 * 60_000, "", emptyList())),
        )
        assertEquals(122_400_000L, data.elapsed)
        assertEquals(7_060_000L, data.recorded)
        assertEquals(115_340_000L, data.unrecorded)
        assertEquals(5_400_000L, data.confirmed)
        assertEquals(760_000L, data.pending)
        assertEquals(900_000L, data.running)
        assertEquals(900_000L, data.byType[0])
        assertEquals(900_000L, data.blocks.filter { it.day == 0 }.sumOf { it.end - it.start })
        assertEquals(4, data.happiness.unrated)
    }

    @Test fun crossingMidnightAndMultiplePurposesNeverDuplicateExperienceOrScores() {
        val data = stats(
            listOf(
                record(
                    1, monday + 23 * 3_600_000 + 45 * 60_000, 1_800_000,
                    tags = listOf(RecordBase.Tag(101, 4.0), RecordBase.Tag(102, 5.0), RecordBase.Tag(201, null), RecordBase.Tag(202, null)),
                ),
            ),
            monday + 26 * 3_600_000, people = setOf(201, 202),
        )
        assertEquals(1_800_000L, data.recorded)
        assertEquals(1, data.records.size)
        assertEquals(2, data.blocks.size)
        assertEquals(listOf(0, 0, 0, 1, 0), data.happiness.distribution)
        assertEquals(1, data.meaning.rated)
        assertEquals(0, data.meaning.unrated)
    }

    @Test fun ratingsUseFixedIdentityAndIgnoreInvalidOrPendingValues() {
        val data = stats(
            listOf(
                record(1, monday, 900_000, tags = listOf(RecordBase.Tag(999, 5.0), RecordBase.Tag(101, 3.5))),
                record(2, monday + 900_000, 900_000, tags = listOf(RecordBase.Tag(101, Double.NaN), RecordBase.Tag(102, null))),
                record(3, monday + 1_800_000, 300_000, false, tags = listOf(RecordBase.Tag(101, 5.0))),
            ),
        )
        assertEquals(0, data.happiness.rated)
        assertEquals(2, data.happiness.unrated)
    }

    @Test fun peopleAndEnvironmentFiltersUseAnyWithinEachGroupAndBothGroupsTogether() {
        val data = stats(
            listOf(
                record(1, monday, 900_000, tags = listOf(RecordBase.Tag(1, null), RecordBase.Tag(2, null), RecordBase.Tag(3, null))),
                record(2, monday + 900_000, 900_000, tags = listOf(RecordBase.Tag(1, null))),
            ),
            people = setOf(1, 2), environments = setOf(3),
        )
        assertEquals(listOf(1L), data.records.map { it.id })
        assertEquals(900_000L, data.recorded)
    }

    @Test fun overlappingHistoricalIntervalsAreSeparatedInsteadOfInflatingTotals() {
        val data = stats(
            listOf(
                record(1, monday, 3_600_000), record(2, monday + 60_000, 900_000),
                record(3, monday + 1_800_000, 900_000), record(4, monday + 3_600_000, 900_000),
            ),
        )
        assertEquals(setOf(1L, 2L, 3L), data.repairs.map { it.id }.toSet())
        assertEquals(900_000L, data.recorded)
        assertEquals(listOf(4L), data.records.map { it.id })
    }

    @Test fun invalidAndFutureIntervalsDoNotBecomeActualExperiences() {
        val data = stats(
            listOf(
                record(1, monday, 0), record(2, monday + 3_600_000, 760_000),
                record(3, monday + 85_000_000, 3_600_000), record(4, monday + 2 * 86_400_000, 900_000),
            ),
        )
        assertEquals(760_000L, data.recorded)
        assertEquals(setOf(1L, 3L), data.repairs.map { it.id }.toSet())
        assertEquals(listOf(4L), data.future.map { it.id })
    }

    @Test fun runningIsClippedAtOneCapturedCutoffAndDoesNotReceiveAnExperienceScore() {
        val data = stats(
            emptyList(), monday + 1_000_123,
            listOf(RunningRecord(1, monday + 240_000, "", listOf(RecordBase.Tag(101, 5.0)))),
        )
        assertEquals(760_123L, data.running)
        assertEquals(0, data.happiness.rated)
        assertEquals(0, data.happiness.unrated)
    }

    @Test fun anExperienceAcrossTheWeekBoundaryIsClippedButKeepsItsIdentity() {
        val data = stats(listOf(record(1, monday - 900_000, 1_800_000)))
        assertEquals(900_000L, data.confirmed)
        assertEquals(1, data.records.size)
        assertEquals(1, data.happiness.unrated)
    }

    @Test fun historicalAndFutureWeeksHaveTheirOwnElapsedLimits() {
        assertEquals(7 * 86_400_000L, stats(emptyList(), monday + 10 * 86_400_000).elapsed)
        assertEquals(0L, stats(emptyList(), monday - 1).elapsed)
        assertEquals("2026-09-28", calculator.shiftWeek("2026-10-05", zone, -1))
        assertEquals("2026-10-05", calculator.weekStart(monday + 6 * 86_400_000, zone))
    }

    @Test fun daylightSavingDaysUseRealElapsedTimeAndLocalMondayBoundaries() {
        val fall = calculator.days("2026-10-26", "America/New_York")
        val spring = calculator.days("2026-03-02", "America/New_York")
        assertEquals(25 * 3_600_000L, fall.last().end - fall.last().start)
        assertEquals(23 * 3_600_000L, spring.last().end - spring.last().start)
        val data = calculator.calculate(
            "2026-10-26", "America/New_York", fall.last().end,
            listOf(record(1, fall.last().start, 25 * 3_600_000)), emptyList(), 101, 102,
        )
        assertEquals(25 * 3_600_000L, data.confirmed)
        assertEquals(169 * 3_600_000L, data.elapsed)
        assertTrue(data.repairs.isEmpty())
    }
}

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

    @Test fun unfinishedFocusIsSeparateFromFormalTimeRatingsAndUnrecordedGaps() {
        val attempt = record(1, monday, 480_000, tags = listOf(RecordBase.Tag(101, 5.0))).copy(isUnfinishedFocus = true, unfinishedReason = "phone call")
        val formal = record(2, monday + 480_000, 1_320_000)
        val data = stats(listOf(attempt, formal))
        assertEquals(1_320_000L, data.recorded)
        assertEquals(1_320_000L, data.confirmed)
        assertEquals(480_000L, data.unfinishedFocusMs)
        assertEquals(listOf(attempt), data.unfinishedFocus)
        assertEquals(listOf(formal), data.records)
        assertEquals(0, data.happiness.rated)
        assertEquals(1, data.happiness.unrated)
        assertEquals(data.elapsed - 1_800_000L, data.unrecorded)
        assertEquals(listOf(formal), calculator.comparisonRecords(listOf(attempt, formal), monday + 86_400_000))
    }

    @Test fun unfinishedFocusClipsAtWeekBoundaryAndStillDetectsRealOverlaps() {
        val attempt = record(1, monday - 120_000, 480_000).copy(isUnfinishedFocus = true)
        assertEquals(360_000L, stats(listOf(attempt)).unfinishedFocusMs)
        val overlapping = record(2, monday, 900_000)
        val invalid = stats(listOf(attempt, overlapping))
        assertEquals(0L, invalid.recorded)
        assertTrue(invalid.unfinishedFocus.isEmpty())
        assertEquals(setOf(1L, 2L), invalid.repairs.map { it.id }.toSet())
    }

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
        assertEquals(6_160_000L, data.confirmed)
        assertEquals(0L, data.pending)
        assertEquals(900_000L, data.running)
        assertEquals(900_000L, data.byType[0])
        assertEquals(900_000L, data.blocks.filter { it.day == 0 }.sumOf { it.end - it.start })
        assertEquals(5, data.happiness.unrated)
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
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0), data.happiness.distribution)
        assertEquals(1, data.meaning.rated)
        assertEquals(0, data.meaning.unrated)
    }

    @Test fun ratingsUseFixedIdentityAndIgnoreInvalidValuesButIncludeLegacyPendingExperiences() {
        val data = stats(
            listOf(
                record(1, monday, 900_000, tags = listOf(RecordBase.Tag(999, 5.0), RecordBase.Tag(101, 3.5))),
                record(2, monday + 900_000, 900_000, tags = listOf(RecordBase.Tag(101, Double.NaN), RecordBase.Tag(102, null))),
                record(3, monday + 1_800_000, 300_000, false, tags = listOf(RecordBase.Tag(101, 5.0))),
            ),
        )
        assertEquals(1, data.happiness.rated)
        assertEquals(2, data.happiness.unrated)
    }

    @Test fun legacyPendingExactDurationCountsWithNegativeRatingsAndRecovery() {
        val record = record(
            1, monday, 760_000, false,
            tags = listOf(RecordBase.Tag(101, -5.0), RecordBase.Tag(102, -3.0)),
        ).copy(recovery = com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_TIRED)
        val data = stats(listOf(record))
        assertEquals(760_000L, data.recorded)
        assertEquals(760_000L, data.confirmedByType[record.typeId])
        assertEquals(0L, data.pending)
        assertEquals(1, data.happiness.distribution[0])
        assertEquals(1, data.meaning.distribution[2])
        assertEquals(com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_TIRED, data.records.single().recovery)
        assertEquals(RecordReviewState.PENDING, data.records.single().reviewState)
        assertTrue(data.repairs.isEmpty())
    }

    @Test fun bothRatingDistributionsKeepNegativeZeroAndMissingDistinct() {
        val data = stats(listOf(
            record(1, monday, 60_000, tags = listOf(RecordBase.Tag(101, -5.0), RecordBase.Tag(102, -5.0))),
            record(2, monday + 60_000, 60_000, tags = listOf(RecordBase.Tag(101, 0.0), RecordBase.Tag(102, 0.0))),
            record(3, monday + 120_000, 60_000, tags = listOf(RecordBase.Tag(101, null), RecordBase.Tag(102, null))),
            record(4, monday + 180_000, 60_000, tags = listOf(RecordBase.Tag(101, 5.0), RecordBase.Tag(102, 5.0))),
            record(5, monday + 240_000, 60_000, tags = listOf(RecordBase.Tag(101, -6.0), RecordBase.Tag(102, 6.0))),
        ))
        listOf(data.happiness, data.meaning).forEach { scores ->
            assertEquals(-5, scores.minimum)
            assertEquals(11, scores.distribution.size)
            assertEquals(1, scores.distribution[0])
            assertEquals(1, scores.distribution[5])
            assertEquals(1, scores.distribution[10])
            assertEquals(2, scores.unrated)
        }
        assertEquals(-5, calculator.rating(data.records.single { it.id == 1L }, 101))
        assertEquals(0, calculator.rating(data.records.single { it.id == 2L }, 101))
        org.junit.Assert.assertNull(calculator.rating(data.records.single { it.id == 3L }, 101))
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

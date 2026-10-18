package com.example.util.simpletimetracker.domain.review.interactor

import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class ReviewDay(val date: String, val start: Long, val end: Long)
enum class ReviewTimeState { CONFIRMED, RUNNING }
data class ReviewTimeBlock(
    val id: Long,
    val typeId: Long,
    val start: Long,
    val end: Long,
    val state: ReviewTimeState,
    val day: Int,
)
data class ReviewScores(val distribution: List<Int>, val unrated: Int, val minimum: Int = -5) {
    val rated: Int get() = distribution.sum()
}
data class WeekReviewStats(
    val days: List<ReviewDay>,
    val elapsed: Long,
    val recorded: Long,
    val confirmed: Long,
    val pending: Long,
    val running: Long,
    val byType: Map<Long, Long>,
    val confirmedByType: Map<Long, Long>,
    val blocks: List<ReviewTimeBlock>,
    val records: List<Record>,
    val repairs: List<Record>,
    val future: List<Record>,
    val happiness: ReviewScores,
    val meaning: ReviewScores,
    val unfinishedFocus: List<Record> = emptyList(),
    val unfinishedFocusMs: Long = 0,
) {
    val unrecorded: Long get() = elapsed - recorded - unfinishedFocusMs
}

class WeekReviewCalculator @Inject constructor() {
    fun weekStart(timestamp: Long, zoneId: String): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = timestamp }
        calendar.add(Calendar.DAY_OF_MONTH, -((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        return format(zoneId).format(calendar.time)
    }

    fun shiftWeek(weekStart: String, zoneId: String, amount: Int): String {
        val calendar = calendar(weekStart, zoneId)
        calendar.add(Calendar.DAY_OF_MONTH, amount * 7)
        return format(zoneId).format(calendar.time)
    }

    fun days(weekStart: String, zoneId: String): List<ReviewDay> {
        val calendar = calendar(weekStart, zoneId)
        return (0..6).map {
            val date = format(zoneId).format(calendar.time)
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            ReviewDay(date, start, calendar.timeInMillis)
        }
    }

    fun calculate(
        weekStart: String,
        zoneId: String,
        now: Long,
        records: List<Record>,
        runningRecords: List<RunningRecord>,
        happinessId: Long,
        meaningId: Long,
        people: Set<Long> = emptySet(),
        environments: Set<Long> = emptySet(),
    ): WeekReviewStats {
        val days = days(weekStart, zoneId)
        val start = days.first().start
        val end = days.last().end
        val cutoff = now.coerceIn(start, end)
        val weekRecords = records.filter { it.timeStarted < end && (it.timeEnded > start || it.timeStarted >= start) }
        val future = weekRecords.filter { it.timeStarted >= cutoff && it.timeEnded > it.timeStarted }
        val repairs = weekRecords.filter {
            it.timeStarted < cutoff && (
                it.timeEnded <= it.timeStarted || it.timeEnded > now
                )
        }.mapTo(mutableSetOf()) { it.id }
        val active = runningRecords.filter { it.timeStarted < cutoff }
        data class Interval(val id: Long, val start: Long, val end: Long, val running: Boolean)
        val intervals = (
            weekRecords.filter { it.timeStarted < cutoff && it.timeEnded > it.timeStarted }.map {
                Interval(it.id, it.timeStarted, it.timeEnded, false)
            } + active.map { Interval(it.id, it.timeStarted, now, true) }
            ).sortedBy { it.start }
        val badRunning = mutableSetOf<Long>()
        var furthest: Interval? = null
        fun mark(interval: Interval) {
            if (interval.running) badRunning.add(interval.id) else repairs.add(interval.id)
        }
        intervals.forEach { interval ->
            furthest?.takeIf { it.end > interval.start }?.let {
                mark(it)
                mark(interval)
            }
            if (furthest == null || interval.end > requireNotNull(furthest).end) furthest = interval
        }
        fun matches(tags: List<RecordBase.Tag>): Boolean =
            (people.isEmpty() || tags.any { it.tagId in people }) &&
                (environments.isEmpty() || tags.any { it.tagId in environments })
        val usable = weekRecords.filter {
            it.id !in repairs && it.timeStarted < cutoff && it.timeEnded > start && matches(it.tags)
        }.distinctBy { it.id }
        val selected = usable.filterNot { it.isUnfinishedFocus }
        val unfinished = usable.filter { it.isUnfinishedFocus }.sortedByDescending { it.timeStarted }
        val blocks = mutableListOf<ReviewTimeBlock>()
        fun add(id: Long, typeId: Long, from: Long, to: Long, state: ReviewTimeState) {
            days.forEachIndexed { day, range ->
                val a = maxOf(from, range.start)
                val b = minOf(to, range.end, cutoff)
                if (a < b) blocks.add(ReviewTimeBlock(id, typeId, a, b, state, day))
            }
        }
        selected.forEach {
            add(
                it.id, it.typeId, it.timeStarted, it.timeEnded,
                ReviewTimeState.CONFIRMED,
            )
        }
        active.filter { it.id !in badRunning && matches(it.tags) }.forEach {
            add(it.id, it.id, it.timeStarted, cutoff, ReviewTimeState.RUNNING)
        }
        fun scores(id: Long, minimum: Int): ReviewScores {
            val values = selected.map { rating(it, id, minimum) }
            return ReviewScores((minimum..5).map { score -> values.count { it == score } }, values.count { it == null }, minimum)
        }
        fun duration(state: ReviewTimeState) = blocks.filter { it.state == state }.sumOf { it.end - it.start }
        fun grouped(data: List<ReviewTimeBlock>) = data.groupBy { it.typeId }.mapValues { (_, values) -> values.sumOf { it.end - it.start } }
        return WeekReviewStats(
            days, cutoff - start, blocks.sumOf { it.end - it.start },
            duration(ReviewTimeState.CONFIRMED), 0L, duration(ReviewTimeState.RUNNING),
            grouped(blocks), grouped(blocks.filter { it.state == ReviewTimeState.CONFIRMED }),
            blocks, selected.sortedByDescending { it.timeStarted }, weekRecords.filter { it.id in repairs }, future,
            scores(happinessId, -5), scores(meaningId, -5),
            unfinished, unfinished.sumOf { minOf(it.timeEnded, cutoff) - maxOf(it.timeStarted, start) },
        )
    }

    fun rating(record: Record, id: Long, minimum: Int = -5): Int? = record.tags.firstOrNull { it.tagId == id }?.numericValue
        ?.takeIf { it.isFinite() && it in minimum.toDouble()..5.0 && it % 1.0 == 0.0 }?.toInt()

    fun comparisonRecords(records: List<Record>, now: Long): List<Record> {
        val overlaps = mutableSetOf<Long>()
        var furthest: Record? = null
        records.filter { it.timeEnded > it.timeStarted }.sortedBy { it.timeStarted }.forEach { record ->
            furthest?.takeIf { it.timeEnded > record.timeStarted }?.let {
                overlaps.add(it.id)
                overlaps.add(record.id)
            }
            if (furthest == null || record.timeEnded > requireNotNull(furthest).timeEnded) furthest = record
        }
        return records.filter {
            !it.isUnfinishedFocus && it.id !in overlaps && it.duration > 0 && it.timeEnded <= now
        }
    }

    private fun format(zoneId: String) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
        isLenient = false
    }

    private fun calendar(date: String, zoneId: String) = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
        time = requireNotNull(format(zoneId).parse(date))
    }
}

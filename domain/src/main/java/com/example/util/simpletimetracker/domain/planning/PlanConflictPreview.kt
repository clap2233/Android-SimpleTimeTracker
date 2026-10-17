package com.example.util.simpletimetracker.domain.planning

import java.util.Calendar

object PlanConflictPreview {
    fun find(
        candidate: PlanSchedule,
        schedules: List<PlanSchedule>,
        exceptions: List<PlanException>,
        excluded: (PlanOccurrence) -> Boolean = { false },
    ): List<PlanOccurrence> {
        val result = linkedMapOf<String, PlanOccurrence>()
        schedules.forEach { other ->
            val relevant = exceptions.filter { it.scheduleId == other.id }
            val starts = linkedSetOf(maxOf(
                PlanCalendar.date(candidate.firstDate, candidate.zoneId).timeInMillis,
                PlanCalendar.date(other.firstDate, other.zoneId).timeInMillis,
            ))
            relevant.forEach {
                starts += PlanCalendar.date(it.movedDate ?: it.date, other.zoneId).timeInMillis
            }
            // Inspect the regular recurrence again after the last finite exception.
            relevant.maxByOrNull { it.date }?.let {
                starts += PlanCalendar.date(it.date, other.zoneId).apply { add(Calendar.DATE, 1) }.timeInMillis
            }
            val span = 370L * 86_400_000
            val ranges = mutableListOf<Pair<Long, Long>>()
            starts.sorted().forEach { start ->
                val previous = ranges.lastOrNull()
                if (previous != null && start <= previous.second) ranges[ranges.lastIndex] = previous.first to maxOf(previous.second, start + span)
                else ranges += start to start + span
            }
            val windows = ranges.flatMap { (start, end) ->
                buildList {
                    var cursor = start
                    while (cursor < end) {
                        val next = minOf(end, cursor + span)
                        add(cursor to next)
                        cursor = next
                    }
                }
            }
            windows.forEach { (from, until) ->
                val proposed = PlanCalendar.occurrences(listOf(candidate), emptyList(), from, until)
                if (proposed.isNotEmpty()) {
                    PlanCalendar.occurrences(listOf(other), relevant, from, until).filter { item ->
                        item.status != PlanStatus.CANCELLED && item.status != PlanStatus.SKIPPED && !excluded(item) &&
                            proposed.any { it.start < item.end && it.end > item.start }
                    }.forEach { result[it.id] = it }
                }
            }
        }
        return result.values.sortedBy { it.start }
    }
}

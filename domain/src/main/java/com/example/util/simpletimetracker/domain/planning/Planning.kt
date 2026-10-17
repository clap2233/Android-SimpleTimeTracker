package com.example.util.simpletimetracker.domain.planning

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class PlanTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val templateId: Long? = null,
    val important: Boolean? = null,
    val urgent: Boolean? = null,
    val deadline: String? = null,
    val status: PlanStatus = PlanStatus.OPEN,
    val archived: Boolean = false,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(title.isNotBlank())
        require(templateId == null || templateId > 0)
        deadline?.let { PlanCalendar.date(it, "UTC") }
    }
}

enum class PlanStatus { OPEN, DONE, SKIPPED, CANCELLED }
enum class PlanKind { ACTIVITY, FREE, BUFFER }
enum class PlanRepeat { NONE, DAILY, WEEKLY }

data class PlanSchedule(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val title: String,
    val kind: PlanKind = PlanKind.ACTIVITY,
    val firstDate: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val zoneId: String = TimeZone.getDefault().id,
    val repeat: PlanRepeat = PlanRepeat.NONE,
    val weekdays: List<Int> = emptyList(),
    val untilDate: String? = null,
    val remind: Boolean = false,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        taskId?.let { require(UUID.fromString(it).toString() == it) }
        require(title.isNotBlank())
        require(startMinute in 0..1439 && durationMinutes in 1..1440)
        PlanCalendar.date(firstDate, zoneId)
        untilDate?.let { PlanCalendar.date(it, zoneId); require(it >= firstDate) }
        require(weekdays.distinct().size == weekdays.size && weekdays.all { it in 1..7 })
        require(if (repeat == PlanRepeat.WEEKLY) weekdays.isNotEmpty() else weekdays.isEmpty())
    }
}

data class PlanException(
    val scheduleId: String,
    val date: String,
    val status: PlanStatus = PlanStatus.OPEN,
    val startMinute: Int? = null,
    val durationMinutes: Int? = null,
    val movedDate: String? = null,
    val title: String? = null,
    val kind: PlanKind? = null,
    val remind: Boolean? = null,
) {
    val id: String get() = "$scheduleId@$date"
    init {
        require(UUID.fromString(scheduleId).toString() == scheduleId)
        PlanCalendar.date(date, "UTC")
        require(startMinute == null || startMinute in 0..1439)
        require(durationMinutes == null || durationMinutes in 1..1440)
        movedDate?.let { PlanCalendar.date(it, "UTC") }
        require(title == null || title.isNotBlank())
    }
}

data class PlanOccurrence(
    val id: String,
    val schedule: PlanSchedule,
    val date: String,
    val start: Long,
    val end: Long,
    val status: PlanStatus,
)

data class PlanRecordLink(
    val id: String = UUID.randomUUID().toString(),
    val recordId: Long? = null,
    val runningRecordId: Long? = null,
    val occurrenceId: String,
    val caption: String,
    val timeStarted: Long,
    val timeEnded: Long? = null,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        require((recordId != null) xor (runningRecordId != null))
        require(recordId == null || recordId > 0)
        require(runningRecordId == null || runningRecordId > 0)
        val parts = occurrenceId.split('@')
        require(parts.size == 2 && UUID.fromString(parts[0]).toString() == parts[0])
        PlanCalendar.date(parts[1], "UTC")
        require(timeEnded == null || timeEnded > timeStarted)
    }
}

object PlanCalendar {
    fun date(value: String, zoneId: String): Calendar {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        val zone = TimeZone.getTimeZone(zoneId)
        require(zoneId in TimeZone.getAvailableIDs() || zone.id == zoneId)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone; isLenient = false }
        return Calendar.getInstance(zone).apply { time = requireNotNull(format.parse(value)) }
    }

    fun dateText(calendar: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        .apply { timeZone = calendar.timeZone }.format(calendar.time)

    fun occurrences(schedules: List<PlanSchedule>, exceptions: List<PlanException>, from: Long, until: Long): List<PlanOccurrence> {
        require(until > from && until - from <= 370L * 86_400_000)
        val overrides = exceptions.associateBy { it.id }
        return schedules.flatMap { schedule ->
            val day = Calendar.getInstance(TimeZone.getTimeZone(schedule.zoneId)).apply {
                timeInMillis = from; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DATE, -1)
            }
            buildList {
                val dates = linkedSetOf<String>()
                while (day.timeInMillis < until) {
                    dates += dateText(day)
                    day.add(Calendar.DATE, 1)
                }
                // A moved instance may originate outside the requested calendar range.
                exceptions.filter { it.scheduleId == schedule.id && it.movedDate != null }.forEach { dates += it.date }
                dates.forEach { text ->
                    val sourceDay = date(text, schedule.zoneId)
                    val weekday = (sourceDay.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
                    val included = text >= schedule.firstDate && (schedule.untilDate == null || text <= schedule.untilDate) && when (schedule.repeat) {
                        PlanRepeat.NONE -> text == schedule.firstDate
                        PlanRepeat.DAILY -> true
                        PlanRepeat.WEEKLY -> weekday in schedule.weekdays
                    }
                    if (included) {
                        val id = "${schedule.id}@$text"
                        val override = overrides[id]
                        val minute = override?.startMinute ?: schedule.startMinute
                        val start = date(override?.movedDate ?: text, schedule.zoneId).apply {
                            set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60)
                        }.timeInMillis
                        val end = start + (override?.durationMinutes ?: schedule.durationMinutes) * 60_000L
                        val effective = schedule.copy(title = override?.title ?: schedule.title,
                            kind = override?.kind ?: schedule.kind, remind = override?.remind ?: schedule.remind)
                        if (start < until && end > from) add(PlanOccurrence(id, effective, text, start, end, override?.status ?: PlanStatus.OPEN))
                    }
                }
            }
        }.sortedBy { it.start }
    }

    fun occupiedMillis(intervals: List<Pair<Long, Long>>, from: Long, until: Long): Long {
        var end = from
        var total = 0L
        intervals.filter { it.second > from && it.first < until }.sortedBy { it.first }.forEach { (a, b) ->
            val start = maxOf(from, end, a)
            val finish = minOf(until, b)
            if (finish > start) total += finish - start
            end = maxOf(end, finish)
        }
        return total
    }
}

interface PlanningRepo {
    suspend fun clear()
    suspend fun tasks(): List<PlanTask>
    suspend fun schedules(): List<PlanSchedule>
    suspend fun exceptions(): List<PlanException>
    suspend fun links(): List<PlanRecordLink>
    suspend fun save(task: PlanTask)
    suspend fun save(schedule: PlanSchedule)
    suspend fun save(exception: PlanException)
    suspend fun save(link: PlanRecordLink)
    suspend fun editSchedule(id: String, date: String, replacement: PlanSchedule?, following: Boolean)
}

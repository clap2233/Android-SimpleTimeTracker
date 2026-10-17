package com.example.util.simpletimetracker.domain.planning

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PlanningExport {
    fun csv(occurrences: List<PlanOccurrence>): String = buildString {
        append("plan_instance,title,kind,status,start,end,time_zone\r\n")
        occurrences.forEach { p ->
            append(listOf(p.id, p.schedule.title, p.schedule.kind.name, p.status.name, utc(p.start), utc(p.end), p.schedule.zoneId)
                .joinToString(",", transform = ::cell))
            append("\r\n")
        }
    }

    fun ics(occurrences: List<PlanOccurrence>): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Life Clock//Plans//ZH\r\n")
        occurrences.forEach { p ->
            append("BEGIN:VEVENT\r\nUID:${p.id}@life-clock-plan\r\nDTSTAMP:${utc(System.currentTimeMillis())}\r\n")
            append("DTSTART:${utc(p.start)}\r\nDTEND:${utc(p.end)}\r\n")
            append("SUMMARY:${escape("[计划] " + p.schedule.title)}\r\n")
            append("DESCRIPTION:${escape("计划不代表实际经历；${p.schedule.kind}；${p.status}")}\r\n")
            append("STATUS:${if (p.status == PlanStatus.CANCELLED) "CANCELLED" else "TENTATIVE"}\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }.split("\r\n").joinToString("\r\n", transform = ::fold)

    private fun utc(value: Long) = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(value))
    private fun escape(value: String) = value.filter { it == '\t' || it == '\r' || it == '\n' || (it.code >= 32 && it.code != 127) }
        .replace("\\", "\\\\").replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun fold(line: String): String = buildString {
        var bytes = 0
        var index = 0
        while (index < line.length) {
            val point = line.codePointAt(index)
            val text = String(Character.toChars(point))
            val length = text.toByteArray(Charsets.UTF_8).size
            if (bytes + length > 75) { append("\r\n "); bytes = 1 }
            append(text)
            bytes += length
            index += Character.charCount(point)
        }
    }
}

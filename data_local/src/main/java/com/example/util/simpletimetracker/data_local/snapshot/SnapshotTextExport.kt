package com.example.util.simpletimetracker.data_local.snapshot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object SnapshotTextExport {
    private fun records(root: JsonObject): List<JsonObject> = SnapshotJson.objects(SnapshotJson.array(root, "records")) +
        SnapshotJson.objects(SnapshotJson.array(root, "runningRecords")).map {
            it.deepCopy().apply {
                addProperty("typeId", SnapshotJson.text(it, "id"))
                addProperty("id", "running:${SnapshotJson.text(it, "id")}")
                addProperty("timeEnded", SnapshotJson.number(root, "createdAt"))
                addProperty("reviewState", "RUNNING")
            }
        }

    fun csv(root: JsonObject): String {
        val tags = SnapshotJson.objects(SnapshotJson.array(root, "tags")).associateBy { SnapshotJson.text(it, "id") }
        val types = SnapshotJson.objects(SnapshotJson.array(root, "recordTypes")).associateBy { SnapshotJson.text(it, "id") }
        val ratings = requireNotNull(root.getAsJsonObject("ratingTagIds"))
        val photos = SnapshotJson.objects(SnapshotJson.array(root, "photos"))
        val repair = SnapshotValidation.repairIds(root).map(Long::toString).toSet()
        fun cell(value: String): String {
            val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        fun score(row: JsonObject, name: String, field: String): String = SnapshotJson.objects(SnapshotJson.array(row, "tags"))
            .firstOrNull { SnapshotJson.text(it, "tagId") == SnapshotJson.text(ratings, name) }?.get(field)?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty()
        return buildString {
            append("dataSetId,recordId,typeId,activity,timeStarted,timeEnded,durationMs,zoneId,state,summary,comment,people,environment,tags,happiness,meaning,happinessRatedAt,meaningRatedAt,photoCount,recovery,combined,isUnfinishedFocus,unfinishedReason\r\n")
            records(root).sortedBy { SnapshotJson.number(it, "timeStarted") }.forEach { row ->
                val id = SnapshotJson.text(row, "id")
                val started = SnapshotJson.number(row, "timeStarted")
                val ended = SnapshotJson.number(row, "timeEnded")
                val selected = SnapshotJson.objects(SnapshotJson.array(row, "tags"))
                fun names(purpose: String?) = selected.mapNotNull { tag -> tags[SnapshotJson.text(tag, "tagId")]?.takeIf { purpose == null || SnapshotJson.text(it, "purpose", "GENERAL") == purpose }?.let { SnapshotJson.text(it, "name") } }.joinToString(" | ")
                val count = if (id.startsWith("running:")) {
                    photos.count { it.get("runningRecordId")?.takeUnless(JsonElement::isJsonNull)?.asString == id.removePrefix("running:") }
                } else {
                    photos.count { it.get("recordId")?.takeUnless(JsonElement::isJsonNull)?.asString == id }
                }
                val values = listOf(
                    SnapshotJson.text(root, "dataSetId"), id, SnapshotJson.text(row, "typeId"), types[SnapshotJson.text(row, "typeId")]?.let { SnapshotJson.text(it, "name") }.orEmpty(),
                    started.toString(), ended.toString(), (ended - started).toString(), row.get("zoneId")?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty(),
                    if (id in repair) "NEEDS_REPAIR" else state(row), SnapshotJson.text(row, "summary", ""), SnapshotJson.text(row, "comment", ""),
                    names("PERSON"), names("ENVIRONMENT"), names(null), score(row, "happinessTagId", "numericValue"), score(row, "meaningTagId", "numericValue"),
                    score(row, "happinessTagId", "ratedAt"), score(row, "meaningTagId", "ratedAt"), count.toString(),
                    row.get("recovery")?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty(),
                    score(row, "happinessTagId", "numericValue").toDoubleOrNull()?.let { h -> score(row, "meaningTagId", "numericValue").toDoubleOrNull()?.let { m -> (h + m).toInt().toString() } }.orEmpty(),
                    SnapshotJson.bool(row, "isUnfinishedFocus").toString(), row.get("unfinishedReason")?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty(),
                )
                append(values.joinToString(",", transform = ::cell))
                append("\r\n")
            }
        }
    }

    fun ics(root: JsonObject): String {
        val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun format(time: Long): String = date.format(Date(time)).also { require(it.length == 16) }
        fun escape(text: String) = text.filter { it == '\t' || it == '\r' || it == '\n' || (it.code >= 32 && it.code != 127) }
            .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n")
        val types = SnapshotJson.objects(SnapshotJson.array(root, "recordTypes")).associateBy { SnapshotJson.text(it, "id") }
        val definitions = SnapshotJson.objects(SnapshotJson.array(root, "tags")).associateBy { SnapshotJson.text(it, "id") }
        val repair = SnapshotValidation.repairIds(root).map(Long::toString).toSet()
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Life Time Clock//Records//EN", "CALSCALE:GREGORIAN")
        val photos = SnapshotJson.objects(SnapshotJson.array(root, "photos"))
        records(root).filter { SnapshotJson.number(it, "timeEnded") > SnapshotJson.number(it, "timeStarted") && SnapshotJson.text(it, "id") !in repair }.forEach { row ->
            val context = SnapshotJson.objects(SnapshotJson.array(row, "tags")).mapNotNull { tag ->
                definitions[SnapshotJson.text(tag, "tagId")]?.let { definition ->
                    SnapshotJson.text(definition, "name") + (tag.get("numericValue")?.takeUnless(JsonElement::isJsonNull)?.let { ": ${it.asString}" } ?: "") +
                        (tag.get("ratedAt")?.takeUnless(JsonElement::isJsonNull)?.let { " (ratedAt=${it.asString})" } ?: "")
                }
            }
            val activity = types[SnapshotJson.text(row, "typeId")]?.let { SnapshotJson.text(it, "name") }.orEmpty()
            val recordId = SnapshotJson.text(row, "id")
            val unfinished = SnapshotJson.bool(row, "isUnfinishedFocus")
            val photoCount = photos.count { photo ->
                if (recordId.startsWith("running:")) {
                    photo.get("runningRecordId")?.takeUnless(JsonElement::isJsonNull)?.asString == recordId.removePrefix("running:")
                } else {
                    photo.get("recordId")?.takeUnless(JsonElement::isJsonNull)?.asString == recordId
                }
            }
            val description = (
                listOf(SnapshotJson.text(row, "summary", ""), SnapshotJson.text(row, "comment", "")) + context +
                    listOf("state=${state(row)}", "zoneId=${row.get("zoneId")?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty()}", "photos=$photoCount") +
                    listOfNotNull(row.get("unfinishedReason")?.takeUnless(JsonElement::isJsonNull)?.asString?.let { "unfinishedReason=$it" })
                )
                .filter(String::isNotEmpty).joinToString("\n")
            lines += listOf(
                "BEGIN:VEVENT", "UID:${SnapshotJson.text(root, "dataSetId")}-${SnapshotJson.text(row, "id")}@life-clock",
                "DTSTAMP:${format(SnapshotJson.number(root, "createdAt"))}", "DTSTART:${format(SnapshotJson.number(row, "timeStarted"))}", "DTEND:${format(SnapshotJson.number(row, "timeEnded"))}",
                "SUMMARY:${escape((if (unfinished) "[未完成专注] " else "") + SnapshotJson.text(row, "summary", "").ifEmpty { activity })}",
                "DESCRIPTION:${escape(description)}", "X-LIFE-CLOCK-STATE:${state(row)}",
            )
            if (unfinished) lines += "TRANSP:TRANSPARENT"
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n", transform = ::fold)
    }

    private fun state(row: JsonObject): String = when {
        SnapshotJson.text(row, "id").startsWith("running:") -> "RUNNING"
        SnapshotJson.bool(row, "isUnfinishedFocus") -> "UNFINISHED_FOCUS"
        else -> "RECORDED"
    }

    private fun fold(line: String): String = buildString {
        var bytes = 0
        var index = 0
        while (index < line.length) {
            val point = line.codePointAt(index)
            val text = String(Character.toChars(point))
            val length = text.toByteArray(Charsets.UTF_8).size
            if (bytes + length > 75) {
                append("\r\n ")
                bytes = 1
            }
            append(text)
            bytes += length
            index += Character.charCount(point)
        }
    }
}

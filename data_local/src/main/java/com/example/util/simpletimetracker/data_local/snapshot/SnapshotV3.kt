package com.example.util.simpletimetracker.data_local.snapshot

import com.example.util.simpletimetracker.data_local.planning.PlanningJson
import com.example.util.simpletimetracker.domain.rhythm.Recovery
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object SnapshotV3 {
    val tables = listOf("planTasks", "planSchedules", "planExceptions", "planRecordLinks")
    private val longIds = listOf("templateId", "recordId", "runningRecordId")

    fun decorate(root: JsonObject, native: Map<String, JsonArray>) {
        listOf("records" to "recovery", "recordTypes" to "timerPolicy", "templates" to "timerPolicy", "runningRecords" to "timerSession").forEach { (name, field) ->
            val table = if (name == "templates") "recordShortcuts" else name
            val column = when (field) { "timerPolicy" -> "timer_policy"; "timerSession" -> "timer_session"; else -> field }
            val rows = SnapshotJson.objects(native[table] ?: JsonArray()).associateBy { it.text("id") }
            SnapshotJson.objects(SnapshotJson.array(root, name)).forEach { public ->
                rows[public.text("id")]?.get(column)?.takeUnless(JsonElement::isJsonNull)?.let {
                    public.add(field, if (field == "recovery") it.deepCopy() else SnapshotJson.parse(it.asString))
                }
            }
        }
        tables.forEach { table ->
            root.add(table, JsonArray().apply {
                SnapshotJson.objects(native[table] ?: JsonArray()).forEach { row ->
                    add(SnapshotJson.parse(row.text("content_json")).apply {
                        addProperty("id", row.text("id"))
                        longIds.forEach { key -> get(key)?.takeUnless(JsonElement::isJsonNull)?.let { addProperty(key, it.asString) } }
                    })
                }
            })
        }
    }

    fun decode(root: JsonObject, result: Map<String, JsonArray>) {
        listOf("records" to "recovery", "recordTypes" to "timerPolicy", "templates" to "timerPolicy", "runningRecords" to "timerSession").forEach { (name, field) ->
            val table = if (name == "templates") "recordShortcuts" else name
            val column = when (field) { "timerPolicy" -> "timer_policy"; "timerSession" -> "timer_session"; else -> field }
            val rows = SnapshotJson.objects(result.getValue(table)).associateBy { it.text("id") }
            SnapshotJson.objects(SnapshotJson.array(root, name)).forEach { public ->
                public.get(field)?.takeUnless(JsonElement::isJsonNull)?.let {
                    rows.getValue(public.text("id")).addProperty(column, if (field == "recovery") it.asString else it.toString())
                }
            }
        }
        tables.forEach { table ->
            SnapshotJson.objects(SnapshotJson.array(root, table)).forEach { row ->
                result.getValue(table).add(SnapshotJson.obj("id" to row.text("id"), "content_json" to row.toString()))
            }
        }
    }

    fun validate(root: JsonObject) {
        fun rows(name: String) = SnapshotJson.objects(SnapshotJson.array(root, name))
        val version = SnapshotJson.number(root, "schemaVersion")
        if (version == 1L) {
            require(tables.all { rows(it).isEmpty() })
            require(rows("records").all { !it.has("recovery") })
            require((rows("templates") + rows("recordTypes")).all { !it.has("timerPolicy") })
            require(rows("runningRecords").all { !it.has("timerSession") })
            return
        }
        rows("records").forEach { row -> row.get("recovery")?.takeUnless(JsonElement::isJsonNull)?.let { Recovery.valueOf(SnapshotJson.text(row, "recovery")) } }
        (rows("recordTypes") + rows("templates")).forEach { row -> row.get("timerPolicy")?.takeUnless(JsonElement::isJsonNull)?.let { policy(it.asJsonObject) } }
        rows("runningRecords").forEach { row -> row.get("timerSession")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let {
            require(it.keySet().all { key -> key in setOf("id", "startedAt", "policy", "muted", "focusNotified") })
            require(it.has("muted") && it.has("focusNotified"))
            require(SnapshotJson.number(it, "startedAt") in 0..SnapshotJson.number(root, "createdAt"))
            SnapshotJson.bool(it, "muted"); SnapshotJson.bool(it, "focusNotified")
            policy(it.getAsJsonObject("policy"))
            PlanningJson.session(it.toString())
        } }
        tables.forEach { table -> require(rows(table).map { it.text("id") }.distinct().size == rows(table).size) }
        val tasks = rows("planTasks").map {
            keys(it, "id", "title", "note", "templateId", "important", "urgent", "deadline", "status", "archived")
            listOf("id", "title", "note", "status").forEach { key -> SnapshotJson.text(it, key) }
            com.example.util.simpletimetracker.domain.planning.PlanStatus.valueOf(it.text("status"))
            require(it.has("archived") && !it["archived"].isJsonNull)
            it.get("deadline")?.takeUnless(JsonElement::isJsonNull)?.let { _ -> SnapshotJson.text(it, "deadline") }
            listOf("important", "urgent", "archived").forEach { key -> if (it.has(key) && !it[key].isJsonNull) SnapshotJson.bool(it, key) }
            SnapshotJson.nullableId(it, "templateId")
            PlanningJson.task(it.toString()).id
        }.toSet()
        val schedules = rows("planSchedules").map {
            keys(it, "id", "taskId", "title", "kind", "firstDate", "startMinute", "durationMinutes", "zoneId", "repeat", "weekdays", "untilDate", "remind")
            listOf("id", "title", "kind", "firstDate", "zoneId", "repeat").forEach { key -> SnapshotJson.text(it, key) }
            listOf("taskId", "untilDate").forEach { key -> it.get(key)?.takeUnless(JsonElement::isJsonNull)?.let { _ -> SnapshotJson.text(it, key) } }
            com.example.util.simpletimetracker.domain.planning.PlanKind.valueOf(it.text("kind"))
            com.example.util.simpletimetracker.domain.planning.PlanRepeat.valueOf(it.text("repeat"))
            SnapshotJson.array(it, "weekdays").forEach { day -> require(day.isJsonPrimitive && day.asJsonPrimitive.isNumber && day.asBigDecimal.intValueExact() in 1..7) }
            require(it.has("remind"))
            SnapshotJson.number(it, "startMinute"); SnapshotJson.number(it, "durationMinutes"); SnapshotJson.bool(it, "remind")
            val value = PlanningJson.schedule(it.toString())
            require(value.taskId == null || value.taskId in tasks)
            value.id
        }.toSet()
        rows("planExceptions").forEach {
            keys(it, "id", "scheduleId", "date", "status", "startMinute", "durationMinutes", "movedDate", "title", "kind", "remind")
            listOf("id", "scheduleId", "date", "status").forEach { key -> SnapshotJson.text(it, key) }
            com.example.util.simpletimetracker.domain.planning.PlanStatus.valueOf(it.text("status"))
            listOf("startMinute", "durationMinutes").forEach { key -> if (it.has(key) && !it[key].isJsonNull) SnapshotJson.number(it, key) }
            listOf("movedDate", "title", "kind").forEach { key -> if (it.has(key) && !it[key].isJsonNull) SnapshotJson.text(it, key) }
            if (it.has("kind") && !it["kind"].isJsonNull) com.example.util.simpletimetracker.domain.planning.PlanKind.valueOf(it.text("kind"))
            if (it.has("remind") && !it["remind"].isJsonNull) SnapshotJson.bool(it, "remind")
            val value = PlanningJson.exception(it.toString())
            require(value.scheduleId in schedules && value.id == it.text("id"))
        }
        val records = rows("records").associateBy { SnapshotJson.id(it, "id") }
        val running = rows("runningRecords").associateBy { SnapshotJson.id(it, "id") }
        rows("planRecordLinks").forEach {
            keys(it, "id", "recordId", "runningRecordId", "occurrenceId", "caption", "timeStarted", "timeEnded")
            listOf("id", "occurrenceId", "caption").forEach { key -> SnapshotJson.text(it, key) }
            SnapshotJson.nullableId(it, "recordId"); SnapshotJson.nullableId(it, "runningRecordId")
            val value = PlanningJson.link(it.toString())
            val owner = if (value.recordId != null) records[value.recordId] else running[value.runningRecordId]
            requireNotNull(owner)
            val end = if (value.recordId != null) SnapshotJson.number(owner, "timeEnded") else SnapshotJson.number(root, "createdAt")
            require(SnapshotJson.number(it, "timeStarted") >= SnapshotJson.number(owner, "timeStarted") && value.timeStarted <= end)
            require(if (value.recordId != null) value.timeEnded != null else value.timeEnded == null)
            value.timeEnded?.let { finish -> require(SnapshotJson.number(it, "timeEnded") <= end && finish > value.timeStarted) }
        }
    }

    private fun policy(value: JsonObject) {
        keys(value, "mode", "intervalMinutes")
        SnapshotJson.text(value, "mode"); SnapshotJson.number(value, "intervalMinutes")
        PlanningJson.policy(value.toString())
    }

    private fun keys(value: JsonObject, vararg names: String) { require(value.keySet().all { it in names }) }
}

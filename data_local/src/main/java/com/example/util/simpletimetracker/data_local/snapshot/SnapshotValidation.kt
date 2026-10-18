package com.example.util.simpletimetracker.data_local.snapshot

import com.example.util.simpletimetracker.domain.backup.model.FullBackupException
import com.example.util.simpletimetracker.domain.backup.model.FullBackupFailure
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.TimeZone
import java.util.UUID

internal object SnapshotValidation {
    private const val MAX_SAFE_TIME = 9_007_199_254_740_991L

    fun validate(root: JsonObject): Set<Long> {
        val schemaVersion = SnapshotJson.number(root, "schemaVersion")
        if (schemaVersion !in 1L..4L) throw FullBackupException(FullBackupFailure.UNSUPPORTED_VERSION)
        val defaultsVersion = if (root.has("activityDefaultsVersion")) SnapshotJson.number(root, "activityDefaultsVersion") else 0L
        require(defaultsVersion in 0..Int.MAX_VALUE.toLong())
        uuid(SnapshotJson.text(root, "dataSetId"))
        uuid(SnapshotJson.text(root, "snapshotId"))
        require(time(root, "createdAt") >= 0)
        fun rows(name: String) = SnapshotJson.objects(SnapshotJson.array(root, name))
        fun ids(name: String): Set<Long> {
            val values = rows(name).map { SnapshotJson.id(it, "id") }
            require(values.distinct().size == values.size) { "Duplicate $name ID" }
            return values.toSet()
        }
        val types = ids("recordTypes")
        val categories = ids("categories")
        val definitions = rows("tags").associateBy { SnapshotJson.id(it, "id") }
        val tags = ids("tags")
        val records = ids("records")
        val running = ids("runningRecords")
        require(running.size <= 1)
        val templates = ids("templates")
        val settings = ids("settingShortcuts")
        require((templates intersect settings).isEmpty())
        val reviews = ids("weeklyReviews")
        ids("trials")
        ids("photos")
        val typeRows = rows("recordTypes").associateBy { SnapshotJson.id(it, "id") }
        val presetKeys = mutableSetOf<String>()
        rows("recordTypes").forEach {
            SnapshotJson.text(it, "name")
            SnapshotJson.text(it, "icon")
            SnapshotJson.text(it, "note", "")
            color(it.getAsJsonObject("color"))
            SnapshotJson.bool(it, "hidden")
            require(SnapshotJson.number(it, "defaultDuration") >= 0)
            val parent = SnapshotJson.nullableId(it, "parentTypeId")
            if (parent != null) {
                require(parent != SnapshotJson.id(it, "id") && parent in types)
                require(SnapshotJson.nullableId(requireNotNull(typeRows[parent]), "parentTypeId") == null)
            }
            val key = nullableText(it, "presetKey")
            if (key != null) require(key.isNotBlank() && presetKeys.add(key))
            if (schemaVersion < 3) require(parent == null && key == null)
        }
        if (schemaVersion < 3) require(defaultsVersion == 0L)
        rows("categories").forEach {
            SnapshotJson.text(it, "name")
            SnapshotJson.text(it, "note", "")
            color(it.getAsJsonObject("color"))
        }
        rows("tags").forEach {
            SnapshotJson.text(it, "name")
            SnapshotJson.text(it, "icon", "")
            SnapshotJson.text(it, "note", "")
            SnapshotJson.text(it, "valueSuffix", "")
            color(it.getAsJsonObject("color"))
            SnapshotJson.bool(it, "archived")
            val source = SnapshotJson.id(it, "iconColorSource", true)
            require(source == 0L || source in types)
            require(SnapshotJson.text(it, "purpose", "GENERAL") in setOf("GENERAL", "PERSON", "ENVIRONMENT"))
            require(SnapshotJson.text(it, "valueType", "NONE") in setOf("NONE", "NUMERIC"))
        }
        val ratingObject = requireNotNull(root.getAsJsonObject("ratingTagIds"))
        val happy = SnapshotJson.id(ratingObject, "happinessTagId")
        val meaning = SnapshotJson.id(ratingObject, "meaningTagId")
        require(happy != meaning && happy in tags && meaning in tags)
        val ratings = setOf(happy, meaning)
        ratings.forEach { require(SnapshotJson.text(requireNotNull(definitions[it]), "valueType") == "NUMERIC") }
        fun checkTags(row: JsonObject, template: Boolean = false) {
            val values = SnapshotJson.objects(SnapshotJson.array(row, "tags"))
            require(values.map { SnapshotJson.id(it, "tagId") }.distinct().size == values.size)
            values.forEach {
                val id = SnapshotJson.id(it, "tagId")
                require(id in tags && (!template || id !in ratings))
                it.get("numericValue")?.takeUnless(JsonElement::isJsonNull)?.let { value ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                    val number = value.asDouble
                    require(number.isFinite())
                    if (id in ratings) require(number in (if (schemaVersion >= 3 || id == meaning && schemaVersion >= 2) -5.0 else 1.0)..5.0 && number % 1.0 == 0.0)
                }
                it.get("ratedAt")?.takeUnless(JsonElement::isJsonNull)?.let { _ -> time(it, "ratedAt") }
            }
        }
        rows("records").forEach {
            val type = SnapshotJson.id(it, "typeId", true)
            require(type == 0L || type in types)
            time(it, "timeStarted")
            time(it, "timeEnded")
            context(it)
            checkTags(it)
            require(SnapshotJson.text(it, "reviewState", "PENDING") in setOf("PENDING", "CONFIRMED"))
            val unfinished = SnapshotJson.bool(it, "isUnfinishedFocus")
            val unfinishedReason = nullableText(it, "unfinishedReason")
            if (schemaVersion < 4) require(!unfinished && unfinishedReason == null)
        }
        rows("runningRecords").forEach {
            require(SnapshotJson.id(it, "id") in types)
            require(time(it, "timeStarted") <= time(root, "createdAt"))
            context(it)
            checkTags(it)
        }
        rows("templates").forEach {
            require(SnapshotJson.id(it, "typeId") in types)
            context(it)
            SnapshotJson.text(it, "name", "")
            checkTags(it, true)
        }
        rows("settingShortcuts").forEach { require(SnapshotJson.text(it, "action") in RecordShortcut.SettingAction.entries.map { action -> action.name }) }
        fun relations(name: String, left: String, leftIds: Set<Long>, right: String, rightIds: Set<Long>) {
            val values = rows(name).map { SnapshotJson.id(it, left) to SnapshotJson.id(it, right) }
            require(values.distinct().size == values.size)
            require(values.all { it.first in leftIds && it.second in rightIds })
        }
        relations("recordTypeCategories", "recordTypeId", types, "categoryId", categories)
        relations("recordTypeTags", "recordTypeId", types, "tagId", tags)
        relations("defaultRecordTypeTags", "recordTypeId", types, "tagId", tags)
        val files = mutableMapOf<String, Triple<String, Long, String>>()
        rows("photos").forEach {
            val record = SnapshotJson.nullableId(it, "recordId")
            val active = SnapshotJson.nullableId(it, "runningRecordId")
            require((record != null) xor (active != null))
            require((record == null || record in records) && (active == null || active in running))
            val path = SnapshotJson.text(it, "relativePath")
            SnapshotJson.safePath(path)
            val hash = SnapshotJson.text(it, "sha256")
            require(hash.matches(Regex("[0-9a-f]{64}")))
            val size = SnapshotJson.number(it, "sizeBytes")
            require(size in 1..50L * 1024 * 1024)
            require(SnapshotJson.number(it, "position") in 0..Int.MAX_VALUE.toLong())
            val mime = SnapshotJson.text(it, "mimeType")
            require(mime.matches(Regex("image/[a-z0-9.+-]+")))
            val signature = Triple(hash, size, mime)
            require(files[path] == null || files[path] == signature)
            files[path] = signature
        }
        val weeks = mutableSetOf<Pair<String, String>>()
        rows("weeklyReviews").forEach { row ->
            val week = SnapshotJson.text(row, "weekStart")
            val zone = SnapshotJson.text(row, "zoneId")
            require(weeks.add(week to zone))
            WeeklyReview(
                SnapshotJson.id(row, "id"), week, zone,
                nullableText(row, "timeAbundance"), references(row, "pleasantRecords"), references(row, "unpleasantRecords"),
                choice(row, "keep"), choice(row, "adjust"), softIds(row, "attentionRecordIds"), softIds(row, "pinnedTemplateIds"),
            )
        }
        rows("trials").forEach {
            val review = SnapshotJson.nullableId(it, "reviewId")
            require(review == null || review in reviews)
            ReviewTrial(
                SnapshotJson.id(it, "id"), review, it.get("source")?.takeUnless(JsonElement::isJsonNull)?.let { ref -> reference(ref.asJsonObject) },
                SnapshotJson.text(it, "description"), softIds(it, "relatedRecordIds"),
            )
        }
        SnapshotV3.validate(root)
        val repair = repairIds(root, legacy = SnapshotJson.number(root, "schemaVersion") == 1L)
        val declared = softIds(root, "repairRecordIds").toSet()
        require(declared == repair) { "Invalid intervals must be declared for repair" }
        return repairIds(root)
    }

    fun repairIds(root: JsonObject, legacy: Boolean = false): Set<Long> {
        val cutoff = SnapshotJson.number(root, "createdAt")
        data class Interval(val id: Long, val start: Long, val end: Long)
        val invalid = mutableSetOf<Long>()
        val intervals = SnapshotJson.objects(SnapshotJson.array(root, "records")).map {
            val id = SnapshotJson.id(it, "id")
            val start = time(it, "timeStarted")
            val end = time(it, "timeEnded")
            if (end <= start || end > cutoff || (legacy && SnapshotJson.text(it, "reviewState", "PENDING") == "CONFIRMED" && (end - start) % 900_000 != 0L)) invalid.add(id)
            Interval(id, start, end)
        }.toMutableList()
        SnapshotJson.objects(SnapshotJson.array(root, "runningRecords")).forEach { intervals.add(Interval(0, time(it, "timeStarted"), cutoff)) }
        var longest: Interval? = null
        intervals.filter { it.end > it.start }.sortedBy { it.start }.forEach { current ->
            val previous = longest
            if (previous != null && current.start < previous.end) {
                if (current.id > 0) invalid.add(current.id)
                if (previous.id > 0) invalid.add(previous.id)
            }
            if (previous == null || current.end > previous.end) longest = current
        }
        return invalid
    }

    private fun uuid(value: String) {
        require(value.length == 36)
        UUID.fromString(value)
    }
    private fun time(root: JsonObject, name: String): Long = SnapshotJson.number(root, name).also { require(it in -MAX_SAFE_TIME..MAX_SAFE_TIME) }
    private fun color(value: JsonObject) {
        require(SnapshotJson.number(value, "colorId") in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        SnapshotJson.text(value, "colorInt", "").takeIf(String::isNotEmpty)?.toInt()
    }
    private fun context(value: JsonObject) {
        SnapshotJson.text(value, "comment", "")
        SnapshotJson.text(value, "summary", "")
        SnapshotJson.nullableId(value, "templateId")
        nullableText(value, "zoneId")?.let { require(it in TimeZone.getAvailableIDs() || TimeZone.getTimeZone(it).id == it) }
    }
    private fun nullableText(root: JsonObject, name: String): String? = root.get(name)?.takeUnless(JsonElement::isJsonNull)?.let { SnapshotJson.text(root, name) }
    private fun softIds(root: JsonObject, name: String): List<Long> = SnapshotJson.array(root, name).map { SnapshotJson.id(it) }.also { require(it.distinct().size == it.size) }
    private fun reference(root: JsonObject) = ReviewRecordReference(SnapshotJson.id(root, "recordId"), SnapshotJson.text(root, "caption", ""))
    private fun references(root: JsonObject, name: String) = SnapshotJson.objects(SnapshotJson.array(root, name)).map(::reference)
    private fun choice(root: JsonObject, name: String): ReviewChoice? = root.get(name)?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let { ReviewChoice(SnapshotJson.text(it, "text", ""), SnapshotJson.nullableId(it, "recordId"), SnapshotJson.nullableId(it, "templateId")) }
}

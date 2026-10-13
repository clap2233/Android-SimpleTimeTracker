package com.example.util.simpletimetracker.data_local.snapshot

import com.example.util.simpletimetracker.data_local.review.ReviewDataLocalMapper
import com.example.util.simpletimetracker.data_local.review.ReviewTrialDBO
import com.example.util.simpletimetracker.data_local.review.WeeklyReviewDBO
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal class SnapshotCodec {
    private val reviews = ReviewDataLocalMapper()

    fun encode(tables: Map<String, JsonArray>, photos: JsonArray, settings: JsonObject, createdAt: Long, snapshotId: String): JsonObject {
        val rowLists = tables.mapValues { SnapshotJson.objects(it.value) }
        fun rows(name: String) = rowLists[name].orEmpty()
        val identity = rows("lifeClockSettings").single()
        val ratings = setOf(identity.long("happiness_tag_id"), identity.long("meaning_tag_id"))
        val tagLinks = listOf("recordToRecordTag" to "record_id", "runningRecordToRecordTag" to "running_record_id", "recordShortcutToRecordTag" to "shortcut_id")
            .associate { (table, owner) -> table to rows(table).groupBy { it.long(owner) } }
        fun tags(table: String, ownerColumn: String, id: Long) = tagLinks[table]?.get(id).orEmpty().map {
            SnapshotJson.obj("tagId" to it.long("record_tag_id").toString(), "numericValue" to it.get("record_tag_numeric_value"), "ratedAt" to it.optionalLong("rated_at"))
        }
        fun record(row: JsonObject, running: Boolean): JsonObject = SnapshotJson.obj(
            "id" to row.long("id").toString(),
            "timeStarted" to row.long("time_started"), "comment" to row.text("comment"),
            "summary" to row.text("summary"), "zoneId" to row.get("zone_id"), "templateId" to row.optionalLong("template_id")?.toString(),
            "tags" to tags(if (running) "runningRecordToRecordTag" else "recordToRecordTag", if (running) "running_record_id" else "record_id", row.long("id")),
        ).also { result ->
            if (!running) {
                result.addProperty("typeId", row.long("type_id").toString())
                result.addProperty("timeEnded", row.long("time_ended"))
                require(row.long("review_state") in 0L..1L)
                result.addProperty("reviewState", if (row.long("review_state") == 1L) "CONFIRMED" else "PENDING")
            }
        }
        fun relation(table: String, leftColumn: String, leftName: String, rightColumn: String, rightName: String) = rows(table).map {
            SnapshotJson.obj(leftName to it.long(leftColumn).toString(), rightName to it.long(rightColumn).toString())
        }
        val recordTypes = rows("recordTypes").map {
            SnapshotJson.obj(
                "id" to it.long("id").toString(), "name" to it.text("name"), "icon" to it.text("icon"),
                "color" to color(it), "defaultDuration" to it.long("instantDuration"), "note" to it.text("note"), "hidden" to it.boolean("hidden"),
            )
        }
        val tagDefinitions = rows("recordTags").map {
            val purpose = it.long("purpose").toInt()
            require(purpose in 0..2)
            SnapshotJson.obj(
                "id" to it.long("id").toString(), "name" to it.text("name"), "icon" to it.text("icon"), "color" to color(it),
                "iconColorSource" to it.long("icon_color_source").toString(), "note" to it.text("note"), "archived" to it.boolean("archived"),
                "valueType" to if (it.optionalLong("value_type") == 1L) "NUMERIC" else "NONE", "valueSuffix" to it.get("value_suffix")?.takeUnless { value -> value.isJsonNull }?.asString.orEmpty(),
                "purpose" to listOf("GENERAL", "PERSON", "ENVIRONMENT")[purpose],
            )
        }
        val templates = rows("recordShortcuts").filter { it.long("target_type") == 0L }.map {
            SnapshotJson.obj(
                "id" to it.long("id").toString(), "typeId" to it.long("type_id").toString(), "name" to it.text("name"),
                "summary" to it.text("summary"), "comment" to it.text("comment"),
                "tags" to tags("recordShortcutToRecordTag", "shortcut_id", it.long("id")).filter { value -> SnapshotJson.id(value, "tagId") !in ratings },
            )
        }
        val shortcuts = rows("recordShortcuts").filter { it.long("target_type") == 1L }.map {
            val action = it.long("setting_action").toInt()
            require(action in RecordShortcut.SettingAction.entries.indices)
            SnapshotJson.obj("id" to it.long("id").toString(), "action" to RecordShortcut.SettingAction.entries[action].name)
        }
        val weekly = rows("weeklyReviews").map { row ->
            val review = reviews.map(WeeklyReviewDBO(row.long("id"), row.text("week_start"), row.text("zone_id"), row.text("content_json")))
            SnapshotJson.obj(
                "id" to review.id.toString(), "weekStart" to review.weekStart, "zoneId" to review.zoneId, "timeAbundance" to review.timeAbundance,
                "pleasantRecords" to review.pleasantRecords.map(::reference), "unpleasantRecords" to review.unpleasantRecords.map(::reference),
                "keep" to review.keep?.let(::choice), "adjust" to review.adjust?.let(::choice),
                "attentionRecordIds" to review.attentionRecordIds.map(Long::toString), "pinnedTemplateIds" to review.pinnedTemplateIds.map(Long::toString),
            )
        }
        val trials = rows("reviewTrials").map { row ->
            val trial = reviews.map(ReviewTrialDBO(row.long("id"), row.optionalLong("review_id"), row.text("content_json")))
            SnapshotJson.obj(
                "id" to trial.id.toString(), "reviewId" to trial.reviewId?.toString(), "source" to trial.source?.let(::reference),
                "description" to trial.description, "relatedRecordIds" to trial.relatedRecordIds.map(Long::toString),
            )
        }
        val mediaById = SnapshotJson.objects(photos).associateBy { SnapshotJson.id(it, "id") }
        val normalizedPhotos = rows("recordPhotos").map { row ->
            val descriptor = requireNotNull(mediaById[row.long("id")])
            SnapshotJson.obj(
                "id" to row.long("id").toString(), "recordId" to row.optionalLong("record_id")?.toString(),
                "runningRecordId" to row.optionalLong("running_record_id")?.toString(), "relativePath" to descriptor.get("relativePath"),
                "sha256" to row.text("sha256"), "position" to row.long("position"), "mimeType" to descriptor.get("mimeType"), "sizeBytes" to descriptor.get("sizeBytes"),
            )
        }
        val raw = JsonObject().apply {
            tables.forEach { (name, values) ->
                add(
                    name,
                    values.deepCopy().also { copied ->
                        SnapshotJson.objects(copied).forEach { row ->
                            if (name == "lifeClockSettings") row.remove("restore_operation_id")
                            if (name == "recordPhotos") row.add("relative_path", requireNotNull(mediaById[row.long("id")]).get("relativePath").deepCopy())
                        }
                    },
                )
            }
        }
        return SnapshotJson.obj(
            "schemaVersion" to 1, "dataSetId" to identity.text("data_set_id"), "snapshotId" to snapshotId, "createdAt" to createdAt,
            "ratingTagIds" to SnapshotJson.obj("happinessTagId" to identity.long("happiness_tag_id").toString(), "meaningTagId" to identity.long("meaning_tag_id").toString()),
            "recordTypes" to recordTypes, "categories" to rows("categories").map { SnapshotJson.obj("id" to it.long("id").toString(), "name" to it.text("name"), "color" to color(it), "note" to it.text("note")) },
            "recordTypeCategories" to relation("recordTypeCategory", "record_type_id", "recordTypeId", "category_id", "categoryId"),
            "tags" to tagDefinitions, "recordTypeTags" to relation("recordTypeToTag", "record_type_id", "recordTypeId", "record_tag_id", "tagId"),
            "defaultRecordTypeTags" to relation("recordTypeToDefaultTag", "record_type_id", "recordTypeId", "record_tag_id", "tagId"),
            "records" to rows("records").map { record(it, false) }, "runningRecords" to rows("runningRecords").map { record(it, true) },
            "photos" to normalizedPhotos, "templates" to templates, "settingShortcuts" to shortcuts, "weeklyReviews" to weekly, "trials" to trials,
            "businessSettings" to settings, "upstreamData" to SnapshotJson.obj("databaseVersion" to 37, "tables" to raw),
        ).also { root -> root.add("repairRecordIds", SnapshotJson.value(SnapshotValidation.repairIds(root).map(Long::toString))) }
    }

    private fun color(row: JsonObject) = SnapshotJson.obj("colorId" to row.long("color"), "colorInt" to row.text("color_int"))
    private fun reference(value: ReviewRecordReference) = SnapshotJson.obj("recordId" to value.recordId.toString(), "caption" to value.caption)
    private fun choice(value: ReviewChoice) = SnapshotJson.obj("text" to value.text, "recordId" to value.recordId?.toString(), "templateId" to value.templateId?.toString())
}

internal fun JsonObject.long(name: String): Long = requireNotNull(get(name)).asString.toLong()
internal fun JsonObject.optionalLong(name: String): Long? = get(name)?.takeUnless(JsonElement::isJsonNull)?.asString?.toLong()
internal fun JsonObject.text(name: String): String = requireNotNull(get(name)).asString
internal fun JsonObject.boolean(name: String): Boolean {
    val value = long(name)
    require(value in 0L..1L)
    return value == 1L
}

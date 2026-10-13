package com.example.util.simpletimetracker.data_local.snapshot

import com.example.util.simpletimetracker.data_local.review.ReviewDataLocalMapper
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object SnapshotTables {
    val names = listOf(
        "recordTypes", "categories", "recordTags", "recordShortcuts", "weeklyReviews", "lifeClockSettings",
        "records", "runningRecords", "recordTypeCategory", "recordTypeToTag", "recordTypeToDefaultTag",
        "recordToRecordTag", "runningRecordToRecordTag", "recordShortcutToRecordTag", "recordPhotos", "reviewTrials",
        "activityFilters", "favouriteComments", "recordTypeToFavouriteComment", "recordTypeGoals", "favouriteIcons",
        "complexRules", "favouriteColors", "activitySuggestion", "durationSuggestions", "favouriteRecordFilters", "favouriteRecordFilter",
    )
    private val core = names.take(16).toSet()

    fun decode(root: JsonObject): Map<String, JsonArray> {
        SnapshotValidation.validate(root)
        val fromPublic = publicTables(root)
        val upstream = root.get("upstreamData")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject ?: return fromPublic
        require(SnapshotJson.number(upstream, "databaseVersion") in 36L..37L)
        val raw = requireNotNull(upstream.getAsJsonObject("tables"))
        require(raw.keySet().all { it in names }) { "Unknown backup table" }
        val tables = names.associateWith { SnapshotJson.array(raw, it).deepCopy() }
        val codec = SnapshotCodec()
        val metadata = root.getAsJsonObject("businessSettings") ?: JsonObject()
        val photos = SnapshotJson.array(root, "photos")
        val createdAt = SnapshotJson.number(root, "createdAt")
        val snapshotId = SnapshotJson.text(root, "snapshotId")
        val expected = codec.encode(fromPublic, photos, metadata, createdAt, snapshotId)
        val actual = codec.encode(tables, photos, metadata, createdAt, snapshotId)
        expected.entrySet().filter { it.key != "upstreamData" }.forEach { (key, value) -> require(value == actual.get(key)) { "Conflicting $key data" } }
        return tables
    }

    private fun publicTables(root: JsonObject): Map<String, JsonArray> {
        val result = names.associateWith { JsonArray() }.toMutableMap()
        fun add(table: String, vararg values: Pair<String, Any?>) {
            val row = JsonObject()
            values.forEach { (name, value) ->
                row.add(
                    name,
                    SnapshotJson.value(
                        when (value) {
                            is Boolean -> if (value) "1" else "0"
                            is Long -> value.toString()
                            is Int -> value.toString()
                            else -> value
                        },
                    ),
                )
            }
            requireNotNull(result[table]).add(row)
        }
        fun objects(name: String) = SnapshotJson.objects(SnapshotJson.array(root, name))
        fun text(row: JsonObject, name: String) = SnapshotJson.text(row, name, "")
        fun id(row: JsonObject, name: String, zero: Boolean = false) = SnapshotJson.id(row, name, zero)
        fun color(row: JsonObject) = requireNotNull(row.getAsJsonObject("color"))
        objects("recordTypes").forEach { row ->
            add(
                "recordTypes", "id" to id(row, "id"), "name" to text(row, "name"), "icon" to text(row, "icon"),
                "color" to SnapshotJson.number(color(row), "colorId"), "color_int" to text(color(row), "colorInt"),
                "hidden" to SnapshotJson.bool(row, "hidden"), "instant" to false, "instantDuration" to SnapshotJson.number(row, "defaultDuration"), "note" to text(row, "note"),
            )
        }
        objects("categories").forEach { row ->
            add(
                "categories", "id" to id(row, "id"), "name" to text(row, "name"),
                "color" to SnapshotJson.number(color(row), "colorId"), "color_int" to text(color(row), "colorInt"), "note" to text(row, "note"),
            )
        }
        objects("tags").forEach { row ->
            add(
                "recordTags", "id" to id(row, "id"), "type_id" to 0L, "name" to text(row, "name"), "icon" to text(row, "icon"),
                "color" to SnapshotJson.number(color(row), "colorId"), "color_int" to text(color(row), "colorInt"),
                "icon_color_source" to id(row, "iconColorSource", true), "archived" to SnapshotJson.bool(row, "archived"), "note" to text(row, "note"),
                "value_type" to if (SnapshotJson.text(row, "valueType", "NONE") == "NUMERIC") 1L else null, "value_suffix" to text(row, "valueSuffix").takeIf(String::isNotEmpty),
                "purpose" to listOf("GENERAL", "PERSON", "ENVIRONMENT").indexOf(SnapshotJson.text(row, "purpose", "GENERAL")),
            )
        }
        fun joinTags(row: JsonObject, table: String, column: String, owner: Long) {
            SnapshotJson.objects(SnapshotJson.array(row, "tags")).forEach { tag ->
                val values = mutableListOf(column to owner, "record_tag_id" to id(tag, "tagId"), "record_tag_numeric_value" to tag.get("numericValue"))
                if (table != "recordShortcutToRecordTag") values.add("rated_at" to tag.get("ratedAt")?.takeUnless(JsonElement::isJsonNull)?.let { SnapshotJson.number(tag, "ratedAt") })
                add(table, *values.toTypedArray())
            }
        }
        fun context(row: JsonObject) = arrayOf(
            "comment" to text(row, "comment"), "summary" to text(row, "summary"),
            "zone_id" to row.get("zoneId"), "template_id" to SnapshotJson.nullableId(row, "templateId"),
        )
        objects("records").forEach { row ->
            val key = id(row, "id")
            add(
                "records", "id" to key, "type_id" to id(row, "typeId", true), "time_started" to SnapshotJson.number(row, "timeStarted"), "time_ended" to SnapshotJson.number(row, "timeEnded"),
                "tag_id" to 0L, "review_state" to if (SnapshotJson.text(row, "reviewState", "PENDING") == "CONFIRMED") 1L else 0L, *context(row),
            )
            joinTags(row, "recordToRecordTag", "record_id", key)
        }
        objects("runningRecords").forEach { row ->
            val key = id(row, "id")
            add("runningRecords", "id" to key, "time_started" to SnapshotJson.number(row, "timeStarted"), "tag_id" to 0L, *context(row))
            joinTags(row, "runningRecordToRecordTag", "running_record_id", key)
        }
        objects("templates").forEach { row ->
            val key = id(row, "id")
            add("recordShortcuts", "id" to key, "type_id" to id(row, "typeId"), "comment" to text(row, "comment"), "target_type" to 0L, "setting_action" to 0L, "name" to text(row, "name"), "summary" to text(row, "summary"))
            joinTags(row, "recordShortcutToRecordTag", "shortcut_id", key)
        }
        objects("settingShortcuts").forEach { row ->
            add(
                "recordShortcuts", "id" to id(row, "id"), "type_id" to 0L, "comment" to "", "target_type" to 1L,
                "setting_action" to RecordShortcut.SettingAction.entries.indexOfFirst { it.name == text(row, "action") }, "name" to "", "summary" to "",
            )
        }
        fun relation(name: String, table: String, left: String, leftColumn: String, right: String, rightColumn: String) {
            objects(name).forEach { row -> add(table, leftColumn to id(row, left), rightColumn to id(row, right)) }
        }
        relation("recordTypeCategories", "recordTypeCategory", "recordTypeId", "record_type_id", "categoryId", "category_id")
        relation("recordTypeTags", "recordTypeToTag", "recordTypeId", "record_type_id", "tagId", "record_tag_id")
        relation("defaultRecordTypeTags", "recordTypeToDefaultTag", "recordTypeId", "record_type_id", "tagId", "record_tag_id")
        objects("photos").forEach { row ->
            add(
                "recordPhotos", "id" to id(row, "id"), "record_id" to SnapshotJson.nullableId(row, "recordId"), "running_record_id" to SnapshotJson.nullableId(row, "runningRecordId"),
                "relative_path" to text(row, "relativePath"), "sha256" to text(row, "sha256"), "position" to SnapshotJson.number(row, "position"),
            )
        }
        val mapper = ReviewDataLocalMapper()
        fun softIds(row: JsonObject, name: String) = SnapshotJson.array(row, name).map { SnapshotJson.id(it) }
        fun reference(row: JsonObject) = ReviewRecordReference(id(row, "recordId"), text(row, "caption"))
        fun choice(row: JsonObject, name: String) = row.get(name)?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let { ReviewChoice(text(it, "text"), SnapshotJson.nullableId(it, "recordId"), SnapshotJson.nullableId(it, "templateId")) }
        objects("weeklyReviews").forEach { row ->
            val review = WeeklyReview(
                id(row, "id"), text(row, "weekStart"), text(row, "zoneId"), row.get("timeAbundance")?.takeUnless(JsonElement::isJsonNull)?.asString,
                SnapshotJson.objects(SnapshotJson.array(row, "pleasantRecords")).map(::reference), SnapshotJson.objects(SnapshotJson.array(row, "unpleasantRecords")).map(::reference),
                choice(row, "keep"), choice(row, "adjust"), softIds(row, "attentionRecordIds"), softIds(row, "pinnedTemplateIds"),
            )
            val dbo = mapper.map(review)
            add("weeklyReviews", "id" to dbo.id, "week_start" to dbo.weekStart, "zone_id" to dbo.zoneId, "content_json" to dbo.contentJson)
        }
        objects("trials").forEach { row ->
            val trial = ReviewTrial(id(row, "id"), SnapshotJson.nullableId(row, "reviewId"), row.get("source")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let(::reference), text(row, "description"), softIds(row, "relatedRecordIds"))
            val dbo = mapper.map(trial)
            add("reviewTrials", "id" to dbo.id, "review_id" to dbo.reviewId, "content_json" to dbo.contentJson)
        }
        val ratings = requireNotNull(root.getAsJsonObject("ratingTagIds"))
        add("lifeClockSettings", "id" to 1L, "data_set_id" to SnapshotJson.text(root, "dataSetId"), "happiness_tag_id" to id(ratings, "happinessTagId"), "meaning_tag_id" to id(ratings, "meaningTagId"))
        return result
    }

    fun finishRunning(tables: Map<String, JsonArray>, createdAt: Long, repair: Set<Long>): Map<String, JsonArray> {
        val result = tables.mapValues { it.value.deepCopy() }.toMutableMap()
        val records = requireNotNull(result["records"])
        var lastId = SnapshotJson.objects(records).maxOfOrNull { it.long("id") } ?: 0L
        SnapshotJson.objects(records).filter { it.long("id") in repair }.forEach { it.addProperty("review_state", "0") }
        SnapshotJson.objects(requireNotNull(result["runningRecords"])).forEach { active ->
            require(lastId < Long.MAX_VALUE)
            val newId = ++lastId
            records.add(
                active.deepCopy().apply {
                    addProperty("type_id", active.text("id"))
                    addProperty("id", newId.toString())
                    addProperty("time_ended", createdAt.toString())
                    addProperty("review_state", "0")
                },
            )
            SnapshotJson.objects(requireNotNull(result["runningRecordToRecordTag"])).filter { it.long("running_record_id") == active.long("id") }.forEach { tag ->
                requireNotNull(result["recordToRecordTag"]).add(
                    tag.deepCopy().apply {
                        remove("running_record_id")
                        addProperty("record_id", newId.toString())
                    },
                )
            }
            SnapshotJson.objects(requireNotNull(result["recordPhotos"])).filter { it.optionalLong("running_record_id") == active.long("id") }.forEach {
                it.addProperty("record_id", newId.toString())
                it.add("running_record_id", com.google.gson.JsonNull.INSTANCE)
            }
        }
        result["runningRecords"] = JsonArray()
        result["runningRecordToRecordTag"] = JsonArray()
        return result
    }
}

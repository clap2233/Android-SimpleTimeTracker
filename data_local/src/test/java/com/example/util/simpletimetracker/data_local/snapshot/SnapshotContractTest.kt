package com.example.util.simpletimetracker.data_local.snapshot

import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotContractTest {
    private fun fixture(): JsonObject = SnapshotJson.parse(File("../shared/fixtures/snapshot-v1.json").readText())

    @Test
    fun phoneWeekReviewMatchesTheSharedSnapshotAcceptanceResults() {
        val root = fixture()
        val expected = SnapshotJson.parse(File("../shared/fixtures/expected-v1.json").readText())
        fun tags(item: JsonObject) = SnapshotJson.objects(SnapshotJson.array(item, "tags")).map {
            com.example.util.simpletimetracker.domain.record.model.RecordBase.Tag(
                it.get("tagId").asString.toLong(), it.get("numericValue").takeUnless { value -> value.isJsonNull }?.asDouble,
            )
        }
        val records = SnapshotJson.objects(SnapshotJson.array(root, "records")).map {
            com.example.util.simpletimetracker.domain.record.model.Record(
                id = it.get("id").asString.toLong(), typeId = it.get("typeId").asString.toLong(),
                timeStarted = it.get("timeStarted").asLong, timeEnded = it.get("timeEnded").asLong,
                comment = it.get("comment").asString, tags = tags(it),
                reviewState = com.example.util.simpletimetracker.domain.record.model.RecordReviewState.valueOf(it.get("reviewState").asString),
            )
        }
        val running = SnapshotJson.objects(SnapshotJson.array(root, "runningRecords")).map {
            com.example.util.simpletimetracker.domain.record.model.RunningRecord(
                it.get("id").asString.toLong(),
                it.get("timeStarted").asLong, it.get("comment").asString, tags(it),
            )
        }
        val identity = root.getAsJsonObject("ratingTagIds")
        val calculator = com.example.util.simpletimetracker.domain.review.interactor.WeekReviewCalculator()
        val stats = calculator.calculate(
            "2026-10-05", "Asia/Shanghai", root.get("createdAt").asLong,
            records, running, identity.get("happinessTagId").asString.toLong(), identity.get("meaningTagId").asString.toLong(),
        )
        assertEquals(expected.get("elapsedMs").asLong, stats.elapsed)
        assertEquals(expected.get("recordedUnionMs").asLong, stats.recorded)
        assertEquals(expected.get("unrecordedMs").asLong, stats.unrecorded)
        assertEquals(expected.get("confirmedMs").asLong, stats.confirmed)
        assertEquals(expected.get("pendingMs").asLong, stats.pending)
        assertEquals(expected.get("runningMs").asLong, stats.running)
        assertEquals(expected.getAsJsonObject("confirmedByType").entrySet().associate { it.key.toLong() to it.value.asLong }, stats.confirmedByType)
        listOf("happiness" to stats.happiness, "meaning" to stats.meaning).forEach { (name, scores) ->
            val values = expected.getAsJsonObject(name)
            assertEquals(values.getAsJsonArray("distribution").map { it.asInt }, scores.distribution)
            assertEquals(values.get("ratedCount").asInt, scores.rated)
            assertEquals(values.get("unratedCount").asInt, scores.unrated)
        }
        expected.getAsJsonArray("dailyConfirmedMs").forEachIndexed { index, duration ->
            assertEquals(
                duration.asLong,
                stats.blocks.filter {
                    it.day == index && it.state ==
                        com.example.util.simpletimetracker.domain.review.interactor.ReviewTimeState.CONFIRMED
                }.sumOf { it.end - it.start },
            )
        }
        val filtered = calculator.calculate(
            "2026-10-05", "Asia/Shanghai", root.get("createdAt").asLong,
            records, running, identity.get("happinessTagId").asString.toLong(), identity.get("meaningTagId").asString.toLong(),
            people = setOf(201, 202),
        )
        assertEquals(expected.get("combinedTagFilterMs").asLong, filtered.recorded)
        assertEquals(expected.getAsJsonArray("combinedTagFilterIds").map { it.asString.toLong() }, filtered.records.map { it.id })
    }

    @Test
    fun publicFixtureDecodesWithoutNativeExtensionsAndKeepsNullRatingsAndSoftReferences() {
        val root = fixture()
        assertTrue(SnapshotValidation.validate(root).isEmpty())
        val tables = SnapshotTables.decode(root)
        val encoded = SnapshotCodec().encode(tables, SnapshotJson.array(root, "photos"), root.getAsJsonObject("businessSettings"), SnapshotJson.number(root, "createdAt"), SnapshotJson.text(root, "snapshotId"))
        listOf("records", "runningRecords", "weeklyReviews", "trials", "ratingTagIds", "photos").forEach { assertEquals(it, root.get(it), encoded.get(it)) }
    }

    @Test
    fun missingOptionalCollectionsAreEmptyAndBadReferencesAreRejected() {
        val root = fixture().apply {
            remove("templates")
            remove("weeklyReviews")
            remove("trials")
            remove("settingShortcuts")
        }
        assertTrue(SnapshotTables.decode(root).getValue("recordShortcuts").size() == 0)
        root.getAsJsonArray("records")[0].asJsonObject.addProperty("typeId", "99999")
        assertTrue(runCatching { SnapshotValidation.validate(root) }.isFailure)
    }

    @Test
    fun malformedIdsScoresPathsAndDuplicateJsonKeysAreRejected() {
        for (path in listOf("../photos/a.png", "photos/../a.png", "photos\\a.png", "/photos/a.png")) {
            val root = fixture()
            root.getAsJsonArray("photos")[0].asJsonObject.addProperty("relativePath", path)
            assertTrue(runCatching { SnapshotValidation.validate(root) }.isFailure)
        }
        val id = fixture().apply { getAsJsonArray("records")[0].asJsonObject.addProperty("id", "01") }
        assertTrue(runCatching { SnapshotValidation.validate(id) }.isFailure)
        assertTrue(runCatching { SnapshotJson.parse("{\"schemaVersion\":1,\"schemaVersion\":2}") }.isFailure)
        val root = fixture()
        val rating = SnapshotJson.text(root.getAsJsonObject("ratingTagIds"), "happinessTagId")
        val tag = root.getAsJsonArray("records").flatMap { SnapshotJson.objects(it.asJsonObject.getAsJsonArray("tags")) }.first { SnapshotJson.text(it, "tagId") == rating }
        tag.addProperty("numericValue", 2.5)
        assertTrue(runCatching { SnapshotValidation.validate(root) }.isFailure)
    }

    @Test
    fun ongoingRecordStopsAtSnapshotTimeAndKeepsPhotosAndScores() {
        val root = fixture()
        val before = SnapshotTables.decode(root)
        val after = SnapshotTables.finishRunning(before, SnapshotJson.number(root, "createdAt"), emptySet())
        assertEquals(0, after.getValue("runningRecords").size())
        assertEquals(before.getValue("records").size() + 1, after.getValue("records").size())
        val last = SnapshotJson.objects(after.getValue("records")).last()
        assertEquals(SnapshotJson.number(root, "createdAt"), last.long("time_ended"))
        assertEquals("0", last.text("review_state"))
    }

    @Test
    fun csvAndIcsContainExperienceFieldsAndFoldUtf8WithoutSplittingCharacters() {
        val root = fixture()
        val csv = SnapshotTextExport.csv(root)
        assertTrue(csv.contains("happinessRatedAt,meaningRatedAt,photoCount"))
        assertTrue(csv.contains("洗碗"))
        root.getAsJsonArray("records")[0].asJsonObject.addProperty("summary", "泡茶🍵后阅读".repeat(30))
        val ics = SnapshotTextExport.ics(root)
        ics.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertTrue(ics.replace("\r\n ", "").contains("泡茶🍵后阅读".repeat(30)))
        assertTrue(ics.contains("@life-clock"))
    }

    @Test
    fun businessSettingsRejectCredentialsAndWrongStorageTypes() {
        assertTrue(runCatching { SnapshotPrefs.validate(JsonObject().apply { addProperty("automaticBackupUri", "private") }) }.isFailure)
        assertTrue(runCatching { SnapshotPrefs.validate(JsonObject().apply { addProperty("showSeconds", 1) }) }.isFailure)
        SnapshotPrefs.validate(fixture().getAsJsonObject("businessSettings"))
    }
}

package com.example.util.simpletimetracker.data_local.snapshot

import com.example.util.simpletimetracker.data_local.planning.PlanningJson
import com.example.util.simpletimetracker.domain.planning.PlanRecordLink
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.domain.rhythm.TimerSession
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SnapshotV4Test {
    private fun fixture(version: Int = 4) = SnapshotJson.parse(File("../shared/fixtures/snapshot-v1.json").readText())
        .apply { addProperty("schemaVersion", version) }

    private fun encode(root: JsonObject, tables: Map<String, JsonArray> = SnapshotTables.decode(root)) = SnapshotCodec().encode(
        tables, SnapshotJson.array(root, "photos"), root.getAsJsonObject("businessSettings"),
        SnapshotJson.number(root, "createdAt"), SnapshotJson.text(root, "snapshotId"),
    )

    @Test fun unfinishedFocusAndOptionalReasonRoundTripWithPhotosAndScores() {
        val root = fixture()
        val record = root.getAsJsonArray("records")[0].asJsonObject.apply {
            addProperty("isUnfinishedFocus", true)
            addProperty("unfinishedReason", "临时接电话\n回来再继续")
            addProperty("timeEnded", get("timeStarted").asLong + 479_999)
        }
        val tables = SnapshotTables.decode(root)
        val encoded = encode(root, tables)
        assertEquals(4, encoded.get("schemaVersion").asInt)
        assertEquals(40, encoded.getAsJsonObject("upstreamData").get("databaseVersion").asInt)
        assertEquals(record, encoded.getAsJsonArray("records")[0])
        assertEquals(root.get("photos"), encoded.get("photos"))
        assertEquals(tables.getValue("records"), SnapshotTables.decode(encoded).getValue("records"))
        record.add("unfinishedReason", JsonNull.INSTANCE)
        val withoutReason = encode(root).getAsJsonArray("records")[0].asJsonObject
        assertTrue(withoutReason.get("isUnfinishedFocus").asBoolean)
        assertFalse(withoutReason.has("unfinishedReason"))
    }

    @Test fun oldSchemasAndNativeMirrorsDefaultToFormalExperiences() {
        listOf(1, 2, 3).forEach { version ->
            val root = fixture(version)
            val tables = SnapshotTables.decode(root)
            SnapshotJson.objects(tables.getValue("records")).forEach {
                assertFalse(it.boolean("is_unfinished_focus"))
                assertTrue(it.get("unfinished_reason").isJsonNull)
            }
            val encoded = encode(root, tables)
            encoded.getAsJsonObject("upstreamData").addProperty("databaseVersion", if (version == 3) 39 else 38)
            SnapshotJson.objects(encoded.getAsJsonObject("upstreamData").getAsJsonObject("tables").getAsJsonArray("records")).forEach {
                it.remove("is_unfinished_focus")
                it.remove("unfinished_reason")
            }
            assertEquals(tables.getValue("records"), SnapshotTables.decode(encoded).getValue("records"))
            assertEquals(root.get("records"), encoded.get("records"))
        }
    }

    @Test fun invalidNewValuesLegacyDeclarationsAndNativeConflictsAreRejected() {
        listOf(1, 2, 3).forEach { version ->
            listOf("{\"isUnfinishedFocus\":true}", "{\"unfinishedReason\":\"interrupted\"}").forEach { fields ->
                val root = fixture(version)
                SnapshotJson.parse(fields).entrySet().forEach { (key, value) -> root.getAsJsonArray("records")[0].asJsonObject.add(key, value) }
                assertTrue(runCatching { SnapshotTables.decode(root) }.isFailure)
            }
        }
        listOf("{\"isUnfinishedFocus\":\"true\"}", "{\"isUnfinishedFocus\":null}", "{\"unfinishedReason\":3}").forEach { fields ->
            val root = fixture()
            SnapshotJson.parse(fields).entrySet().forEach { (key, value) -> root.getAsJsonArray("records")[0].asJsonObject.add(key, value) }
            assertTrue(runCatching { SnapshotTables.decode(root) }.isFailure)
        }
        val encoded = encode(fixture())
        encoded.getAsJsonObject("upstreamData").getAsJsonObject("tables").getAsJsonArray("records")[0].asJsonObject
            .addProperty("is_unfinished_focus", "1")
        assertTrue(runCatching { SnapshotTables.decode(encoded) }.isFailure)
        val reasonWithoutFlag = fixture().apply { getAsJsonArray("records")[0].asJsonObject.addProperty("unfinishedReason", "retained text") }
        assertEquals("retained text", encode(reasonWithoutFlag).getAsJsonArray("records")[0].asJsonObject.text("unfinishedReason"))
    }

    @Test fun unfinishedIntervalsStillRejectBadReferencesAndDeclareRealOverlaps() {
        val root = fixture()
        val records = SnapshotJson.objects(root.getAsJsonArray("records"))
        records[0].addProperty("isUnfinishedFocus", true)
        records[0].addProperty("typeId", "99999")
        assertTrue(runCatching { SnapshotTables.decode(root) }.isFailure)
        records[0].addProperty("typeId", root.getAsJsonArray("recordTypes")[0].asJsonObject.text("id"))
        records[1].addProperty("timeStarted", records[0].get("timeStarted").asLong + 1)
        records[1].addProperty("timeEnded", records[0].get("timeEnded").asLong)
        val repair = SnapshotValidation.repairIds(root)
        assertTrue(records.take(2).all { it.long("id") in repair })
        assertTrue(runCatching { SnapshotTables.decode(root) }.isFailure)
        root.add("repairRecordIds", SnapshotJson.value(repair.map(Long::toString)))
        assertEquals(repair, SnapshotValidation.validate(root))
    }

    @Test fun runningFocusRestoreUsesSessionStartAndPreservesAttachmentsAndPlanLinks() {
        listOf(479_999L, 899_999L, 900_000L, 1_320_000L).forEach { elapsed ->
            val root = fixture()
            val now = root.get("createdAt").asLong
            val running = root.getAsJsonArray("runningRecords")[0].asJsonObject
            val session = TimerSession(startedAt = now - elapsed, policy = TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT))
            running.add("timerSession", SnapshotJson.parse(PlanningJson.encode(session)))
            root.getAsJsonArray("photos").add(root.getAsJsonArray("photos")[0].asJsonObject.deepCopy().apply {
                addProperty("id", "2")
                add("recordId", JsonNull.INSTANCE)
                addProperty("runningRecordId", running.text("id"))
            })
            val link = PlanRecordLink(runningRecordId = running.long("id"), occurrenceId = "30000000-0000-4000-8000-000000000001@2026-10-05",
                caption = "planned focus", timeStarted = running.long("timeStarted"))
            root.add("planRecordLinks", JsonArray().apply { add(SnapshotJson.parse(PlanningJson.encode(link)).apply { addProperty("runningRecordId", running.text("id")) }) })
            val before = SnapshotTables.decode(root)
            val after = SnapshotTables.finishRunning(before, now, emptySet())
            val ended = SnapshotJson.objects(after.getValue("records")).last()
            assertEquals(now, ended.long("time_ended"))
            assertEquals(elapsed < 900_000, ended.boolean("is_unfinished_focus"))
            assertEquals(before.getValue("recordPhotos").size(), after.getValue("recordPhotos").size())
            assertTrue(SnapshotJson.objects(after.getValue("recordPhotos")).all { it.get("running_record_id").isJsonNull })
            assertEquals(ended.long("id"), SnapshotJson.objects(after.getValue("recordPhotos")).last().long("record_id"))
            val finishedLink = PlanningJson.link(SnapshotJson.objects(after.getValue("planRecordLinks")).single().text("content_json"))
            assertEquals(ended.long("id"), finishedLink.recordId)
            assertNull(finishedLink.runningRecordId)
            assertEquals(now, finishedLink.timeEnded)
            assertEquals(before.getValue("recordToRecordTag").size() + before.getValue("runningRecordToRecordTag").size(), after.getValue("recordToRecordTag").size())
        }
        val ordinary = fixture()
        val restored = SnapshotTables.finishRunning(SnapshotTables.decode(ordinary), ordinary.get("createdAt").asLong, emptySet())
        assertFalse(SnapshotJson.objects(restored.getValue("records")).last().boolean("is_unfinished_focus"))
        listOf(TimerPolicy(), TimerPolicy(TimerPolicy.Mode.INTERVAL, 30)).forEach { policy ->
            val root = fixture()
            val running = root.getAsJsonArray("runningRecords")[0].asJsonObject
            running.add("timerSession", SnapshotJson.parse(PlanningJson.encode(TimerSession(startedAt = root.get("createdAt").asLong - 1, policy = policy))))
            val result = SnapshotTables.finishRunning(SnapshotTables.decode(root), root.get("createdAt").asLong, emptySet())
            assertFalse(SnapshotJson.objects(result.getValue("records")).last().boolean("is_unfinished_focus"))
        }
    }

    @Test fun textExportsClearlyIdentifyUnfinishedFocusAndKeepReasons() {
        val root = fixture()
        root.getAsJsonArray("records")[0].asJsonObject.apply {
            addProperty("isUnfinishedFocus", true)
            addProperty("unfinishedReason", "=interrupted\nphone call")
        }
        val csv = SnapshotTextExport.csv(root)
        assertTrue(csv.contains("combined,isUnfinishedFocus,unfinishedReason"))
        assertTrue(csv.contains("\"UNFINISHED_FOCUS\""))
        assertTrue(csv.contains("\"'=interrupted\nphone call\""))
        val ics = SnapshotTextExport.ics(root).replace("\r\n ", "")
        assertTrue(ics.contains("SUMMARY:[未完成专注] "))
        assertTrue(ics.contains("X-LIFE-CLOCK-STATE:UNFINISHED_FOCUS"))
        assertTrue(ics.contains("unfinishedReason==interrupted\\nphone call"))
    }
}

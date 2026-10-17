package com.example.util.simpletimetracker.data_local.snapshot

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import com.example.util.simpletimetracker.data_local.planning.PlanningJson
import com.example.util.simpletimetracker.domain.planning.PlanRecordLink
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.domain.rhythm.TimerSession

class SnapshotV3Test {
    private fun fixture() = SnapshotJson.parse(File("../shared/fixtures/snapshot-v1.json").readText())
    @Test fun movedPlanDetailsRoundTripAndInvalidTypesAreRejected() {
        val root = fixture().apply { addProperty("schemaVersion", 2) }
        val plan = com.example.util.simpletimetracker.domain.planning.PlanSchedule(title = "reading", firstDate = "2026-10-12",
            startMinute = 540, durationMinutes = 30, repeat = com.example.util.simpletimetracker.domain.planning.PlanRepeat.DAILY)
        val exception = com.example.util.simpletimetracker.domain.planning.PlanException(plan.id, "2026-10-13",
            movedDate = "2026-11-01", title = "buffer", kind = com.example.util.simpletimetracker.domain.planning.PlanKind.BUFFER, remind = false)
        root.add("planSchedules", JsonArray().apply { add(SnapshotJson.parse(PlanningJson.encode(plan))) })
        val item = SnapshotJson.parse(PlanningJson.encode(exception)).apply { addProperty("id", exception.id) }
        root.add("planExceptions", JsonArray().apply { add(item) })
        val tables = SnapshotTables.decode(root)
        val encoded = SnapshotCodec().encode(tables, SnapshotJson.array(root, "photos"), root.getAsJsonObject("businessSettings"), root.get("createdAt").asLong, root.get("snapshotId").asString)
        assertEquals(root.get("planExceptions"), encoded.get("planExceptions"))
        item.addProperty("remind", "false")
        assertTrue(runCatching { SnapshotValidation.validate(root) }.isFailure)
    }
    @Test fun legacyRepairDeclarationIsCheckedButShortRealTimeIsNoLongerAnError() {
        val root = fixture()
        val record = root.getAsJsonArray("records")[1].asJsonObject
        record.addProperty("timeEnded", record.get("timeStarted").asLong + 760000)
        root.add("repairRecordIds", JsonArray().apply { add(record.get("id")) })
        assertTrue(SnapshotValidation.validate(root).isEmpty())
        val tables = SnapshotTables.decode(root)
        assertEquals("1", SnapshotJson.objects(tables.getValue("records"))[1].text("review_state"))
    }
    @Test fun v2RoundTripPreservesNegativeZeroNullRecoveryAndRunningAllocation() {
        val root = fixture().apply { addProperty("schemaVersion", 2) }
        val record = root.getAsJsonArray("records")[1].asJsonObject
        record.addProperty("timeEnded", record.get("timeStarted").asLong + 760000)
        record.addProperty("recovery", "MORE_TIRED")
        val meaningId = root.getAsJsonObject("ratingTagIds").get("meaningTagId").asString
        val meaning = SnapshotJson.objects(record.getAsJsonArray("tags")).first { it.get("tagId").asString == meaningId }
        val running = root.getAsJsonArray("runningRecords")[0].asJsonObject
        val session = TimerSession(startedAt = running.get("timeStarted").asLong, policy = TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT))
        running.add("timerSession", SnapshotJson.parse(PlanningJson.encode(session)))
        val link = PlanRecordLink(runningRecordId = running.get("id").asString.toLong(), occurrenceId = "30000000-0000-4000-8000-000000000001@2026-10-05", caption = "planned reading", timeStarted = session.startedAt)
        val linkJson = SnapshotJson.parse(PlanningJson.encode(link)).apply { addProperty("runningRecordId", running.get("id").asString) }
        root.add("planRecordLinks", JsonArray().apply { add(linkJson) })
        listOf(-5, -3, 0, 5, null).forEach { score ->
            if (score == null) meaning.add("numericValue", JsonNull.INSTANCE) else meaning.addProperty("numericValue", score)
            val tables = SnapshotTables.decode(root)
            val encoded = SnapshotCodec().encode(tables, SnapshotJson.array(root, "photos"), root.getAsJsonObject("businessSettings"), root.get("createdAt").asLong, root.get("snapshotId").asString)
            assertEquals(root.get("records"), encoded.get("records"))
            assertEquals(root.get("planRecordLinks"), encoded.get("planRecordLinks"))
            val restored = SnapshotTables.finishRunning(tables, root.get("createdAt").asLong, emptySet())
            val restoredLink = PlanningJson.link(SnapshotJson.objects(restored.getValue("planRecordLinks")).single().text("content_json"))
            assertNull(restoredLink.runningRecordId)
            assertEquals(root.get("createdAt").asLong, restoredLink.timeEnded)
            assertNotNull(restoredLink.recordId)
        }
    }
    @Test fun malformedV3ValuesAreRejectedBeforeRestore() {
        val root = fixture().apply { addProperty("schemaVersion", 2) }
        val type = root.getAsJsonArray("recordTypes")[0].asJsonObject
        type.add("timerPolicy", SnapshotJson.parse("{\"mode\":\"FOCUS_EXIT\",\"intervalMinutes\":30}"))
        assertTrue(runCatching { SnapshotValidation.validate(root) }.isFailure)
        type.remove("timerPolicy")
        root.getAsJsonArray("records")[0].asJsonObject.addProperty("recovery", "UNKNOWN")
        assertTrue(runCatching { SnapshotValidation.validate(root) }.isFailure)
    }
}

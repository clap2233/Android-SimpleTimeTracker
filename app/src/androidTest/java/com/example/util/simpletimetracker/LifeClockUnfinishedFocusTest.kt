package com.example.util.simpletimetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.planning.PlanningRepoImpl
import com.example.util.simpletimetracker.data_local.record.*
import com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoDBO
import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordTag.RunningRecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordType.RecordTypeDBO
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.domain.record.interactor.RecordTimeValidator
import com.example.util.simpletimetracker.domain.record.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeClockUnfinishedFocusTest {
    @Test fun exactCutoffKeepsEveryAttemptAndLaterActualDuration() = runBlocking {
        for (duration in listOf(480_000L, 899_999L, 900_000L, 959_999L, 1_320_000L)) withDatabase { f ->
            val active = f.startFocus()
            f.attachPhoto()
            val change = f.repo.stop(1, start + duration, active.operationIdentity(), true)
            assertEquals(listOf(active), change.stopped)
            assertTrue(f.running.getAll().isEmpty())
            val saved = f.records.getAll().single()
            assertEquals(duration, saved.duration)
            assertEquals(duration < 900_000, saved.isUnfinishedFocus)
            assertEquals("focus notes", saved.comment)
            assertEquals(active.tags, saved.tags)
            assertEquals(saved.id, f.db.recordPhotoDao().getAll().single().recordId)
            assertTrue(f.repo.stop(1, start + duration + 1).stopped.isEmpty())
            assertEquals(1, f.records.getAll().size)
        }
    }

    @Test fun switchPreservesAttemptAttachmentsAndPlanWithoutCompletingPlan() = runBlocking {
        withDatabase { f ->
            val active = f.startFocus(planned = true)
            f.attachPhoto()
            val next = RunningRecord(2, start + 480_000, "next", emptyList())
            val change = f.repo.start(next, next.timeStarted, active.operationIdentity(), true)
            assertEquals(2L, requireNotNull(change.started).id)
            val attempt = f.records.getAll().single()
            assertTrue(attempt.isUnfinishedFocus)
            assertEquals(attempt.timeEnded, f.running.getAll().single().timeStarted)
            assertEquals(attempt.id, f.db.recordPhotoDao().getAll().single().recordId)
            val link = f.planning.links().single()
            assertEquals(attempt.id, link.recordId)
            assertNull(link.runningRecordId)
            assertEquals(480_000L, requireNotNull(link.timeEnded) - link.timeStarted)
            assertTrue(f.planning.exceptions().isEmpty())
            assertEquals(1, f.planning.schedules().size)
        }
    }

    @Test fun reasonCanBeAddedClearedAndDoesNotChangeOtherData() = runBlocking {
        withDatabase { f ->
            val active = f.startFocus()
            f.attachPhoto()
            f.repo.stop(1, start + 480_000, active.operationIdentity(), true)
            val original = f.records.getAll().single()
            assertTrue(f.repo.updateUnfinishedReason(original.id, "  phone call  "))
            assertEquals(original.copy(unfinishedReason = "phone call"), f.records.get(original.id))
            assertTrue(f.repo.updateUnfinishedReason(original.id, " "))
            assertEquals(original, f.records.get(original.id))
            assertEquals(1, f.db.recordPhotoDao().getAll().size)
            assertFalse(f.repo.updateUnfinishedReason(999, "missing"))
        }
    }

    @Test fun ordinaryAndPeriodicShortRecordsRemainFormal() = runBlocking {
        for (policy in listOf(null, "{\"mode\":\"INTERVAL\",\"intervalMinutes\":30}")) withDatabase { f ->
            f.setPolicy(policy)
            val active = requireNotNull(f.repo.start(RunningRecord(1, start, "", emptyList()), start).started)
            f.repo.stop(1, start + 480_000, active.operationIdentity())
            val saved = f.records.getAll().single()
            assertFalse(saved.isUnfinishedFocus)
            assertEquals(480_000L, saved.duration)
            assertFalse(f.repo.updateUnfinishedReason(saved.id, "not a focus attempt"))
        }
    }

    @Test fun declinedExitStaleIdentityAndFailedSwitchCannotLoseAttempt() = runBlocking {
        withDatabase { f ->
            val active = f.startFocus(planned = true)
            f.attachPhoto()
            val declined = runCatching { f.repo.stop(1, start + 480_000, active.operationIdentity()) }.exceptionOrNull()
            assertEquals(RecordMutationError.FOCUS_CONFIRMATION_REQUIRED, (declined as RecordMutationException).reason)
            f.assertStillRunning(active)
            val stale = runCatching { f.repo.stop(1, start + 480_000, "outdated", true) }.exceptionOrNull()
            assertEquals(RecordMutationError.NOT_FOUND, (stale as RecordMutationException).reason)
            f.assertStillRunning(active)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_next BEFORE INSERT ON runningRecords WHEN NEW.id = 2 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            assertTrue(runCatching { f.repo.start(RunningRecord(2, start + 480_000, "", emptyList()), start + 480_000, active.operationIdentity(), true) }.isFailure)
            f.assertStillRunning(active)
        }
    }

    @Test fun editingStartDoesNotBypassOrResetActualSessionThreshold() = runBlocking {
        withDatabase { f ->
            val active = f.startFocus()
            f.repo.editRunning(1, active.copy(timeStarted = start - 3_600_000))
            f.repo.stop(1, start + 480_000, active.operationIdentity(), true)
            assertTrue(f.records.getAll().single().isUnfinishedFocus)
        }
        withDatabase { f ->
            val active = f.startFocus()
            f.repo.editRunning(1, active.copy(timeStarted = start + 420_000))
            f.repo.stop(1, start + 960_000, active.operationIdentity(), true)
            val saved = f.records.getAll().single()
            assertFalse(saved.isUnfinishedFocus)
            assertEquals(540_000L, saved.duration)
        }
    }

    @Test fun manualShortHistoryIsNotReclassifiedAndCannotMergeWithAttempt() = runBlocking {
        withDatabase { f ->
            val history = Record(typeId = 1, timeStarted = start - 480_000, timeEnded = start, comment = "history", tags = emptyList())
            val historyId = f.repo.save(listOf(history)).single()
            val active = f.startFocus()
            f.repo.stop(1, start + 480_000, active.operationIdentity(), true)
            val attempt = f.records.getAll().single { it.isUnfinishedFocus }
            assertFalse(requireNotNull(f.records.get(historyId)).isUnfinishedFocus)
            val error = runCatching { f.repo.merge(attempt, historyId, null) }.exceptionOrNull()
            assertEquals(RecordMutationError.FOCUS_RESULT_CONFLICT, (error as RecordMutationException).reason)
            assertEquals(2, f.records.getAll().size)
        }
    }

    @Test fun splittingAttemptPreservesOutcomeAndKeepsReasonWithSelectedAttachments() = runBlocking {
        withDatabase { f ->
            val active = f.startFocus()
            f.attachPhoto()
            f.repo.stop(1, start + 480_000, active.operationIdentity(), true)
            val attempt = f.records.getAll().single()
            f.repo.updateUnfinishedReason(attempt.id, "interrupted")
            f.repo.split(requireNotNull(f.records.get(attempt.id)), start + 240_000, attachmentsBefore = true)
            val parts = f.records.getAll().sortedBy { it.timeStarted }
            assertTrue(parts.all { it.isUnfinishedFocus })
            assertEquals("interrupted", parts.first().unfinishedReason)
            assertNull(parts.last().unfinishedReason)
            assertEquals(parts.first().id, f.db.recordPhotoDao().getAll().single().recordId)
        }
    }

    private suspend fun withDatabase(block: suspend (Fixture) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            for (id in 1L..2L) db.recordTypeDao().insert(RecordTypeDBO(id, "type $id", "", 0, "", false, defaultDuration = 0, note = ""))
            val mapper = RecordDataLocalMapper()
            val runningMapper = RunningRecordDataLocalMapper()
            val gate = DataRestoreGate()
            val records = RecordRepoImpl(db.recordDao(), db, mapper)
            val running = RunningRecordRepoImpl(db.runningRecordDao(), db, runningMapper)
            val repo = RecordMutationRepoImpl(db, records, running, mapper, runningMapper, RecordToRecordTagDataLocalMapper(), RunningRecordToRecordTagDataLocalMapper(), RecordTimeValidator(), CurrentTimestampProvider(), gate)
            block(Fixture(db, repo, records, running, PlanningRepoImpl(db, gate)))
        } finally { db.close() }
    }

    private class Fixture(val db: AppDatabase, val repo: RecordMutationRepoImpl, val records: RecordRepoImpl, val running: RunningRecordRepoImpl, val planning: PlanningRepoImpl) {
        suspend fun setPolicy(policy: String?) { db.recordTypeDao().insert(requireNotNull(db.recordTypeDao().get(1)).copy(timerPolicy = policy)) }
        suspend fun startFocus(planned: Boolean = false): RunningRecord {
            setPolicy("{\"mode\":\"FOCUS_EXIT\",\"intervalMinutes\":0}")
            val plan = if (planned) {
                val schedule = PlanSchedule(title = "study", firstDate = "2023-11-14", startMinute = 22 * 60, durationMinutes = 60, zoneId = "UTC")
                planning.save(schedule)
                val day = PlanCalendar.date(schedule.firstDate, "UTC").timeInMillis
                PlanCalendar.occurrences(listOf(schedule), emptyList(), day, day + 86_400_000).single()
            } else null
            return requireNotNull(repo.start(RunningRecord(1, start, "focus notes", listOf(RecordBase.Tag(9, null))), start, plan = plan).started)
        }
        suspend fun attachPhoto() { db.recordPhotoDao().upsert(RecordPhotoDBO(runningRecordId = 1, relativePath = "photos/focus.jpg", sha256 = "a".repeat(64))) }
        suspend fun assertStillRunning(active: RunningRecord) {
            assertEquals(active, running.getAll().single())
            assertTrue(records.getAll().isEmpty())
            assertEquals(1L, db.recordPhotoDao().getAll().single().runningRecordId)
            assertEquals(1L, planning.links().single().runningRecordId)
        }
    }
    companion object { private const val start = 1_700_000_000_123L }
}

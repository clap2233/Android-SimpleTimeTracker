package com.example.util.simpletimetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.record.RecordDataLocalMapper
import com.example.util.simpletimetracker.data_local.record.RecordMutationRepoImpl
import com.example.util.simpletimetracker.data_local.record.RecordRepoImpl
import com.example.util.simpletimetracker.data_local.record.RunningRecordDataLocalMapper
import com.example.util.simpletimetracker.data_local.record.RunningRecordRepoImpl
import com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoDBO
import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordTag.RunningRecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordType.RecordTypeDBO
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.record.interactor.RecordTimeValidator
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeClockRecordMutationTest {
    private val start = 1_700_000_000_123L
    private val recordMapper = RecordDataLocalMapper()
    private val runningMapper = RunningRecordDataLocalMapper()
    private fun record(minutes: Long, from: Long = start, typeId: Long = 1) = Record(
        typeId = typeId, timeStarted = from, timeEnded = from + minutes * 60_000,
        comment = "notes", tags = listOf(RecordBase.Tag(9, 4.0, start + 1)),
        summary = "reading", zoneId = "Asia/Shanghai", templateId = 7,
    )

    @Test
    fun stopKeepsShortDurationRatingsAndPhotosAndIsIdempotent() = runBlocking {
        withDatabase { db, repo, _ ->
            val running = RunningRecord(1, start, "notes", listOf(RecordBase.Tag(9, 4.0, start + 1)), summary = "reading", zoneId = null, templateId = 7)
            repo.start(running, start)
            db.recordPhotoDao().upsert(photo(runningId = 1))
            repo.stop(1, start + 760_123)
            val saved = recordMapper.map(db.recordDao().getAll().single())
            assertEquals(760_123L, saved.timeEnded - saved.timeStarted)
            assertEquals(RecordReviewState.PENDING, saved.reviewState)
            assertEquals(running.tags, saved.tags)
            assertEquals("reading", saved.summary)
            assertNull(saved.zoneId)
            assertEquals(7L, saved.templateId)
            assertEquals(saved.id, db.recordPhotoDao().getAll().single().recordId)
            assertNull(db.recordPhotoDao().getAll().single().runningRecordId)
            assertTrue(repo.stop(1, start + 760_124).stopped.isEmpty())
            assertEquals(1, db.recordDao().getAll().size)
        }
    }

    @Test
    fun switchUsesOneBoundaryAndEditingTransfersOwnershipBeforeDeleting() = runBlocking {
        withDatabase { db, repo, _ ->
            val original = RunningRecord(1, start, "notes", emptyList(), summary = "reading", zoneId = null, templateId = 7)
            repo.start(original, start)
            db.recordPhotoDao().upsert(photo(runningId = 1))
            repo.editRunning(1, original.copy(id = 2))
            assertNull(db.runningRecordDao().get(1))
            assertEquals(2L, db.recordPhotoDao().getAll().single().runningRecordId)
            val switched = repo.start(RunningRecord(3, start + 900_000, "new", emptyList()), start + 900_000)
            assertEquals(2L, switched.stopped.single().id)
            val saved = db.recordDao().getAll().single().record
            assertEquals(saved.timeEnded, db.runningRecordDao().get(3)?.runningRecord?.timeStarted)
            assertEquals(RecordReviewState.CONFIRMED.storageValue, saved.reviewState)
            assertEquals(saved.id, db.recordPhotoDao().getAll().single().recordId)
        }
    }

    @Test
    fun adjusting12And18To15And15KeepsIdentityPhotosAndCacheConsistent() = runBlocking {
        withDatabase { db, repo, cached ->
            val ids = repo.save(listOf(record(12), record(18, start + 720_000, 2)))
            db.recordPhotoDao().upsert(photo(recordId = ids[0]))
            cached.get(ids[0])
            val second = recordMapper.map(requireNotNull(db.recordDao().get(ids[1])))
            repo.adjustBoundary(second, ids[0], start + 900_000, previous = true, neighborRunning = false)
            val firstAfter = requireNotNull(cached.get(ids[0]))
            val secondAfter = requireNotNull(cached.get(ids[1]))
            assertEquals(900_000L, firstAfter.timeEnded - firstAfter.timeStarted)
            assertEquals(firstAfter.timeEnded, secondAfter.timeStarted)
            assertEquals(900_000L, secondAfter.timeEnded - secondAfter.timeStarted)
            assertEquals(RecordReviewState.CONFIRMED, firstAfter.reviewState)
            assertEquals(ids[0], db.recordPhotoDao().getAll().single().recordId)
            assertEquals(4.0, firstAfter.tags.single().numericValue)
        }
    }

    @Test
    fun boundaryCanMoveAnAdjacentRunningStartInTheSameTransaction() = runBlocking {
        withDatabase { db, repo, _ ->
            val id = repo.save(listOf(record(12))).single()
            repo.start(RunningRecord(2, start + 720_000, "ongoing", emptyList()), start + 1_800_000)
            val original = recordMapper.map(requireNotNull(db.recordDao().get(id)))
            repo.adjustBoundary(original, 2, start + 900_000, previous = false, neighborRunning = true)
            assertEquals(start + 900_000, db.recordDao().get(id)?.record?.timeEnded)
            assertEquals(start + 900_000, db.runningRecordDao().get(2)?.runningRecord?.timeStarted)
        }
    }

    @Test
    fun merge3And27PreservesPhotosAndRequiresNumericConflictChoice() = runBlocking {
        withDatabase { db, repo, _ ->
            val first = record(3)
            val second = record(27, start + 180_000, 2).copy(tags = listOf(RecordBase.Tag(9, 2.0)))
            val ids = repo.save(listOf(first, second))
            db.recordPhotoDao().upsert(photo(recordId = ids[0]))
            db.recordPhotoDao().upsert(photo(recordId = ids[1]))
            val current = second.copy(id = ids[1])
            val failure = runCatching { repo.merge(current, ids[0], null) }.exceptionOrNull()
            assertEquals(RecordMutationError.TAG_CONFLICT, (failure as RecordMutationException).reason)
            assertEquals(2, db.recordDao().getAll().size)
            repo.merge(current, ids[0], current.tags)
            val merged = recordMapper.map(db.recordDao().getAll().single())
            assertEquals(ids[1], merged.id)
            assertEquals(1_800_000L, merged.timeEnded - merged.timeStarted)
            assertEquals(RecordReviewState.CONFIRMED, merged.reviewState)
            assertEquals(2.0, merged.tags.single().numericValue)
            assertTrue(db.recordPhotoDao().getAll().all { it.recordId == merged.id })
        }
    }

    @Test
    fun splitKeepsRatingsAndPhotosInOnlyTheChosenPart() = runBlocking {
        withDatabase { db, repo, _ ->
            val id = repo.save(listOf(record(30))).single()
            db.recordPhotoDao().upsert(photo(recordId = id))
            val original = recordMapper.map(requireNotNull(db.recordDao().get(id)))
            repo.split(original, start + 900_000, attachmentsBefore = true)
            val parts = db.recordDao().getAll().map(recordMapper::map).sortedBy { it.timeStarted }
            assertEquals(2, parts.size)
            assertEquals(id, parts[1].id)
            assertEquals(4.0, parts[0].tags.single().numericValue)
            assertNull(parts[1].tags.single().numericValue)
            assertEquals(parts[0].id, db.recordPhotoDao().getAll().single().recordId)
            assertEquals(parts[0].timeEnded, parts[1].timeStarted)
        }
    }

    @Test
    fun failedSecondWriteRollsBackTheWholeBatchAndInvalidatesCache() = runBlocking {
        withDatabase { db, repo, cached ->
            val id = repo.save(listOf(record(15))).single()
            cached.get(id)
            val before = requireNotNull(cached.get(id))
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_record BEFORE INSERT ON records WHEN NEW.comment = 'fail' BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            )
            assertTrue(
                runCatching {
                    repo.save(listOf(before.copy(comment = "changed"), record(15, start + 900_000).copy(comment = "fail")))
                }.isFailure,
            )
            assertEquals(before, cached.get(id))
            assertEquals(1, db.recordDao().getAll().size)
        }
    }

    private fun photo(recordId: Long? = null, runningId: Long? = null) = RecordPhotoDBO(
        recordId = recordId, runningRecordId = runningId, relativePath = "photos/a.jpg", sha256 = "a".repeat(64),
    )

    @Test
    fun repeatedConcurrentStartsCreateOnlyOneRunningRecord() = runBlocking {
        withDatabase { db, repo, _ ->
            coroutineScope {
                (1..8).map { async { repo.start(RunningRecord(1, start, "", emptyList()), start) } }.awaitAll()
            }
            assertEquals(1, db.runningRecordDao().getAll().size)
            assertTrue(db.recordDao().getAll().isEmpty())
        }
    }

    @Test
    fun gapAndThirdRecordCannotBeSilentlyOverwritten() = runBlocking {
        withDatabase { db, repo, _ ->
            val ids = repo.save(listOf(record(3), record(15, start + 300_000, 2), record(10, start + 1_200_000, 3)))
            val current = recordMapper.map(requireNotNull(db.recordDao().get(ids[1])))
            val gap = runCatching { repo.merge(current, ids[0], null) }.exceptionOrNull()
            assertEquals(RecordMutationError.GAP, (gap as RecordMutationException).reason)
            val overlap = runCatching { repo.save(listOf(current.copy(timeEnded = start + 1_800_000))) }.exceptionOrNull()
            assertEquals(RecordMutationError.OVERLAP, (overlap as RecordMutationException).reason)
            assertEquals(current, recordMapper.map(requireNotNull(db.recordDao().get(ids[1]))))
            assertEquals(3, db.recordDao().getAll().size)
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase, RecordMutationRepoImpl, RecordRepoImpl) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            for (id in 1L..3L) db.recordTypeDao().insert(RecordTypeDBO(id, "type $id", "", 0, "", false, defaultDuration = 0, note = ""))
            val records = RecordRepoImpl(db.recordDao(), recordMapper)
            val running = RunningRecordRepoImpl(db.runningRecordDao(), runningMapper)
            val repo = RecordMutationRepoImpl(
                db, records, running, recordMapper, runningMapper, RecordToRecordTagDataLocalMapper(),
                RunningRecordToRecordTagDataLocalMapper(), RecordTimeValidator(), CurrentTimestampProvider(),
            )
            block(db, repo, records)
        } finally {
            db.close()
        }
    }
}

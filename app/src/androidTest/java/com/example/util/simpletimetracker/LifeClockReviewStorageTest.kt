package com.example.util.simpletimetracker

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.lifeClock.LifeClockSettingsRepoImpl
import com.example.util.simpletimetracker.data_local.recordTag.RecordTagDBO
import com.example.util.simpletimetracker.data_local.review.ReviewDataLocalMapper
import com.example.util.simpletimetracker.data_local.review.ReviewRepoImpl
import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeClockReviewStorageTest {

    @Test fun newReviewAndTrialRollbackTogetherWhenTheTrialInsertFails() = runBlocking {
        withDatabase { database ->
            val repo = ReviewRepoImpl(database, ReviewDataLocalMapper())
            val review = WeeklyReview(weekStart = "2026-10-05", zoneId = "Asia/Shanghai")
            val trial = ReviewTrial(description = "Try morning reading")
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_trial BEFORE INSERT ON reviewTrials BEGIN SELECT RAISE(ABORT, 'test'); END")
            assertTrue(runCatching { repo.saveTrialWithReview(review, trial) }.isFailure)
            assertTrue(repo.getReviews().isEmpty())
            assertTrue(repo.getTrials().isEmpty())
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_trial")
            val id = repo.saveTrialWithReview(review, trial)
            assertEquals(id, repo.getTrials().single().id)
            assertEquals(repo.getReviews().single().id, repo.getTrials().single().reviewId)
        }
    }

    @Test fun queuedReviewWritesRejectTheOldRestoreEpoch() = runBlocking {
        withDatabase { database ->
            val gate = com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate()
            val repo = ReviewRepoImpl(database, ReviewDataLocalMapper(), gate)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val transaction = async(Dispatchers.IO) {
                database.withTransaction {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repo.saveReview(WeeklyReview(weekStart = "2026-10-05", zoneId = "Asia/Shanghai")) }
            }
            assertTrue(gate.begin())
            gate.committed()
            gate.end()
            release.complete(Unit)
            transaction.await()
            val error = queued.await().exceptionOrNull() as com.example.util.simpletimetracker.domain.record.model.RecordMutationException
            assertEquals(com.example.util.simpletimetracker.domain.record.model.RecordMutationError.DATA_RESTORING, error.reason)
            assertTrue(repo.getReviews().isEmpty())
        }
    }

    @Test
    fun concurrentInitializationUsesOneIdentityAndDoesNotGuessTagsByName() = runBlocking {
        withDatabase { database ->
            val legacyId = database.recordTagDao().insert(tag("快乐"))
            val repo = LifeClockSettingsRepoImpl(database)
            val identities = coroutineScope { (1..8).map { async { repo.getOrCreate() } }.awaitAll() }
            val identity = identities.first()
            assertTrue(identities.all { it == identity })
            assertEquals(identity.dataSetId, UUID.fromString(identity.dataSetId).toString())
            assertFalse(legacyId in identity.ratingTagIds)
            assertEquals(3, database.recordTagDao().getAll().size)

            val happiness = requireNotNull(database.recordTagDao().get(identity.happinessTagId))
            database.recordTagDao().insert(happiness.copy(name = "开心"))
            assertEquals(identity, repo.getOrCreate())
            assertEquals("开心", database.recordTagDao().get(identity.happinessTagId)?.name)
        }
    }

    @Test
    fun failedInitializationRollsBackTagsAndIdentity() = runBlocking {
        withDatabase { database ->
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_meaning BEFORE INSERT ON recordTags WHEN NEW.name = '意义' " +
                    "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            )
            val repo = LifeClockSettingsRepoImpl(database)
            assertTrue(runCatching { repo.getOrCreate() }.isFailure)
            assertNull(database.lifeClockSettingsDao().get())
            assertEquals(emptyList<RecordTagDBO>(), database.recordTagDao().getAll())
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_meaning")
            assertNotNull(repo.getOrCreate())
            assertEquals(2, database.recordTagDao().getAll().size)
        }
    }

    @Test
    fun savingTheSameWeekKeepsIdentityAndLinkedTrialText() = runBlocking {
        withDatabase { database ->
            val repo = ReviewRepoImpl(database, ReviewDataLocalMapper())
            val empty = WeeklyReview(weekStart = "2026-10-05", zoneId = "Asia/Shanghai")
            val id = repo.saveReview(empty)
            val trialId = repo.saveTrial(
                ReviewTrial(reviewId = id, source = ReviewRecordReference(999, "晚间阅读"), description = "试一次早晨阅读"),
            )
            val changed = empty.copy(keep = ReviewChoice("保留散步"), pinnedTemplateIds = listOf(7))
            assertEquals(id, repo.saveReview(changed))
            assertEquals(changed.copy(id = id), repo.getReview(empty.weekStart, empty.zoneId))
            assertEquals(id, repo.getTrials().single().reviewId)
            assertEquals(trialId, repo.getTrials().single().id)
            assertTrue(runCatching { repo.saveReview(empty.copy(id = id, weekStart = "2026-10-12")) }.isFailure)

            repo.removeReview(id)
            val retained = repo.getTrials().single()
            assertNull(retained.reviewId)
            assertEquals("试一次早晨阅读", retained.description)
            assertEquals(ReviewRecordReference(999, "晚间阅读"), retained.source)
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun tag(name: String): RecordTagDBO = RecordTagDBO(
        id = 0, typeId = 0, name = name, icon = "", color = 0, colorInt = "",
        iconColorSource = 0, archived = false, note = "", valueType = 1, valueSuffix = null,
    )
}

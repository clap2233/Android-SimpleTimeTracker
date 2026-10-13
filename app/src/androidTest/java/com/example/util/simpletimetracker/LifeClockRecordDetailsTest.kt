package com.example.util.simpletimetracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.lifeClock.LifeClockSettingsRepoImpl
import com.example.util.simpletimetracker.data_local.record.RecordDataLocalMapper
import com.example.util.simpletimetracker.data_local.record.RecordMutationRepoImpl
import com.example.util.simpletimetracker.data_local.record.RecordRepoImpl
import com.example.util.simpletimetracker.data_local.record.RunningRecordDataLocalMapper
import com.example.util.simpletimetracker.data_local.record.RunningRecordRepoImpl
import com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoRepoImpl
import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordTag.RunningRecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordType.RecordTypeDBO
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.record.interactor.RecordTimeValidator
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeClockRecordDetailsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val start = 1_700_000_000_123L
    private val mapper = RecordDataLocalMapper()

    @Test
    fun photoCopySurvivesDeletingOriginalAndRejectsInvalidImages() = runBlocking {
        withDatabase { db, _, photos ->
            val source = sourceImage()
            val photo = photos.importPhoto(Uri.fromFile(source).toString())
            try {
                source.delete()
                assertNotNull(BitmapFactory.decodeFile(photos.path(photo)))
                assertEquals(photos.path(photo), RecordPhotoRepoImpl(context, db).path(photo))
                val invalid = File.createTempFile("invalid-image-", ".txt", context.cacheDir).apply { writeText("not an image") }
                try {
                    assertTrue(runCatching { photos.importPhoto(Uri.fromFile(invalid).toString()) }.exceptionOrNull() is IOException)
                } finally {
                    invalid.delete()
                }
            } finally {
                File(photos.path(photo)).delete()
            }
        }
    }

    @Test
    fun detailsCanBeSavedChangedAndClearedWithoutLosingEmptyRatings() = runBlocking {
        withDatabase { db, mutations, photos ->
            val identity = LifeClockSettingsRepoImpl(db).getOrCreate()
            val source = sourceImage()
            val photo = photos.importPhoto(Uri.fromFile(source).toString())
            source.delete()
            try {
                val record = record().copy(
                    summary = "tea and reading",
                    tags = listOf(
                        RecordBase.Tag(identity.happinessTagId, 4.0, start + 1), RecordBase.Tag(identity.meaningTagId, null),
                    ),
                )
                val id = mutations.saveDetails(record, listOf(photo))
                val saved = mapper.map(requireNotNull(db.recordDao().get(id)))
                assertEquals(record.summary, saved.summary)
                assertEquals(record.tags, saved.tags)
                assertEquals(1, photos.get(id, false).size)
                photos.discardUnused(listOf(photo))
                assertTrue(File(photos.path(photo)).exists())
                val cleared = saved.copy(summary = "", comment = "", tags = saved.tags.map { it.copy(numericValue = null, ratedAt = null) })
                mutations.saveDetails(cleared, emptyList())
                assertEquals(cleared, mapper.map(requireNotNull(db.recordDao().get(id))))
                assertTrue(photos.get(id, false).isEmpty())
                assertTrue(File(photos.path(photo)).exists())
                photos.discardUnused(listOf(photo))
                assertTrue(!File(photos.path(photo)).exists())
            } finally {
                File(photos.path(photo)).delete()
            }
        }
    }

    @Test
    fun runningDetailsAndPhotoFollowTypeChangeAndStopButRatingsAreNotInheritedAtStart() = runBlocking {
        withDatabase { db, mutations, photos ->
            val identity = LifeClockSettingsRepoImpl(db).getOrCreate()
            val rating = RecordBase.Tag(identity.happinessTagId, 5.0, start + 1)
            val original = RunningRecord(1, start, "notes", listOf(rating))
            mutations.start(original, start)
            assertTrue(db.runningRecordDao().get(1)?.recordTags.orEmpty().isEmpty())
            val source = sourceImage()
            val photo = photos.importPhoto(Uri.fromFile(source).toString())
            source.delete()
            try {
                val changed = original.copy(id = 2, summary = "walk", tags = listOf(rating))
                mutations.editRunningDetails(1, changed, listOf(photo))
                assertNull(db.runningRecordDao().get(1))
                assertEquals(1, photos.get(2, true).size)
                mutations.stop(2, start + 760_123)
                val ended = mapper.map(db.recordDao().getAll().single())
                assertEquals("walk", ended.summary)
                assertEquals(listOf(rating), ended.tags)
                assertEquals(1, photos.get(ended.id, false).size)
                assertTrue(photos.get(2, true).isEmpty())
            } finally {
                File(photos.path(photo)).delete()
            }
        }
    }

    @Test
    fun photoWriteFailureRollsBackTextRatingsAndAssociations() = runBlocking {
        withDatabase { db, mutations, photos ->
            val source = sourceImage()
            val photo = photos.importPhoto(Uri.fromFile(source).toString())
            source.delete()
            try {
                val id = mutations.saveDetails(record(), emptyList())
                val before = mapper.map(requireNotNull(db.recordDao().get(id)))
                db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo BEFORE INSERT ON recordPhotos BEGIN SELECT RAISE(ABORT, 'test failure'); END")
                assertTrue(runCatching { mutations.saveDetails(before.copy(summary = "changed", tags = listOf(RecordBase.Tag(7, 4.0))), listOf(photo)) }.isFailure)
                assertEquals(before, mapper.map(requireNotNull(db.recordDao().get(id))))
                assertTrue(photos.get(id, false).isEmpty())
            } finally {
                File(photos.path(photo)).delete()
            }
        }
    }

    @Test
    fun builtInScoresRejectFractionsAndOutOfRangeWithoutWriting() = runBlocking {
        withDatabase { db, mutations, _ ->
            val identity = LifeClockSettingsRepoImpl(db).getOrCreate()
            for (score in listOf(0.0, 2.5, 6.0, Double.NaN)) {
                val invalid = record().copy(tags = listOf(RecordBase.Tag(identity.happinessTagId, score)))
                assertTrue(runCatching { mutations.saveDetails(invalid, emptyList()) }.exceptionOrNull() is RecordMutationException)
            }
            assertTrue(db.recordDao().getAll().isEmpty())
        }
    }

    private fun record() = Record(typeId = 1, timeStarted = start, timeEnded = start + 60_123, comment = "notes", tags = emptyList())

    private fun sourceImage(): File {
        val file = File.createTempFile("source-image-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private suspend fun withDatabase(block: suspend (AppDatabase, RecordMutationRepoImpl, RecordPhotoRepoImpl) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            for (id in 1L..2L) db.recordTypeDao().insert(RecordTypeDBO(id, "type $id", "", 0, "", false, defaultDuration = 0, note = ""))
            val runningMapper = RunningRecordDataLocalMapper()
            val mutations = RecordMutationRepoImpl(
                db, RecordRepoImpl(db.recordDao(), mapper), RunningRecordRepoImpl(db.runningRecordDao(), runningMapper),
                mapper, runningMapper, RecordToRecordTagDataLocalMapper(), RunningRecordToRecordTagDataLocalMapper(), RecordTimeValidator(), CurrentTimestampProvider(),
            )
            block(db, mutations, RecordPhotoRepoImpl(context, db))
        } finally {
            db.close()
        }
    }
}

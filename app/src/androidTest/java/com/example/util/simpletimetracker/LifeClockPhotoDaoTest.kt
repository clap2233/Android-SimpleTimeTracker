package com.example.util.simpletimetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.record.RecordDBO
import com.example.util.simpletimetracker.data_local.record.RunningRecordDBO
import com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoDBO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeClockPhotoDaoTest {

    @Test
    fun updatingExistingRecordsPreservesTheirPhotosAndIds() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        try {
            val record = RecordDBO(id = 1, typeId = 2, timeStarted = 123, timeEnded = 900123, comment = "before", tagId = 0)
            val running = RunningRecordDBO(id = 2, timeStarted = 900123, comment = "before", tagId = 0)
            assertEquals(1L, database.recordDao().insert(record))
            assertEquals(2L, database.runningRecordDao().insert(running))
            val photo = RecordPhotoDBO(recordId = 1, relativePath = "photos/a.jpg", sha256 = "a".repeat(64))
            database.recordPhotoDao().upsert(photo)
            database.recordPhotoDao().upsert(photo.copy(recordId = null, runningRecordId = 2))

            assertEquals(1L, database.recordDao().insert(record.copy(comment = "after")))
            assertEquals(2L, database.runningRecordDao().insert(running.copy(comment = "after")))
            assertEquals(1, database.recordPhotoDao().getByRecord(1).size)
            assertEquals(1, database.recordPhotoDao().getByRunningRecord(2).size)
            assertEquals("after", database.recordDao().get(1)?.record?.comment)

            database.recordPhotoDao().transferToRecord(runningRecordId = 2, recordId = 1)
            database.runningRecordDao().delete(2)
            assertEquals(2, database.recordPhotoDao().getByRecord(1).size)
            database.recordDao().delete(1)
            assertEquals(emptyList<RecordPhotoDBO>(), database.recordPhotoDao().getAll())
        } finally {
            database.close()
        }
    }
}

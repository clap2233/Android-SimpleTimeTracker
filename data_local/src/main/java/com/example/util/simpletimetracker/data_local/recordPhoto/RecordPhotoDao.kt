package com.example.util.simpletimetracker.data_local.recordPhoto

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RecordPhotoDao {

    @Query("SELECT * FROM recordPhotos ORDER BY record_id, running_record_id, position, id")
    suspend fun getAll(): List<RecordPhotoDBO>

    @Query("SELECT * FROM recordPhotos WHERE record_id = :recordId ORDER BY position, id")
    suspend fun getByRecord(recordId: Long): List<RecordPhotoDBO>

    @Query("SELECT * FROM recordPhotos WHERE running_record_id = :runningRecordId ORDER BY position, id")
    suspend fun getByRunningRecord(runningRecordId: Long): List<RecordPhotoDBO>

    @Upsert
    suspend fun upsert(photo: RecordPhotoDBO): Long

    @Query("UPDATE recordPhotos SET record_id = :recordId, running_record_id = NULL WHERE running_record_id = :runningRecordId")
    suspend fun transferToRecord(runningRecordId: Long, recordId: Long)

    @Query("DELETE FROM recordPhotos WHERE id = :id")
    suspend fun delete(id: Long)
}

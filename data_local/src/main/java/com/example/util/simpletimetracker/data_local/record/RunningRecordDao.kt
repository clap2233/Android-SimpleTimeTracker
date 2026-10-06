package com.example.util.simpletimetracker.data_local.record

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface RunningRecordDao {

    @Transaction
    @Query("select exists(select 1 from runningRecords)")
    suspend fun isEmpty(): Long

    @Transaction
    @Query("SELECT * FROM runningRecords")
    suspend fun getAll(): List<RunningRecordWithRecordTagsDBO>

    @Transaction
    @Query("SELECT * FROM runningRecords WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RunningRecordWithRecordTagsDBO?

    @Upsert
    suspend fun upsert(record: RunningRecordDBO): Long

    @Transaction
    suspend fun insert(record: RunningRecordDBO): Long {
        val insertedId = upsert(record)
        return if (insertedId == -1L) record.id else insertedId
    }

    @Query("DELETE FROM runningRecords WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM runningRecords")
    suspend fun clear()
}

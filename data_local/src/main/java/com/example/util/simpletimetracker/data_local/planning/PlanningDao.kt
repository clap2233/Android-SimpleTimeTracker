package com.example.util.simpletimetracker.data_local.planning

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PlanningDao {
    @Query("DELETE FROM planTasks")
    suspend fun clearTasks()

    @Query("DELETE FROM planSchedules")
    suspend fun clearSchedules()

    @Query("DELETE FROM planExceptions")
    suspend fun clearExceptions()

    @Query("DELETE FROM planRecordLinks")
    suspend fun clearLinks()

    @Query("SELECT * FROM planTasks ORDER BY id")
    suspend fun planTasks(): List<PlanTaskDBO>

    @Upsert
    suspend fun save(value: PlanTaskDBO)

    @Query("SELECT * FROM planSchedules ORDER BY id")
    suspend fun planSchedules(): List<PlanScheduleDBO>

    @Upsert
    suspend fun save(value: PlanScheduleDBO)

    @Query("SELECT * FROM planExceptions ORDER BY id")
    suspend fun planExceptions(): List<PlanExceptionDBO>

    @Upsert
    suspend fun save(value: PlanExceptionDBO)

    @Query("DELETE FROM planExceptions WHERE id = :id")
    suspend fun deleteException(id: String)

    @Query("SELECT * FROM planRecordLinks ORDER BY id")
    suspend fun planRecordLinks(): List<PlanRecordLinkDBO>

    @Upsert
    suspend fun save(value: PlanRecordLinkDBO)

    @Query("DELETE FROM planRecordLinks WHERE id = :id")
    suspend fun deleteLink(id: String)
}

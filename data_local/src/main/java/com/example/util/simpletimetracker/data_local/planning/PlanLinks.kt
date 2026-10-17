package com.example.util.simpletimetracker.data_local.planning

import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.planning.PlanRecordLink
import java.util.UUID

/** Called only inside the record mutation's database transaction. */
object PlanLinks {
    private suspend fun all(db: AppDatabase) = db.planningDao().planRecordLinks().map { PlanningJson.link(it.contentJson) }
    private suspend fun save(db: AppDatabase, link: PlanRecordLink) = db.planningDao().save(PlanRecordLinkDBO(link.id, PlanningJson.encode(link)))

    suspend fun finish(db: AppDatabase, runningId: Long, recordId: Long, start: Long, end: Long) {
        all(db).filter { it.runningRecordId == runningId }.forEach { link ->
            val from = maxOf(start, link.timeStarted)
            if (end > from) save(db, link.copy(recordId = recordId, runningRecordId = null, timeStarted = from, timeEnded = end))
            else db.planningDao().deleteLink(link.id)
        }
    }

    suspend fun moveRunning(db: AppDatabase, oldId: Long, newId: Long, start: Long) {
        all(db).filter { it.runningRecordId == oldId }.forEach { save(db, it.copy(runningRecordId = newId, timeStarted = maxOf(start, it.timeStarted))) }
    }

    suspend fun merge(db: AppDatabase, oldId: Long, newId: Long) {
        all(db).filter { it.recordId == oldId }.forEach { save(db, it.copy(recordId = newId)) }
    }

    suspend fun split(db: AppDatabase, oldId: Long, beforeId: Long, boundary: Long) {
        all(db).filter { it.recordId == oldId }.forEach { link ->
            val end = requireNotNull(link.timeEnded)
            when {
                end <= boundary -> save(db, link.copy(recordId = beforeId))
                link.timeStarted >= boundary -> Unit
                else -> {
                    save(db, link.copy(timeStarted = boundary))
                    save(db, link.copy(id = UUID.randomUUID().toString(), recordId = beforeId, timeEnded = boundary))
                }
            }
        }
    }

    suspend fun clip(db: AppDatabase, id: Long, start: Long, end: Long) {
        all(db).filter { it.recordId == id }.forEach { link ->
            val from = maxOf(start, link.timeStarted)
            val until = minOf(end, requireNotNull(link.timeEnded))
            if (until > from) save(db, link.copy(timeStarted = from, timeEnded = until)) else db.planningDao().deleteLink(link.id)
        }
    }

    suspend fun removeMissing(db: AppDatabase) {
        val records = db.recordDao().getAll().map { it.record.id }.toSet()
        val running = db.runningRecordDao().getAll().map { it.runningRecord.id }.toSet()
        all(db).filter { it.recordId?.let(records::contains) == false || it.runningRecordId?.let(running::contains) == false }
            .forEach { db.planningDao().deleteLink(it.id) }
    }
}

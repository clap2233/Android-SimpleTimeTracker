package com.example.util.simpletimetracker.data_local.planning

import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import javax.inject.Inject

class PlanningRepoImpl @Inject constructor(private val database: AppDatabase, private val restoreGate: DataRestoreGate) : PlanningRepo {
    private val dao get() = database.planningDao()
    override suspend fun clear() = write {
        dao.clearLinks()
        dao.clearExceptions()
        dao.clearSchedules()
        dao.clearTasks()
    }
    override suspend fun tasks() = dao.planTasks().map { PlanningJson.task(it.contentJson) }
    override suspend fun schedules() = dao.planSchedules().map { PlanningJson.schedule(it.contentJson) }
    override suspend fun exceptions() = dao.planExceptions().map { PlanningJson.exception(it.contentJson) }
    override suspend fun links() = dao.planRecordLinks().map { PlanningJson.link(it.contentJson) }
    override suspend fun save(task: PlanTask) = write { dao.save(PlanTaskDBO(task.id, PlanningJson.encode(task))) }
    override suspend fun save(schedule: PlanSchedule) = write {
        require(schedule.taskId == null || tasks().any { it.id == schedule.taskId })
        dao.save(PlanScheduleDBO(schedule.id, PlanningJson.encode(schedule)))
    }
    override suspend fun save(exception: PlanException) = write {
        require(schedules().any { it.id == exception.scheduleId })
        dao.save(PlanExceptionDBO(exception.id, PlanningJson.encode(exception)))
    }
    override suspend fun save(link: PlanRecordLink) = write {
        require(link.recordId?.let { database.recordDao().get(it) != null } ?: (database.runningRecordDao().get(requireNotNull(link.runningRecordId)) != null))
        dao.save(PlanRecordLinkDBO(link.id, PlanningJson.encode(link)))
    }
    override suspend fun editSchedule(id: String, date: String, replacement: PlanSchedule?, following: Boolean) = write {
        val original = schedules().single { it.id == id }
        val existing = exceptions().firstOrNull { it.scheduleId == id && it.date == date }
        val result = PlanChanges.edit(original, date, replacement, following, existing)
        require(replacement?.taskId == null || tasks().any { it.id == replacement.taskId })
        if (following) exceptions().filter { it.scheduleId == id && it.date >= date }
            .forEach { dao.deleteException(it.id) }
        result.schedules.forEach { dao.save(PlanScheduleDBO(it.id, PlanningJson.encode(it))) }
        result.exceptions.forEach { dao.save(PlanExceptionDBO(it.id, PlanningJson.encode(it))) }
    }
    private suspend fun write(action: suspend () -> Unit) {
        val token = restoreGate.token()
        database.withTransaction { check(restoreGate.canWrite(token)); action() }
    }
}

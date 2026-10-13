package com.example.util.simpletimetracker.data_local.recordShortcut

import com.example.util.simpletimetracker.data_local.base.logDataAccess
import com.example.util.simpletimetracker.data_local.base.withLockedCache
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.recordShortcut.repo.RecordShortcutRepo
import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordShortcutRepoImpl @Inject constructor(
    private val dao: RecordShortcutDao,
    private val mapper: RecordShortcutDataLocalMapper,
    private val database: AppDatabase,
    private val gate: DataRestoreGate,
    private val tagMapper: com.example.util.simpletimetracker.data_local.recordTag.RecordShortcutToRecordTagDataLocalMapper,
) : RecordShortcutRepo {

    private var cache: List<RecordShortcut>? = null

    private val mutex: Mutex = Mutex()

    override suspend fun getAll(): List<RecordShortcut> = withContext(Dispatchers.IO) {
        logDataAccess("getAll")
        dao.getAll().map(mapper::map)
    }

    override suspend fun getByType(typeIds: List<Long>): List<RecordShortcut> = withContext(Dispatchers.IO) {
        logDataAccess("getByType")
        dao.getByType(typeIds).map(mapper::map)
    }

    override suspend fun get(id: Long): RecordShortcut? = mutex.withLockedCache(
        logMessage = "get",
        accessCache = { cache?.firstOrNull { it.id == id } },
        accessSource = { dao.get(id)?.let(mapper::map) },
    )

    override suspend fun add(recordShortcut: RecordShortcut): Long {
        val token = gate.token()
        return mutex.withLockedCache(
            logMessage = "add",
            accessSource = {
                database.withTransaction {
                    if (!gate.canWrite(token)) throw RecordMutationException(RecordMutationError.DATA_RESTORING)
                    val id = dao.insert(recordShortcut.let(mapper::map))
                    val target = recordShortcut.target as? RecordShortcut.Target.Record
                    val identity = database.lifeClockSettingsDao().get()
                    val ratings = identity?.let { setOf(it.happinessTagId, it.meaningTagId) }.orEmpty()
                    val tags = target?.tags.orEmpty().filterNot { it.tagId in ratings }
                    tags.forEach { require(database.recordTagDao().get(it.tagId) != null) }
                    database.recordShortcutToRecordTagDao().insert(tags.map { tagMapper.map(id, it) })
                    id
                }
            },
            afterSourceAccess = { clearCache() },
        )
    }

    override suspend fun update(recordShortcut: RecordShortcut) = mutex.withLockedCache(
        logMessage = "update",
        accessSource = { dao.update(recordShortcut.let(mapper::map)) },
        afterSourceAccess = { clearCache() },
    )

    override suspend fun remove(id: Long) = mutex.withLockedCache(
        logMessage = "remove",
        accessSource = { dao.delete(id) },
        afterSourceAccess = { clearCache() },
    )

    override suspend fun removeByType(typeId: Long) = mutex.withLockedCache(
        logMessage = "removeByType",
        accessSource = { dao.deleteByType(typeId) },
        afterSourceAccess = { clearCache() },
    )

    override suspend fun clear() = mutex.withLockedCache(
        logMessage = "clear",
        accessSource = { dao.clear() },
        afterSourceAccess = { clearCache() },
    )

    private fun clearCache() {
        cache = null
    }
    suspend fun invalidateCache() {
        mutex.withLock { cache = null }
    }
}

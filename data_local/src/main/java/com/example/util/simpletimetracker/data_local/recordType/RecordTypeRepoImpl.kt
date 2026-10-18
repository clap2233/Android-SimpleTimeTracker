package com.example.util.simpletimetracker.data_local.recordType

import com.example.util.simpletimetracker.data_local.base.withLockedCache
import com.example.util.simpletimetracker.domain.extension.removeIf
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.recordType.repo.RecordTypeRepo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.rhythm.ActivityPolicy

@Singleton
class RecordTypeRepoImpl @Inject constructor(
    private val recordTypeDao: RecordTypeDao,
    private val recordTypeDataLocalMapper: RecordTypeDataLocalMapper,
    private val database: AppDatabase,
) : RecordTypeRepo {

    private var cache: List<RecordType>? = null
    private val mutex: Mutex = Mutex()

    override suspend fun getAll(): List<RecordType> = mutex.withLockedCache(
        logMessage = "getAll",
        accessCache = { cache },
        accessSource = { recordTypeDao.getAll().map(recordTypeDataLocalMapper::map) },
        afterSourceAccess = { cache = it },
    )

    override suspend fun get(id: Long): RecordType? = mutex.withLockedCache(
        logMessage = "get id",
        accessCache = { cache?.firstOrNull { it.id == id } },
        accessSource = { recordTypeDao.get(id)?.let(recordTypeDataLocalMapper::map) },
    )

    override suspend fun get(name: String): List<RecordType> = mutex.withLockedCache(
        logMessage = "get name",
        accessCache = { cache?.filter { it.name == name } },
        accessSource = { recordTypeDao.get(name).map(recordTypeDataLocalMapper::map) },
    )

    override suspend fun add(recordType: RecordType): Long = mutex.withLockedCache(
        logMessage = "add",
        accessSource = { database.withTransaction {
            recordType.parentTypeId?.let { parentId ->
                require(parentId != recordType.id)
                require(recordTypeDao.get(parentId)?.parentTypeId == null && recordTypeDao.get(parentId) != null)
                require(recordTypeDao.getAll().none { it.parentTypeId == recordType.id && recordType.id != 0L })
            }
            require(recordType.presetKey == null || recordTypeDao.getAll().none { it.id != recordType.id && it.presetKey == recordType.presetKey })
            recordTypeDao.insert(recordType.let(recordTypeDataLocalMapper::map))
        } },
        afterSourceAccess = { cache = null },
    )

    override suspend fun archive(id: Long) = mutex.withLockedCache(
        logMessage = "archive",
        accessSource = { recordTypeDao.archive(id) },
        afterSourceAccess = { cache = cache?.map { if (it.id == id) it.copy(hidden = true) else it } },
    )

    override suspend fun restore(id: Long) = mutex.withLockedCache(
        logMessage = "restore",
        accessSource = { recordTypeDao.restore(id) },
        afterSourceAccess = { cache = cache?.map { if (it.id == id) it.copy(hidden = false) else it } },
    )

    override suspend fun remove(id: Long) = mutex.withLockedCache(
        logMessage = "remove",
        accessSource = { database.withTransaction {
            val all = recordTypeDao.getAll().map(recordTypeDataLocalMapper::map)
            all.filter { it.parentTypeId == id }.forEach { child ->
                recordTypeDao.insert(recordTypeDataLocalMapper.map(child.copy(parentTypeId = null, timerPolicy = ActivityPolicy.resolve(child, all))))
            }
            recordTypeDao.delete(id)
        } },
        afterSourceAccess = { cache = null },
    )

    override suspend fun clear() = mutex.withLockedCache(
        logMessage = "clear",
        accessSource = { recordTypeDao.clear() },
        afterSourceAccess = { cache = null },
    )
    suspend fun invalidateCache() {
        mutex.withLock { cache = null }
    }
}

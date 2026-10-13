package com.example.util.simpletimetracker.data_local.recordTag

import com.example.util.simpletimetracker.data_local.base.withLockedCache
import com.example.util.simpletimetracker.domain.extension.removeIf
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTypeToDefaultTag
import com.example.util.simpletimetracker.domain.recordTag.repo.RecordTypeToDefaultTagRepo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordTypeToDefaultTagRepoImpl @Inject constructor(
    private val dao: RecordTypeToDefaultTagDao,
    private val mapper: RecordTypeToDefaultTagDataLocalMapper,
) : RecordTypeToDefaultTagRepo {

    private var cache: List<RecordTypeToDefaultTag>? = null
    private val mutex: Mutex = Mutex()

    override suspend fun getAll(): List<RecordTypeToDefaultTag> = mutex.withLockedCache(
        logMessage = "getAll",
        accessCache = { cache },
        accessSource = { dao.getAll().map(mapper::map) },
        afterSourceAccess = { cache = it },
    )

    override suspend fun getTagIdsByType(typeId: Long): Set<Long> = mutex.withLockedCache(
        logMessage = "getTagIdsByType",
        accessCache = { cache?.filter { it.recordTypeId == typeId }?.map { it.tagId }?.toSet() },
        accessSource = { dao.getTagIdsByType(typeId).toSet() },
    )

    override suspend fun getTypeIdsByTag(tagId: Long): Set<Long> = mutex.withLockedCache(
        logMessage = "getTypeIdsByTag",
        accessCache = { cache?.filter { it.tagId == tagId }?.map { it.recordTypeId }?.toSet() },
        accessSource = { dao.getTypeIdsByTag(tagId).toSet() },
    )

    override suspend fun add(recordTypeToTag: RecordTypeToDefaultTag) = mutex.withLockedCache(
        logMessage = "add",
        accessSource = {
            recordTypeToTag
                .let(mapper::map)
                .let { dao.insert(listOf(it)) }
        },
        afterSourceAccess = { cache = null },
    )

    override suspend fun addTypes(tagId: Long, typeIds: List<Long>) = mutex.withLockedCache(
        logMessage = "addTypes",
        accessSource = {
            typeIds.map {
                mapper.map(typeId = it, tagId = tagId)
            }.let { dao.insert(it) }
        },
        afterSourceAccess = { cache = null },
    )

    override suspend fun removeTypes(tagId: Long, typeIds: List<Long>) = mutex.withLockedCache(
        logMessage = "removeTypes",
        accessSource = {
            typeIds.map {
                mapper.map(typeId = it, tagId = tagId)
            }.let { dao.delete(it) }
        },
        afterSourceAccess = { cache = null },
    )

    override suspend fun removeAll(tagId: Long) = mutex.withLockedCache(
        logMessage = "removeAll",
        accessSource = { dao.deleteAll(tagId) },
        afterSourceAccess = { cache = cache?.removeIf { it.tagId == tagId } },
    )

    override suspend fun removeAllByType(typeId: Long) = mutex.withLockedCache(
        logMessage = "removeAllByType",
        accessSource = { dao.deleteAllByType(typeId) },
        afterSourceAccess = { cache = cache?.removeIf { it.recordTypeId == typeId } },
    )

    override suspend fun clear() = mutex.withLockedCache(
        logMessage = "clear",
        accessSource = { dao.clear() },
        afterSourceAccess = { cache = null },
    )
    suspend fun invalidateCache() {
        mutex.withLock { cache = null }
    }
}
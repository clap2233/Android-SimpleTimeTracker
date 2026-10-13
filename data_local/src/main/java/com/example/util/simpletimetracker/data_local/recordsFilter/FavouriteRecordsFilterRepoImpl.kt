package com.example.util.simpletimetracker.data_local.recordsFilter

import com.example.util.simpletimetracker.data_local.base.withLockedCache
import com.example.util.simpletimetracker.domain.record.model.FavouriteRecordsFilter
import com.example.util.simpletimetracker.domain.recordsFilter.repo.FavouriteRecordsFilterRepo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavouriteRecordsFilterRepoImpl @Inject constructor(
    private val dao: FavouriteRecordsFilterDao,
    private val mapper: FavouriteRecordsFilterDataLocalMapper,
) : FavouriteRecordsFilterRepo {

    private var cache: MutableMap<Long, FavouriteRecordsFilter> = mutableMapOf()
    private val mutex: Mutex = Mutex()

    override suspend fun getAll(): List<FavouriteRecordsFilter> = mutex.withLockedCache(
        logMessage = "getAll",
        accessCache = { cache.values.toList().takeIf { it.isNotEmpty() } },
        accessSource = { dao.getAll().mapNotNull(mapper::map) },
        afterSourceAccess = { cache = it.associateBy(FavouriteRecordsFilter::id).toMutableMap() },
    )

    override suspend fun get(id: Long): FavouriteRecordsFilter? = mutex.withLockedCache(
        logMessage = "get",
        accessCache = { cache[id] },
        accessSource = { dao.get(id)?.let(::mapItem) },
        afterSourceAccess = { it?.let { cache.put(id, it) } },
    )

    override suspend fun add(data: FavouriteRecordsFilter) = mutex.withLockedCache(
        logMessage = "add",
        accessSource = { dao.insert(data.let(mapper::map)) },
        afterSourceAccess = { clearCache() },
    )

    override suspend fun remove(id: Long): Unit = mutex.withLockedCache(
        logMessage = "remove",
        accessSource = { dao.get(id)?.main?.let { dao.delete(it) } },
        afterSourceAccess = { clearCache() },
    )

    override suspend fun clear() = mutex.withLockedCache(
        logMessage = "clear",
        accessSource = { dao.clear() },
        afterSourceAccess = { clearCache() },
    )

    private fun clearCache() {
        cache.clear()
    }

    private fun mapItem(
        dbo: FavouriteRecordsFilterDBO,
    ): FavouriteRecordsFilter? {
        return mapper.map(dbo)
    }
    suspend fun invalidateCache() {
        mutex.withLock { cache.clear() }
    }
}
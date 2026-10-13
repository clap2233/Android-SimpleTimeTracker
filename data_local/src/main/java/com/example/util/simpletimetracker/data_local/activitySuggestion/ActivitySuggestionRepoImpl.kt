package com.example.util.simpletimetracker.data_local.activitySuggestion

import com.example.util.simpletimetracker.data_local.base.withLockedCache
import com.example.util.simpletimetracker.domain.activitySuggestion.model.ActivitySuggestion
import com.example.util.simpletimetracker.domain.activitySuggestion.repo.ActivitySuggestionRepo
import com.example.util.simpletimetracker.domain.extension.removeIf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivitySuggestionRepoImpl @Inject constructor(
    private val dao: ActivitySuggestionDao,
    private val mapper: ActivitySuggestionDataLocalMapper,
) : ActivitySuggestionRepo {

    private var cache: List<ActivitySuggestion>? = null
    private val mutex: Mutex = Mutex()

    override suspend fun getAll(): List<ActivitySuggestion> = mutex.withLockedCache(
        logMessage = "getAll",
        accessCache = { cache },
        accessSource = { dao.getAll().map(mapper::map) },
        afterSourceAccess = { cache = it },
    )

    override suspend fun get(id: Long): ActivitySuggestion? = mutex.withLockedCache(
        logMessage = "get",
        accessCache = { cache?.firstOrNull { it.id == id } },
        accessSource = { dao.get(id)?.let(mapper::map) },
    )

    override suspend fun getByTypeId(typeId: Long): List<ActivitySuggestion> = mutex.withLockedCache(
        logMessage = "getByTypeId",
        accessCache = { cache?.filter { it.forTypeId == typeId } },
        accessSource = { dao.getByTypeId(typeId).map(mapper::map) },
    )

    override suspend fun add(activityFilters: List<ActivitySuggestion>) = mutex.withLockedCache(
        logMessage = "add",
        accessSource = { dao.insert(activityFilters.map(mapper::map)) },
        afterSourceAccess = { cache = null },
    )

    override suspend fun remove(ids: List<Long>) = mutex.withLockedCache(
        logMessage = "remove",
        accessSource = { dao.delete(ids) },
        afterSourceAccess = { cache = cache?.removeIf { it.id in ids } },
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
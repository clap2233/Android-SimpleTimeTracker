package com.example.util.simpletimetracker.data_local.complexRule

import com.example.util.simpletimetracker.data_local.base.withLockedCache
import com.example.util.simpletimetracker.domain.complexRule.model.ComplexRule
import com.example.util.simpletimetracker.domain.complexRule.repo.ComplexRuleRepo
import com.example.util.simpletimetracker.domain.extension.removeIf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComplexRuleRepoImpl @Inject constructor(
    private val dao: ComplexRulesDao,
    private val mapper: ComplexRuleDataLocalMapper,
) : ComplexRuleRepo {

    private var cache: List<ComplexRule>? = null
    private val mutex: Mutex = Mutex()

    override suspend fun isEmpty(): Boolean = mutex.withLockedCache(
        logMessage = "isEmpty",
        accessCache = { cache?.isEmpty() },
        accessSource = { dao.isEmpty() == 0L },
    )

    override suspend fun getAll(): List<ComplexRule> = mutex.withLockedCache(
        logMessage = "getAll",
        accessCache = { cache },
        accessSource = { dao.getAll().map(mapper::map) },
        afterSourceAccess = { cache = it },
    )

    override suspend fun get(id: Long): ComplexRule? = mutex.withLockedCache(
        logMessage = "get id",
        accessCache = { cache?.firstOrNull { it.id == id } },
        accessSource = { dao.get(id)?.let(mapper::map) },
    )

    override suspend fun add(favouriteIcon: ComplexRule): Long = mutex.withLockedCache(
        logMessage = "add",
        accessSource = { dao.insert(favouriteIcon.let(mapper::map)) },
        afterSourceAccess = { cache = null },
    )

    override suspend fun disable(id: Long) = mutex.withLockedCache(
        logMessage = "disable",
        accessSource = { dao.disable(id) },
        afterSourceAccess = { cache = cache?.map { if (it.id == id) it.copy(disabled = true) else it } },
    )

    override suspend fun enable(id: Long) = mutex.withLockedCache(
        logMessage = "enable",
        accessSource = { dao.enable(id) },
        afterSourceAccess = { cache = cache?.map { if (it.id == id) it.copy(disabled = false) else it } },
    )

    override suspend fun remove(id: Long) = mutex.withLockedCache(
        logMessage = "remove",
        accessSource = { dao.delete(id) },
        afterSourceAccess = { cache = cache?.removeIf { it.id == id } },
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
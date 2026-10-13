package com.example.util.simpletimetracker.domain.backup.interactor

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRestoreGate @Inject constructor() {
    private val restoring = AtomicBoolean(false)
    private val version = AtomicLong(0)
    fun token(): Long = version.get()
    fun canWrite(token: Long): Boolean = !restoring.get() && token == version.get()
    fun begin(): Boolean = restoring.compareAndSet(false, true)
    fun committed() {
        version.incrementAndGet()
    }
    fun end() {
        restoring.set(false)
    }
}

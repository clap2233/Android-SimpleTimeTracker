package com.example.util.simpletimetracker.domain.backup.repo

data class CloudBackupState(val serverUrl: String = "", val configured: Boolean = false, val pending: Boolean = false, val lastSuccess: Long? = null)
data class CloudBackupResult(val snapshotId: String, val createdAt: Long, val isLatest: Boolean)
class CloudBackupException(val code: String) : Exception(code)

interface CloudBackupRepo {
    suspend fun state(): CloudBackupState
    suspend fun configure(serverUrl: String, writeToken: String)
    suspend fun upload(): CloudBackupResult
    suspend fun discardPending()
}

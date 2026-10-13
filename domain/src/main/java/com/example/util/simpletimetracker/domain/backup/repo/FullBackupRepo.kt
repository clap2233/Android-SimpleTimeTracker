package com.example.util.simpletimetracker.domain.backup.repo

import com.example.util.simpletimetracker.domain.backup.model.FullBackupPreview

interface FullBackupRepo {
    suspend fun export(uri: String)
    suspend fun prepareRestore(uri: String): FullBackupPreview
    suspend fun restore(token: String)
    suspend fun discard(token: String)
    suspend fun exportRecoveryCopy(uri: String)
    suspend fun hasRecoveryCopy(): Boolean
    suspend fun exportCsv(uri: String)
    suspend fun exportIcs(uri: String)
    suspend fun recoverInterruptedRestore()
}

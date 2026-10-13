package com.example.util.simpletimetracker.domain.backup.model

data class FullBackupPreview(
    val token: String,
    val createdAt: Long,
    val recordCount: Int,
    val runningCount: Int,
    val photoCount: Int,
    val reviewCount: Int,
    val repairCount: Int,
)

enum class FullBackupFailure { INVALID_FORMAT, UNSUPPORTED_VERSION, INVALID_MEDIA, NO_SPACE, IO, BUSY }

class FullBackupException(val failure: FullBackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)

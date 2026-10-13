package com.example.util.simpletimetracker.domain.backup.repo

data class CloudSnapshotFiles(val snapshotJson: String, val photos: Map<String, String>)

interface CloudSnapshotSource {
    suspend fun captureForCloud(): CloudSnapshotFiles
}

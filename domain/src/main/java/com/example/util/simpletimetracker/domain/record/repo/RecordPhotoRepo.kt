package com.example.util.simpletimetracker.domain.record.repo

import com.example.util.simpletimetracker.domain.record.model.RecordPhoto

interface RecordPhotoRepo {
    suspend fun get(ownerId: Long, running: Boolean): List<RecordPhoto>
    suspend fun importPhoto(uri: String): RecordPhoto
    fun path(photo: RecordPhoto): String
    suspend fun discardUnused(photos: List<RecordPhoto>)
}

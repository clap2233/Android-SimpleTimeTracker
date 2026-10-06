package com.example.util.simpletimetracker.domain.recordTag.model

data class RecordToRecordTag(
    val recordId: Long,
    val recordTagId: Long,
    val recordTagNumericValue: Double?,
    val ratedAt: Long? = null,
)

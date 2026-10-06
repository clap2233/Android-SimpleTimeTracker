package com.example.util.simpletimetracker.domain.recordTag.model

data class RunningRecordToRecordTag(
    val runningRecordId: Long,
    val recordTagId: Long,
    val recordTagNumericValue: Double?,
    val ratedAt: Long? = null,
)

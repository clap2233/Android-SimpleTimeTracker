package com.example.util.simpletimetracker.domain.record.model

sealed interface RecordBase {
    val typeIds: List<Long>
    val timeStarted: Long
    val timeEnded: Long
    val comment: String
    val tags: List<Tag>

    val duration: Long get() = timeEnded - timeStarted

    data class Tag(
        val tagId: Long,
        val numericValue: Double?,
        val ratedAt: Long? = null,
    )
}

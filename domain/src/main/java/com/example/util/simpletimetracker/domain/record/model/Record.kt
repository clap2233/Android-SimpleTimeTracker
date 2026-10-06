package com.example.util.simpletimetracker.domain.record.model

import java.util.TimeZone

data class Record(
    val id: Long = 0,
    val typeId: Long,
    override val timeStarted: Long,
    override val timeEnded: Long,
    override val comment: String,
    override val tags: List<RecordBase.Tag>,
    val reviewState: RecordReviewState = RecordReviewState.PENDING,
    val summary: String = "",
    val zoneId: String? = TimeZone.getDefault().id,
    val templateId: Long? = null,
) : RecordBase {

    override val typeIds: List<Long> = listOf(typeId)
}

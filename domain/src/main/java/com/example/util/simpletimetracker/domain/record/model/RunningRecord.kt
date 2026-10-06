package com.example.util.simpletimetracker.domain.record.model

import java.util.TimeZone

data class RunningRecord(
    val id: Long,
    override val timeStarted: Long,
    override val comment: String,
    override val tags: List<RecordBase.Tag>,
    val summary: String = "",
    val zoneId: String? = TimeZone.getDefault().id,
    val templateId: Long? = null,
) : RecordBase {

    override val typeIds: List<Long> = listOf(id)
    override val timeEnded: Long get() = System.currentTimeMillis()
}

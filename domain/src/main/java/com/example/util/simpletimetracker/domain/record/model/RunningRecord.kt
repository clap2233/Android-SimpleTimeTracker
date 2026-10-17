package com.example.util.simpletimetracker.domain.record.model

import java.util.TimeZone

fun RunningRecord.operationIdentity(): String = timerSession?.id ?: "legacy:$id:$timeStarted"

data class RunningRecord(
    val id: Long,
    override val timeStarted: Long,
    override val comment: String,
    override val tags: List<RecordBase.Tag>,
    val summary: String = "",
    val zoneId: String? = TimeZone.getDefault().id,
    val templateId: Long? = null,
    val timerSession: com.example.util.simpletimetracker.domain.rhythm.TimerSession? = null,
) : RecordBase {

    override val typeIds: List<Long> = listOf(id)
    override val timeEnded: Long get() = System.currentTimeMillis()
}

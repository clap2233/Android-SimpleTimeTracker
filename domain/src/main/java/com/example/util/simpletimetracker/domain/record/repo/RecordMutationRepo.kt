package com.example.util.simpletimetracker.domain.record.repo

import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.model.RecordPhoto

data class TimerMutation(val stopped: List<RunningRecord>, val started: RunningRecord?)

interface RecordMutationRepo {
    suspend fun saveDetails(record: Record, photos: List<com.example.util.simpletimetracker.domain.record.model.RecordPhoto>): Long
    suspend fun editRunningDetails(originalId: Long, record: RunningRecord, photos: List<com.example.util.simpletimetracker.domain.record.model.RecordPhoto>)
    suspend fun save(records: List<Record>): List<Long>
    suspend fun start(record: RunningRecord, now: Long): TimerMutation
    suspend fun stop(typeId: Long, now: Long): TimerMutation
    suspend fun editRunning(originalId: Long, record: RunningRecord)
    suspend fun merge(record: Record, previousId: Long, tags: List<RecordBase.Tag>?, photos: List<RecordPhoto>? = null)
    suspend fun split(record: Record, boundary: Long, attachmentsBefore: Boolean, photos: List<RecordPhoto>? = null)
    suspend fun adjustBoundary(record: Record, neighborId: Long, boundary: Long, previous: Boolean, neighborRunning: Boolean = false, photos: List<RecordPhoto>? = null)
}

interface RecordMutationFeedback {
    suspend fun show(reason: RecordMutationError)
}

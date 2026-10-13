package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import javax.inject.Inject

class RecordTimeValidator @Inject constructor() {
    fun validate(records: List<Record>, others: List<Record>, running: List<RunningRecord>, now: Long) {
        records.forEach { record ->
            if (record.timeEnded <= record.timeStarted) fail(RecordMutationError.EMPTY_INTERVAL)
            if (record.timeEnded > now) fail(RecordMutationError.FUTURE_TIME)
            if (record.timeEnded - record.timeStarted <= 0) fail(RecordMutationError.EMPTY_INTERVAL)
            if (record.reviewState == RecordReviewState.CONFIRMED && (record.timeEnded - record.timeStarted) % QUARTER != 0L) {
                fail(RecordMutationError.NOT_QUARTER_HOUR)
            }
            if (others.any { overlaps(record, it) } || running.any { it.timeStarted < record.timeEnded }) {
                fail(RecordMutationError.OVERLAP)
            }
        }
        records.forEachIndexed { index, record ->
            if (records.drop(index + 1).any { overlaps(record, it) }) fail(RecordMutationError.OVERLAP)
        }
    }

    fun state(start: Long, end: Long): RecordReviewState =
        if (end > start && (end - start) % QUARTER == 0L) RecordReviewState.CONFIRMED else RecordReviewState.PENDING

    private fun overlaps(first: Record, second: Record): Boolean =
        second.timeEnded > second.timeStarted && first.timeStarted < second.timeEnded && first.timeEnded > second.timeStarted

    private fun fail(reason: RecordMutationError): Nothing = throw RecordMutationException(reason)

    companion object {
        const val QUARTER = 900_000L
    }
}

package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RunningRecordRepo
import com.example.util.simpletimetracker.domain.recordTag.repo.RunningRecordToRecordTagRepo
import javax.inject.Inject
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider

class RunningRecordInteractor @Inject constructor(
    private val runningRecordRepo: RunningRecordRepo,
    private val runningRecordToRecordTagRepo: RunningRecordToRecordTagRepo,
    private val mutations: RecordMutationRepo,
    private val feedback: RecordMutationFeedback,
    private val clock: CurrentTimestampProvider,
) {

    suspend fun isEmpty(): Boolean {
        return runningRecordRepo.isEmpty()
    }

    suspend fun getAll(): List<RunningRecord> {
        return runningRecordRepo.getAll()
    }

    suspend fun get(id: Long): RunningRecord? {
        return runningRecordRepo.get(id)
    }

    suspend fun has(id: Long): Boolean {
        return runningRecordRepo.has(id)
    }

    suspend fun add(runningRecord: RunningRecord): Boolean {
        return try {
            if (runningRecordRepo.has(runningRecord.id)) {
                mutations.editRunning(runningRecord.id, runningRecord)
            } else {
                mutations.start(runningRecord, clock.get())
            }
            true
        } catch (error: RecordMutationException) {
            feedback.show(error.reason)
            false
        }
    }

    suspend fun remove(id: Long) {
        runningRecordToRecordTagRepo.removeAllByRunningRecordId(id)
        runningRecordRepo.remove(id)
    }

    private suspend fun updateTags(
        recordId: Long,
        tags: List<RecordBase.Tag>,
    ) {
        runningRecordToRecordTagRepo.removeAllByRunningRecordId(recordId)
        runningRecordToRecordTagRepo.addRunningRecordTags(recordId, tags)
    }
}

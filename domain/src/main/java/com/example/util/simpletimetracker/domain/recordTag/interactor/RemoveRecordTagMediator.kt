package com.example.util.simpletimetracker.domain.recordTag.interactor

import com.example.util.simpletimetracker.domain.notifications.interactor.UpdateExternalViewsInteractor
import javax.inject.Inject

class RemoveRecordTagMediator @Inject constructor(
    private val recordTagInteractor: RecordTagInteractor,
    private val externalViewsInteractor: UpdateExternalViewsInteractor,
    private val feedback: com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback,
) {

    suspend fun remove(
        tagId: Long,
        fromArchive: Boolean,
    ) {
        if (recordTagInteractor.remove(tagId)) {
            doAfterRemove(tagId, fromArchive)
        } else {
            feedback.show(com.example.util.simpletimetracker.domain.record.model.RecordMutationError.RESERVED_TAG)
        }
    }

    private suspend fun doAfterRemove(
        tagId: Long,
        fromArchive: Boolean,
    ) {
        externalViewsInteractor.onTagRemove(tagId, fromArchive)
    }
}

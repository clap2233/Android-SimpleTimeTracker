package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.base.suspendLazy
import com.example.util.simpletimetracker.domain.notifications.interactor.ActivityStartedStoppedBroadcastInteractor
import com.example.util.simpletimetracker.domain.notifications.interactor.UpdateExternalViewsInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordDataSelectionDialogResult
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import javax.inject.Inject

class AddRunningRecordMediator @Inject constructor(
    private val mutations: RecordMutationRepo,
    private val feedback: RecordMutationFeedback,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val recordInteractor: RecordInteractor,
    private val activityStartedStoppedBroadcastInteractor: ActivityStartedStoppedBroadcastInteractor,
    private val shouldShowRecordDataSelectionInteractor: ShouldShowRecordDataSelectionInteractor,
    private val processRulesInteractor: ProcessRulesInteractor,
    private val updateExternalViewsInteractor: UpdateExternalViewsInteractor,
    private val currentTimestampProvider: CurrentTimestampProvider,
    private val restoreGate: com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate = com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate(),
) {
    suspend fun tryStartTimer(
        typeId: Long,
        updateNotificationSwitch: Boolean = true,
        commentInputAvailable: Boolean = true,
        onNeedToShowTagSelection: suspend (RecordDataSelectionDialogResult) -> Unit,
    ): Boolean {
        if (runningRecordInteractor.get(typeId) != null) return false
        val selection = shouldShowRecordDataSelectionInteractor.execute(typeId, commentInputAvailable)
        return if (selection.fields.isNotEmpty()) {
            onNeedToShowTagSelection(selection)
            false
        } else {
            startTimer(typeId, emptyList(), "", updateNotificationSwitch = updateNotificationSwitch)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun startTimer(
        typeId: Long,
        tags: List<RecordBase.Tag>,
        comment: String,
        timeStarted: StartTime = StartTime.TakeCurrent,
        updateNotificationSwitch: Boolean = true,
        checkDefaultDuration: Boolean = true,
        useSelectedTags: Boolean = false,
        summary: String = "",
        templateId: Long? = null,
    ): Boolean {
        val now = currentTimestampProvider.get()
        val writeToken = restoreGate.token()
        val start = when (timeStarted) {
            is StartTime.Current -> timeStarted.currentTimeStampMs
            is StartTime.TakeCurrent -> now
            is StartTime.Timestamp -> timeStarted.timestampMs
        }
        val rules = processRulesInteractor.getRulesResultForStart(
            typeId = typeId, timeStarted = start,
            prevRecords = suspendLazy { recordInteractor.getAllPrev(start) }, retroactiveTrackingMode = false,
        )
        val selected = if (useSelectedTags) tags else processRulesInteractor.getAllTags(typeId, tags, rules.tags)
        val record = RunningRecord(typeId, start, comment, selected, summary = summary, templateId = templateId)
        if (!restoreGate.canWrite(writeToken)) {
            feedback.show(com.example.util.simpletimetracker.domain.record.model.RecordMutationError.DATA_RESTORING)
            return false
        }
        return try {
            val change = mutations.start(record, now)
            val started = change.started ?: return false
            change.stopped.forEach {
                activityStartedStoppedBroadcastInteractor.onActivityStopped(it.id, it.tags.map(RecordBase.Tag::tagId), it.comment, it.timeStarted)
                updateExternalViewsInteractor.onRunningRecordRemove(it.id, it.tags.map(RecordBase.Tag::tagId), updateWidgets = false, updateNotificationSwitch = false)
            }
            activityStartedStoppedBroadcastInteractor.onActionActivityStarted(typeId, started.tags.map(RecordBase.Tag::tagId), comment)
            updateExternalViewsInteractor.onRunningRecordAdd(typeId, started.tags.map(RecordBase.Tag::tagId), updateNotificationSwitch)
            true
        } catch (error: RecordMutationException) {
            feedback.show(error.reason)
            false
        }
    }

    suspend fun addAfterChange(
        typeId: Long,
        timeStarted: Long,
        comment: String,
        tags: List<RecordBase.Tag>,
        sourceRecord: RunningRecord? = null,
        photos: List<com.example.util.simpletimetracker.domain.record.model.RecordPhoto>? = null,
    ): Boolean {
        val source = sourceRecord ?: return false
        return try {
            val changed = source.copy(id = typeId, timeStarted = timeStarted, comment = comment, tags = tags)
            if (photos == null) mutations.editRunning(source.id, changed) else mutations.editRunningDetails(source.id, changed, photos)
            updateExternalViewsInteractor.onRunningRecordRemove(source.id, source.tags.map(RecordBase.Tag::tagId), updateWidgets = false, updateNotificationSwitch = false)
            updateExternalViewsInteractor.onRunningRecordAdd(typeId, tags.map(RecordBase.Tag::tagId), updateNotificationSwitch = true)
            true
        } catch (error: RecordMutationException) {
            feedback.show(error.reason)
            false
        }
    }

    sealed interface StartTime {
        data class Current(val currentTimeStampMs: Long) : StartTime
        data class Timestamp(val timestampMs: Long) : StartTime
        object TakeCurrent : StartTime
    }
}

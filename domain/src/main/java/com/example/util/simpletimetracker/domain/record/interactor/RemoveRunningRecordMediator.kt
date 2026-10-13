package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.notifications.interactor.ActivityStartedStoppedBroadcastInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroStopInteractor
import com.example.util.simpletimetracker.domain.notifications.interactor.UpdateExternalViewsInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import javax.inject.Inject

class RemoveRunningRecordMediator @Inject constructor(
    private val mutations: RecordMutationRepo,
    private val feedback: RecordMutationFeedback,
    private val clock: CurrentTimestampProvider,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val activityStartedStoppedBroadcastInteractor: ActivityStartedStoppedBroadcastInteractor,
    private val pomodoroStopInteractor: PomodoroStopInteractor,
    private val updateExternalViewsInteractor: UpdateExternalViewsInteractor,
) {

    suspend fun removeWithRecordAdd(
        runningRecord: RunningRecord,
        updateWidgets: Boolean = true,
        updateNotificationSwitch: Boolean = true,
        timeEnded: Long? = null, // null - take current time.
    ) {
        val change = try {
            mutations.stop(runningRecord.id, timeEnded ?: clock.get())
        } catch (error: RecordMutationException) {
            feedback.show(error.reason)
            return
        }
        val stopped = change.stopped.singleOrNull() ?: return
        activityStartedStoppedBroadcastInteractor.onActivityStopped(
            typeId = stopped.id,
            tagIds = stopped.tags.map(RecordBase.Tag::tagId),
            comment = stopped.comment,
            timeStarted = stopped.timeStarted,
        )
        updateExternalViewsInteractor.onRunningRecordRemove(
            typeId = stopped.id,
            tagIds = stopped.tags.map(RecordBase.Tag::tagId),
            updateWidgets = updateWidgets,
            updateNotificationSwitch = updateNotificationSwitch,
        )
        pomodoroStopInteractor.checkAndStop(stopped.id)
    }

    suspend fun remove(
        typeId: Long,
        updateWidgets: Boolean = true,
        updateNotificationSwitch: Boolean = true,
        checkPomodoroStop: Boolean = true,
    ) {
        val runningRecord = runningRecordInteractor.get(typeId)
        runningRecordInteractor.remove(typeId)
        updateExternalViewsInteractor.onRunningRecordRemove(
            typeId = typeId,
            tagIds = runningRecord?.tags.orEmpty().map(RecordBase.Tag::tagId),
            updateWidgets = updateWidgets,
            updateNotificationSwitch = updateNotificationSwitch,
        )
        if (checkPomodoroStop) pomodoroStopInteractor.checkAndStop(typeId)
    }
}

package com.example.util.simpletimetracker.feature_notification.recordType.controller

import com.example.util.simpletimetracker.core.extension.allowDiskRead
import com.example.util.simpletimetracker.domain.notifications.interactor.NotificationActivitySwitchInteractor
import com.example.util.simpletimetracker.domain.notifications.interactor.NotificationTypeInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.feature_notification.activitySwitch.mapper.NotificationControlsMapper
import com.example.util.simpletimetracker.feature_notification.recordType.interactor.ActivityStartStopFromBroadcastInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.example.util.simpletimetracker.domain.record.model.operationIdentity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationTypeBroadcastController @Inject constructor(
    private val running: com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor,
    private val notificationTypeInteractor: NotificationTypeInteractor,
    private val notificationActivitySwitchInteractor: NotificationActivitySwitchInteractor,
    private val activityStartStopFromBroadcastInteractor: ActivityStartStopFromBroadcastInteractor,
    private val notificationControlsMapper: NotificationControlsMapper,
) {

    fun onActionActivityStop(
        typeId: Long,
        expectedSessionId: String?,
    ) {
        if (typeId == 0L) return
        safeLaunch {
            if (expectedSessionId == null || running.get(typeId)?.operationIdentity() != expectedSessionId) return@safeLaunch
            activityStartStopFromBroadcastInteractor.onActionActivityStop(
                typeId = typeId,
                expectedSessionId = expectedSessionId,
            )
        }
    }

    fun onActionTypeClick(
        expectedSessionId: String?,
        from: Int,
        typeId: Long,
        selectedTypeId: Long,
        typesShift: Int,
    ) {
        safeLaunch {
            if (expectedSessionId == null || (running.getAll().singleOrNull()?.operationIdentity() ?: "idle") != expectedSessionId) return@safeLaunch
            activityStartStopFromBroadcastInteractor.onActionTypeClick(
                from = notificationControlsMapper.mapExtraToFrom(
                    extra = from,
                    recordTypeId = typeId,
                    sessionId = expectedSessionId,
                ) ?: return@safeLaunch,
                selectedTypeId = selectedTypeId,
                typesShift = typesShift,
            )
        }
    }

    fun onActionTagClick(
        expectedSessionId: String?,
        from: Int,
        typeId: Long,
        selectedTypeId: Long,
        tagId: Long,
        typesShift: Int,
        tagsShift: Int,
        selectedTags: List<RecordBase.Tag>,
        isMultipleTagAvailable: Boolean,
        requiredValueSelectionTagIds: List<Long>,
    ) {
        safeLaunch {
            if (expectedSessionId == null || (running.getAll().singleOrNull()?.operationIdentity() ?: "idle") != expectedSessionId) return@safeLaunch
            activityStartStopFromBroadcastInteractor.onActionTagClick(
                from = notificationControlsMapper.mapExtraToFrom(
                    extra = from,
                    recordTypeId = typeId,
                    sessionId = expectedSessionId,
                ) ?: return@safeLaunch,
                selectedTypeId = selectedTypeId,
                tagId = tagId,
                typesShift = typesShift,
                tagsShift = tagsShift,
                selectedTags = selectedTags,
                isMultipleTagAvailable = isMultipleTagAvailable,
                requiredValueSelectionTagIds = requiredValueSelectionTagIds,
            )
        }
    }

    fun onActionTagValueSave(
        expectedSessionId: String?,
        from: Int,
        typeId: Long,
        selectedTypeId: Long,
        tagId: Long,
        tagValue: String?,
        typesShift: Int,
        tagsShift: Int,
        selectedTags: List<RecordBase.Tag>,
        isMultipleTagAvailable: Boolean,
        requiredValueSelectionTagIds: List<Long>,
    ) {
        safeLaunch {
            if (expectedSessionId == null || (running.getAll().singleOrNull()?.operationIdentity() ?: "idle") != expectedSessionId) return@safeLaunch
            activityStartStopFromBroadcastInteractor.onActionTagValueSave(
                from = notificationControlsMapper.mapExtraToFrom(
                    extra = from,
                    recordTypeId = typeId,
                    sessionId = expectedSessionId,
                ) ?: return@safeLaunch,
                selectedTypeId = selectedTypeId,
                tagId = tagId,
                tagValue = tagValue,
                typesShift = typesShift,
                tagsShift = tagsShift,
                selectedTags = selectedTags,
                isMultipleTagAvailable = isMultipleTagAvailable,
                requiredValueSelectionTagIds = requiredValueSelectionTagIds,
            )
        }
    }

    fun onRequestUpdate(
        expectedSessionId: String?,
        from: Int,
        typeId: Long,
        selectedTypeId: Long,
        selectedTags: List<RecordBase.Tag>,
        editingTagId: Long?,
        editingTagValueInput: String?,
        typesShift: Int,
        tagsShift: Int,
        isMultipleTagAvailable: Boolean,
        requiredValueSelectionTagIds: List<Long>,
    ) {
        safeLaunch {
            if (expectedSessionId == null || (running.getAll().singleOrNull()?.operationIdentity() ?: "idle") != expectedSessionId) return@safeLaunch
            activityStartStopFromBroadcastInteractor.onRequestUpdate(
                from = notificationControlsMapper.mapExtraToFrom(
                    extra = from,
                    recordTypeId = typeId,
                    sessionId = expectedSessionId,
                ) ?: return@safeLaunch,
                selectedTypeId = selectedTypeId,
                selectedTags = selectedTags,
                editingTagId = editingTagId,
                editingTagValueInput = editingTagValueInput,
                typesShift = typesShift,
                tagsShift = tagsShift,
                isMultipleTagAvailable = isMultipleTagAvailable,
                requiredValueSelectionTagIds = requiredValueSelectionTagIds,
            )
        }
    }

    fun onTypeCancel(
        typeId: Long,
    ) {
        safeLaunch {
            notificationTypeInteractor.checkAndShow(typeId)
        }
    }

    fun onActivitySwitchCancel() {
        safeLaunch {
            notificationActivitySwitchInteractor.updateNotification()
        }
    }

    fun onBootCompleted() {
        safeLaunch {
            notificationTypeInteractor.updateNotifications()
            notificationActivitySwitchInteractor.updateNotification()
        }
    }

    private fun safeLaunch(
        block: suspend CoroutineScope.() -> Unit,
    ) {
        allowDiskRead { MainScope() }.launch(block = block)
    }
}

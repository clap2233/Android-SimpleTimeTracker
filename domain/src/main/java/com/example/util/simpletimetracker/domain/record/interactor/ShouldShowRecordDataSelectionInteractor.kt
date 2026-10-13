package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.base.suspendLazy
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordDataSelectionDialogResult
import com.example.util.simpletimetracker.domain.recordTag.interactor.GetSelectableTagsInteractor
import com.example.util.simpletimetracker.domain.recordTag.interactor.RecordTagInteractor
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTag
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTagValueType
import javax.inject.Inject

class ShouldShowRecordDataSelectionInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val getSelectableTagsInteractor: GetSelectableTagsInteractor,
    private val recordInteractor: RecordInteractor,
    private val recordTagInteractor: RecordTagInteractor,
    private val currentTimestampProvider: CurrentTimestampProvider,
    private val processRulesInteractor: ProcessRulesInteractor,
    private val settingsRepo: com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo,
) {

    suspend fun execute(
        typeId: Long,
        commentInputAvailable: Boolean,
    ): RecordDataSelectionDialogResult {
        val ratingIds = settingsRepo.getOrCreate().ratingTagIds
        val fields = mutableSetOf<RecordDataSelectionDialogResult.Field>()

        if (needToShowTags(typeId, ratingIds)) {
            fields += RecordDataSelectionDialogResult.Field.Tags
        }
        if (needToShowComment(typeId, commentInputAvailable)) {
            fields += RecordDataSelectionDialogResult.Field.Comment
        }

        val currentTime = currentTimestampProvider.get()
        val rulesResult = processRulesInteractor.getRulesResultForStart(
            typeId = typeId,
            timeStarted = currentTime,
            prevRecords = suspendLazy { recordInteractor.getAllPrev(currentTime) },
            retroactiveTrackingMode = prefsInteractor.getRetroactiveTrackingMode(),
        )
        val requiredValueSelectionTagIds = filterNumericTagValueSelectionTagIds(
            tagIds = rulesResult.tagIdsToSelectValueOnStart - ratingIds,
        )
        if (requiredValueSelectionTagIds.isNotEmpty()) {
            // Force tag selection dialog if any tags need value.
            fields += RecordDataSelectionDialogResult.Field.Tags
        }
        val allTags = processRulesInteractor.getAllTags(
            typeId = typeId,
            currentTags = emptyList(),
            tagValuesFromRules = rulesResult.tags,
        )

        return RecordDataSelectionDialogResult(
            fields = fields.toList(),
            preselectedTags = allTags.filterNot { it.tagId in ratingIds },
            requiredValueSelectionTagIds = requiredValueSelectionTagIds,
        )
    }

    private suspend fun needToShowTags(typeId: Long, ratingIds: Set<Long>): Boolean {
        if (!prefsInteractor.getShowRecordTagSelection()) return false

        val excludedActivities = prefsInteractor.getRecordTagSelectionExcludeActivities()

        // Check if activity is excluded from tag dialog.
        return if (typeId !in excludedActivities) {
            // Check if activity has tags.
            val assignableTags = getSelectableTagsInteractor.execute(typeId)
                .filterNot { it.archived || it.id in ratingIds }
            assignableTags.isNotEmpty()
        } else {
            false
        }
    }

    private suspend fun needToShowComment(
        typeId: Long,
        commentInputAvailable: Boolean,
    ): Boolean {
        if (!commentInputAvailable) return false
        if (!prefsInteractor.getShowCommentInput()) return false

        val excludedActivities = prefsInteractor.getCommentInputExcludeActivities()

        // Check if activity is excluded from comment input.
        return typeId !in excludedActivities
    }

    // If tag was with value but when value was disabled - not show value selection.
    private suspend fun filterNumericTagValueSelectionTagIds(tagIds: Set<Long>): List<Long> {
        if (tagIds.isEmpty()) return emptyList()
        val tags = recordTagInteractor.getAll().associateBy(RecordTag::id)
        return tagIds.filter {
            tags[it]?.valueType == RecordTagValueType.NUMERIC && tags[it]?.archived == false
        }
    }
}

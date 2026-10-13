package com.example.util.simpletimetracker.feature_running_records.mapper

import com.example.util.simpletimetracker.core.mapper.ColorMapper
import com.example.util.simpletimetracker.core.mapper.RecordViewDataMapper
import com.example.util.simpletimetracker.core.mapper.RunningRecordViewDataMapper
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.base.DurationFormat
import com.example.util.simpletimetracker.domain.base.UNTRACKED_ITEM_ID
import com.example.util.simpletimetracker.domain.extension.orZero
import com.example.util.simpletimetracker.domain.color.model.AppColor
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTag
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.empty.EmptyViewData
import com.example.util.simpletimetracker.feature_base_adapter.hint.HintViewData
import com.example.util.simpletimetracker.feature_base_adapter.hintBig.HintBigViewData
import com.example.util.simpletimetracker.feature_base_adapter.recordWithHint.RecordWithHintViewData
import com.example.util.simpletimetracker.feature_running_records.R
import javax.inject.Inject

class RunningRecordsViewDataMapper @Inject constructor(
    private val resourceRepo: ResourceRepo,
    private val colorMapper: ColorMapper,
    private val runningRecordViewDataMapper: RunningRecordViewDataMapper,
    private val recordViewDataMapper: RecordViewDataMapper,
) {

    fun mapToTypesEmpty(): ViewHolderType {
        return HintBigViewData(
            text = resourceRepo.getString(
                R.string.running_records_types_empty,
                resourceRepo.getString(R.string.running_records_add_type),
                resourceRepo.getString(R.string.running_records_add_default),
            ),
            infoIconVisible = true,
            closeIconVisible = false,
        )
    }

    fun mapToEmpty(
        startTimersByLongClick: Boolean,
    ): ViewHolderType {
        return EmptyViewData(
            message = "此刻还没有计时",
            hint = if (startTimersByLongClick) "长按活动，开始记录" else "轻点活动，开始记录",
        )
    }

    // TODO add hint about how it works and limitations?
    fun mapToRetroActiveMode(
        typesMap: Map<Long, RecordType>,
        recordTags: List<RecordTag>,
        prevRecords: List<Record>,
        isDarkTheme: Boolean,
        durationFormat: DurationFormat,
        useMilitaryTime: Boolean,
        showSeconds: Boolean,
        startTimersByLongClick: Boolean,
    ): List<ViewHolderType> {
        val result = mutableListOf<ViewHolderType>()

        if (prevRecords.isEmpty()) {
            val hintResId = if (!startTimersByLongClick) {
                R.string.running_records_empty_hint
            } else {
                R.string.running_records_empty_hint_inverted
            }
            result += EmptyViewData(
                message = resourceRepo.getString(R.string.retroactive_tracking_mode_hint),
                hint = resourceRepo.getString(hintResId),
            )
        }

        if (prevRecords.isNotEmpty()) {
            result += runningRecordViewDataMapper.map(
                runningRecord = RunningRecord(
                    id = UNTRACKED_ITEM_ID,
                    timeStarted = prevRecords.firstOrNull()?.timeEnded.orZero(),
                    comment = "",
                    tags = emptyList(),
                ),
                dailyCurrent = null,
                recordType = RecordType(
                    id = 0L,
                    name = resourceRepo.getString(R.string.untracked_time_name),
                    icon = "",
                    color = AppColor(
                        0, colorMapper.toUntrackedColor(isDarkTheme).toString(),
                    ),
                    defaultDuration = 0,
                    note = "",
                ),
                recordTags = emptyList(),
                goals = emptyList(),
                isDarkTheme = isDarkTheme,
                useMilitaryTime = useMilitaryTime,
                showSeconds = showSeconds,
                durationFormat = durationFormat,
                nowIconVisible = false,
                goalsVisible = false,
                totalDurationVisible = false,
            )
            result += prevRecords.mapNotNull { record ->
                val prevRecordType = typesMap[record.typeId]
                    ?: return@mapNotNull null
                val data = recordViewDataMapper.map(
                    record = record,
                    recordType = prevRecordType,
                    recordTags = recordTags,
                    isDarkTheme = isDarkTheme,
                    useMilitaryTime = useMilitaryTime,
                    durationFormat = durationFormat,
                    showSeconds = showSeconds,
                )
                RecordWithHintViewData(data)
            }
            result += HintViewData(
                text = resourceRepo.getString(R.string.retroactive_tracking_mode_hint),
                paddingTop = 0,
                paddingBottom = 0,
            )
        }

        return result
    }
}
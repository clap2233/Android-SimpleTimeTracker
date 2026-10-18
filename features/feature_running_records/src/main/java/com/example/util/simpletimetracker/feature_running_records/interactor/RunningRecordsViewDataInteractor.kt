package com.example.util.simpletimetracker.feature_running_records.interactor

import com.example.util.simpletimetracker.core.R
import com.example.util.simpletimetracker.core.interactor.ActivityFilterViewDataInteractor
import com.example.util.simpletimetracker.core.interactor.ActivitySuggestionViewDataInteractor
import com.example.util.simpletimetracker.core.interactor.FilterGoalsByDayOfWeekInteractor
import com.example.util.simpletimetracker.core.interactor.GetCurrentRecordsDurationInteractor
import com.example.util.simpletimetracker.core.interactor.GetRunningRecordViewDataMediator
import com.example.util.simpletimetracker.core.interactor.TimerReminderStatusInteractor
import com.example.util.simpletimetracker.core.mapper.RecordTypeViewDataMapper
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.extension.addBetweenEach
import com.example.util.simpletimetracker.domain.extension.plus
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.recordTag.interactor.RecordTagInteractor
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeGoalInteractor
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.rhythm.ActivityPolicy
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.commentField.CommentFieldViewData
import com.example.util.simpletimetracker.feature_base_adapter.divider.DividerViewData
import com.example.util.simpletimetracker.feature_base_adapter.emptySpace.EmptySpaceViewData
import com.example.util.simpletimetracker.feature_running_records.mapper.RunningRecordsViewDataMapper
import com.example.util.simpletimetracker.feature_running_records.view.NowGroupViewData
import com.example.util.simpletimetracker.feature_running_records.view.NowTimerStatusViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RunningRecordsViewDataInteractor @Inject constructor(
    private val resourceRepo: ResourceRepo,
    private val prefsInteractor: PrefsInteractor,
    private val recordTypeInteractor: RecordTypeInteractor,
    private val recordTagInteractor: RecordTagInteractor,
    private val recordTypeGoalInteractor: RecordTypeGoalInteractor,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val recordInteractor: RecordInteractor,
    private val activityFilterViewDataInteractor: ActivityFilterViewDataInteractor,
    private val mapper: RunningRecordsViewDataMapper,
    private val recordTypeViewDataMapper: RecordTypeViewDataMapper,
    private val getRunningRecordViewDataMediator: GetRunningRecordViewDataMediator,
    private val getCurrentRecordsDurationInteractor: GetCurrentRecordsDurationInteractor,
    private val filterGoalsByDayOfWeekInteractor: FilterGoalsByDayOfWeekInteractor,
    private val activitySuggestionViewDataInteractor: ActivitySuggestionViewDataInteractor,
    private val recordsShortcutsViewDataInteractor: RecordsShortcutsViewDataInteractorImpl,
    private val timerReminderStatusInteractor: TimerReminderStatusInteractor,
) {

    suspend fun getViewData(
        completeTypeIds: Set<Long>,
        navBarHeightDp: Int,
        searchText: String,
        fromSearchChange: Boolean,
        groupExpansion: Map<Long, Boolean>,
    ): List<ViewHolderType> = withContext(Dispatchers.Default) {
        val recordTypes = recordTypeInteractor.getAll()
        val recordTypesMap = recordTypes.associateBy(RecordType::id)
        val recordTags = recordTagInteractor.getAll()
        val runningRecords = runningRecordInteractor.getAll()
        val recordTypesRunning = runningRecords.map(RunningRecord::id)
        val numberOfCards = prefsInteractor.getNumberOfCards()
        val isDarkTheme = prefsInteractor.getDarkMode()
        val useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat()
        val showSeconds = prefsInteractor.getShowSeconds()
        val durationFormat = prefsInteractor.getDurationFormat()
        val showFirstEnterHint = recordTypes.filterNot(RecordType::hidden).isEmpty()
        val showDefaultTypesButton = !prefsInteractor.getDefaultTypesHidden()
        val showRepeatButton = prefsInteractor.getEnableRepeatButton()
        val retroactiveTrackingModeEnabled = prefsInteractor.getRetroactiveTrackingMode()
        val isFiltersCollapsed = prefsInteractor.getIsActivityFiltersCollapsed()
        val isNavBarAtTheBottom = prefsInteractor.getIsNavBarAtTheBottom()
        val enableSearchOnMain = prefsInteractor.getEnableSearchOnMain()
        val startTimersByLongClick = prefsInteractor.getStartTimerByLongClick()
        val goals = filterGoalsByDayOfWeekInteractor
            .execute(recordTypeGoalInteractor.getAllTypeGoals())
            .groupBy { it.idData.value }
        val allDailyCurrents = if (goals.isNotEmpty()) {
            getCurrentRecordsDurationInteractor.getAllDailyCurrents(
                typeIds = recordTypesMap.keys,
                runningRecords = runningRecords,
            )
        } else {
            // No goals - no need to calculate durations.
            emptyMap()
        }
        val actualSearchText = if (enableSearchOnMain) searchText else ""

        val runningRecordsViewData = when {
            showFirstEnterHint -> {
                listOf(mapper.mapToTypesEmpty())
            }
            retroactiveTrackingModeEnabled -> {
                val prevRecord = recordInteractor.getAllPrev(
                    timeStarted = System.currentTimeMillis(),
                )
                mapper.mapToRetroActiveMode(
                    typesMap = recordTypesMap,
                    recordTags = recordTags,
                    prevRecords = prevRecord,
                    isDarkTheme = isDarkTheme,
                    durationFormat = durationFormat,
                    useMilitaryTime = useMilitaryTime,
                    showSeconds = showSeconds,
                    startTimersByLongClick = startTimersByLongClick,
                )
            }
            runningRecords.isEmpty() -> {
                mapper.mapToEmpty(
                    startTimersByLongClick = startTimersByLongClick,
                ).let(::listOf)
            }
            else -> {
                runningRecords
                    .sortedByDescending(RunningRecord::timeStarted)
                    .flatMap { runningRecord ->
                        val type = recordTypesMap[runningRecord.id] ?: return@flatMap emptyList()
                        val card = getRunningRecordViewDataMediator.execute(
                            type = type,
                            tags = recordTags,
                            goals = goals[runningRecord.id].orEmpty(),
                            record = runningRecord,
                            nowIconVisible = false,
                            goalsVisible = true,
                            totalDurationVisible = true,
                            isDarkTheme = isDarkTheme,
                            useMilitaryTime = useMilitaryTime,
                            durationFormat = durationFormat,
                            showSeconds = showSeconds,
                        )
                        listOf(card, NowTimerStatusViewData(runningRecord.id, timerReminderStatusInteractor.describe(runningRecord)))
                    }
            }
        }

        val searchViewData = if (enableSearchOnMain) {
            CommentFieldViewData(
                id = "running_records_search".hashCode().toLong(),
                text = if (fromSearchChange) null else actualSearchText,
                marginTopDp = -3,
                marginHorizontal = 8,
                hint = resourceRepo.getString(R.string.search_hint),
                valueType = CommentFieldViewData.ValueType.TextSingleLine,
            ).let(::listOf)
        } else {
            emptyList()
        }

        val filter = activityFilterViewDataInteractor.getFilter()
        val filtersViewData = activityFilterViewDataInteractor.getFilterViewData(
            filter = filter,
            searchText = actualSearchText,
            isDarkTheme = isDarkTheme,
            isFiltersCollapsed = isFiltersCollapsed,
            appendAddButton = true,
        )

        val suggestionsViewData = activitySuggestionViewDataInteractor.getSuggestionsViewData(
            recordTypesMap = recordTypesMap,
            goals = goals,
            runningRecords = runningRecords,
            allDailyCurrents = allDailyCurrents,
            completeTypeIds = completeTypeIds,
            searchText = actualSearchText,
            numberOfCards = numberOfCards,
            isDarkTheme = isDarkTheme,
        )

        val visibleTypes = recordTypes
            .filterNot {
                it.hidden
            }
            .let { list ->
                activityFilterViewDataInteractor.applyFilter(list, filter)
            }
        fun mapType(type: RecordType) = recordTypeViewDataMapper.map(
                    recordType = type,
                    numberOfCards = numberOfCards,
                    isDarkTheme = isDarkTheme,
                    checkState = recordTypeViewDataMapper.mapGoalCheckmark(
                        type = type,
                        goals = goals,
                        allDailyCurrents = allDailyCurrents,
                    ),
                    isComplete = type.id in completeTypeIds,
                ).let { card ->
                    card.copy(
                        itemIsFiltered = type.id in recordTypesRunning,
                        policyLabel = policyLabel(ActivityPolicy.resolve(type, recordTypes)),
                        height = card.height + 36,
                    )
                }
        fun matches(type: RecordType) = actualSearchText.isEmpty() ||
            type.name.contains(actualSearchText, ignoreCase = true)

        val roots = recordTypes.filter { !it.hidden && it.parentTypeId == null && it.presetKey != null && it.presetKey?.contains(".") == false }
        val groupedIds = mutableSetOf<Long>()
        val recordTypesViewData = mutableListOf<ViewHolderType>().apply {
            roots.forEach { root ->
                val children = visibleTypes.filter { it.parentTypeId == root.id && (matches(it) || matches(root)) }
                if (root !in visibleTypes && children.isEmpty()) return@forEach
                if (!matches(root) && children.isEmpty()) return@forEach
                val expanded = actualSearchText.isNotEmpty() || (groupExpansion[root.id]
                    ?: (root.presetKey in setOf("learning", "entertainment")))
                add(NowGroupViewData(
                    id = root.id,
                    name = root.name,
                    expanded = expanded,
                    policy = policyLabel(ActivityPolicy.resolve(root, recordTypes)),
                    canStart = root in visibleTypes,
                    activity = mapType(root),
                ))
                groupedIds += root.id
                groupedIds += children.map { it.id }
                if (expanded) addAll(children.map(::mapType))
            }
            val ungrouped = visibleTypes.filter { it.id !in groupedIds && matches(it) }
            if (ungrouped.isNotEmpty()) {
                val expanded = groupExpansion[UNGROUPED_ID] ?: true
                add(NowGroupViewData(UNGROUPED_ID, "未分组", expanded, "长按活动可修改所属分组", canStart = false))
                if (expanded) addAll(ungrouped.map(::mapType))
            }
                    if (showRepeatButton) {
                        recordTypeViewDataMapper.mapToRepeatItem(
                            numberOfCards = numberOfCards,
                            isDarkTheme = isDarkTheme,
                        ).let(::add)
                    }
                    recordTypeViewDataMapper.mapToAddItem(
                        numberOfCards = numberOfCards,
                        isDarkTheme = isDarkTheme,
                    ).let(::add)
                    if (showDefaultTypesButton) {
                        recordTypeViewDataMapper.mapToAddDefaultItem(
                            numberOfCards = numberOfCards,
                            isDarkTheme = isDarkTheme,
                        ).let(::add)
                    }
        }

        val shortcutsViewData = recordsShortcutsViewDataInteractor.getShortcutsViewData(
            filter = filter,
            recordTypesMap = recordTypesMap,
            recordTags = recordTags,
            runningRecords = runningRecords,
            searchText = actualSearchText,
            isDarkTheme = isDarkTheme,
        )

        // Flexbox layout doesn't fully support clipToPadding = false.
        // Because of that bottom padding with nav bar insets are not applied to main recycler.
        // Instead an empty space added.
        val bottomSpaceForNavBar = if (isNavBarAtTheBottom) {
            emptyList()
        } else {
            EmptySpaceViewData(
                id = "running_records_nav_bar_space".hashCode().toLong(),
                height = EmptySpaceViewData.ViewDimension.ExactSizeDp(navBarHeightDp),
                wrapBefore = true,
            ).let(::listOf)
        }

        return@withContext listOf(
            runningRecordsViewData,
            searchViewData,
            filtersViewData,
            listOf(com.example.util.simpletimetracker.feature_base_adapter.hint.HintViewData("活动", gravity = com.example.util.simpletimetracker.feature_base_adapter.hint.HintViewData.Gravity.START)) + recordTypesViewData,
            if (shortcutsViewData.isEmpty()) emptyList() else listOf(com.example.util.simpletimetracker.feature_base_adapter.hint.HintViewData("活动模板", gravity = com.example.util.simpletimetracker.feature_base_adapter.hint.HintViewData.Gravity.START)) + shortcutsViewData,
        ).filter {
            it.isNotEmpty()
        }.addBetweenEach { index ->
            listOf(DividerViewData(index.toLong()))
        }.flatten().plus(
            bottomSpaceForNavBar,
        )
    }

    private fun policyLabel(policy: TimerPolicy): String = when (policy.mode) {
        TimerPolicy.Mode.ORDINARY -> "自由计时"
        TimerPolicy.Mode.FOCUS_EXIT -> "退出轻提醒 · 15 分钟段"
        TimerPolicy.Mode.INTERVAL -> "每 ${policy.intervalMinutes} 分钟提醒"
    }

    companion object {
        private const val UNGROUPED_ID = -50_001L
    }
}

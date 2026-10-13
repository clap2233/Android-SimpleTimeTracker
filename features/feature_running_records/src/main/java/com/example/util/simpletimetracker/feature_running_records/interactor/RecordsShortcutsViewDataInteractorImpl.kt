package com.example.util.simpletimetracker.feature_running_records.interactor

import com.example.util.simpletimetracker.core.interactor.ActivityFilterViewDataInteractor
import com.example.util.simpletimetracker.core.interactor.IsSettingShortcutEnabledInteractor
import com.example.util.simpletimetracker.core.mapper.RecordShortcutViewDataMapper
import com.example.util.simpletimetracker.domain.extension.search
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.recordShortcut.interactor.RecordShortcutInteractor
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTag
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.feature_base_adapter.recordShortcut.RecordShortcutViewData
import com.example.util.simpletimetracker.feature_running_records.api.RecordsShortcutsViewDataInteractor
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.feature_settings.api.SettingsCardOrderMapper
import javax.inject.Inject

class RecordsShortcutsViewDataInteractorImpl @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val recordShortcutInteractor: RecordShortcutInteractor,
    private val recordShortcutViewDataMapper: RecordShortcutViewDataMapper,
    private val isSettingShortcutEnabledInteractor: IsSettingShortcutEnabledInteractor,
    private val activityFilterViewDataInteractor: ActivityFilterViewDataInteractor,
    private val settingsCardOrderMapper: SettingsCardOrderMapper,
) : RecordsShortcutsViewDataInteractor {

    override suspend fun getShortcutsViewData(
        filter: ActivityFilterViewDataInteractor.Filter,
        recordTypesMap: Map<Long, RecordType>,
        recordTags: List<RecordTag>,
        runningRecords: List<RunningRecord>,
        searchText: String,
        isDarkTheme: Boolean,
    ): List<RecordShortcutViewData> {
        val shortcuts = recordShortcutInteractor.getAll()

        return shortcuts.let {
            activityFilterViewDataInteractor.applyFilterToShortcuts(it, filter)
        }.map { shortcut ->
            val isFiltered = when (shortcut.target) {
                is RecordShortcut.Target.Record -> {
                    runningRecords.any { it.templateId == shortcut.id }
                }
                is RecordShortcut.Target.Setting -> false
            }
            val isEnabled = when (val target = shortcut.target) {
                is RecordShortcut.Target.Record -> false
                is RecordShortcut.Target.Setting -> {
                    isSettingShortcutEnabledInteractor.execute(target)
                }
            }
            val spinnerData = mapSpinnerData(shortcut)
            recordShortcutViewDataMapper.map(
                shortcut = shortcut,
                typesMap = recordTypesMap,
                tags = recordTags,
                isDarkTheme = isDarkTheme,
                isFiltered = isFiltered,
                isEnabled = isEnabled,
                spinnerData = spinnerData,
            )
        }.search(
            text = searchText,
            searchableContent = { data.name },
        )
    }

    private suspend fun mapSpinnerData(
        shortcut: RecordShortcut,
    ): RecordShortcutViewData.SpinnerData? {
        return when (val target = shortcut.target) {
            is RecordShortcut.Target.Record -> null
            is RecordShortcut.Target.Setting -> when (target.action) {
                RecordShortcut.SettingAction.SortActivities -> {
                    val order = prefsInteractor.getCardOrder()
                    val data = settingsCardOrderMapper.toCardOrderViewData(order)
                    RecordShortcutViewData.SpinnerData(
                        block = SettingsBlock.DisplaySortActivities,
                        items = data.items,
                        selectedPosition = data.selectedPosition,
                        processSameItemSelected = false,
                        isButtonVisible = data.isManualConfigButtonVisible,
                    )
                }
                else -> null
            }
        }
    }
}

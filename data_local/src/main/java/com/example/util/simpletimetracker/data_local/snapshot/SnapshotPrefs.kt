package com.example.util.simpletimetracker.data_local.snapshot

import android.content.SharedPreferences
import com.google.gson.JsonObject

internal object SnapshotPrefs {
    // Persist business preferences only; never device permissions, file URIs, upload state or credentials.
    private enum class Kind { BOOLEAN, INT, LONG, STRING, SET }
    private data class Field(val storageKey: String, val kind: Kind)
    private val fields = mapOf(
        "recordTypesFilteredOnList" to Field("recordTypesFilteredOnList", Kind.SET),
        "categoriesFilteredOnList" to Field("categoriesFilteredOnList", Kind.SET),
        "tagsFilteredOnList" to Field("tagsFilteredOnList", Kind.SET),
        "listFilterType" to Field("listFilterType", Kind.INT),
        "recordTypesFilteredOnChart" to Field("recordTypesFilteredOnChart", Kind.SET),
        "categoriesFilteredOnChart" to Field("categoriesFilteredOnChart", Kind.SET),
        "tagsFilteredOnChart" to Field("tagsFilteredOnChart", Kind.SET),
        "chartFilterType" to Field("chartFilterType", Kind.INT),
        "cardOrder" to Field("cardOrder", Kind.INT),
        "categoryOrder" to Field("categoryOrder", Kind.INT),
        "tagOrder" to Field("tagOrder", Kind.INT),
        "cardOrderManual" to Field("cardOrderManual", Kind.SET),
        "categoryOrderManual" to Field("categoryOrderManual", Kind.SET),
        "tagOrderManual" to Field("tagOrderManual", Kind.SET),
        "statisticsDetailTagValueSettings" to Field("statisticsDetailTagValueSettings", Kind.SET),
        "statisticsRange" to Field("statisticsRange", Kind.INT),
        "statisticsRangeCustomStart" to Field("statisticsRangeCustomStart", Kind.LONG),
        "statisticsRangeCustomEnd" to Field("statisticsRangeCustomEnd", Kind.LONG),
        "statisticsRangeLastDays" to Field("statisticsRangeLastDays", Kind.INT),
        "statisticsDetailRange" to Field("statisticsDetailRange", Kind.INT),
        "statisticsDetailRangeCustomStart" to Field("statisticsDetailRangeCustomStart", Kind.LONG),
        "statisticsDetailRangeCustomEnd" to Field("statisticsDetailRangeCustomEnd", Kind.LONG),
        "statisticsDetailRangeLastDays" to Field("statisticsDetailRangeLastDays", Kind.INT),
        "fileExportRange" to Field("fileExportRange", Kind.INT),
        "fileExportRangeCustomStart" to Field("fileExportRangeCustomStart", Kind.LONG),
        "fileExportRangeCustomEnd" to Field("fileExportRangeCustomEnd", Kind.LONG),
        "fileExportRangeLastDays" to Field("fileExportRangeLastDays", Kind.INT),
        "csvExportDateTimeFormat" to Field("csvExportDateTimeFormat", Kind.INT),
        "csvExportCustomFileName" to Field("csvExportCustomFilename", Kind.STRING),
        "icsExportCustomFileName" to Field("icsExportCustomFilename", Kind.STRING),
        "keepStatisticsRange" to Field("keepStatisticsRange", Kind.BOOLEAN),
        "firstDayOfWeek" to Field("firstDayOfWeek", Kind.INT),
        "startOfDayShift" to Field("startOfDayShift", Kind.LONG),
        "showUntrackedInRecords" to Field("showUntrackedInRecords", Kind.BOOLEAN),
        "showUntrackedInStatistics" to Field("showUntrackedInStatistics", Kind.BOOLEAN),
        "showRecordsCalendar" to Field("showRecordsCalendar", Kind.BOOLEAN),
        "reverseOrderInCalendar" to Field("reverseOrderInCalendar", Kind.BOOLEAN),
        "daysInCalendar" to Field("daysInCalendar", Kind.INT),
        "showActivityFilters" to Field("showActivityFilters", Kind.BOOLEAN),
        "isActivityFiltersCollapsed" to Field("isActivityFiltersCollapsed", Kind.BOOLEAN),
        "allowMultipleActivityFilters" to Field("allowMultipleActivityFilters", Kind.BOOLEAN),
        "showCategoriesAsPredefinedFilters" to Field("showCategoriesAsPredefinedFilters", Kind.BOOLEAN),
        "selectedPredefinedFilters" to Field("selectedPredefinedFilters", Kind.SET),
        "enableRepeatButton" to Field("enableRepeatButton", Kind.BOOLEAN),
        "enableSearchOnMain" to Field("enableSearchOnMain", Kind.BOOLEAN),
        "enablePomodoroMode" to Field("enablePomodoroMode", Kind.BOOLEAN),
        "pomodoroFocusTime" to Field("pomodoroFocusTime", Kind.LONG),
        "pomodoroBreakTime" to Field("pomodoroBreakTime", Kind.LONG),
        "pomodoroLongBreakTime" to Field("pomodoroLongBreakTime", Kind.LONG),
        "pomodoroPeriodsUntilLongBreak" to Field("pomodoroPeriodsUntilLongBreak", Kind.LONG),
        "pomodoroShowMoreControls" to Field("pomodoroShowMoreControls", Kind.BOOLEAN),
        "showGoalsSeparately" to Field("showGoalsSeparately", Kind.BOOLEAN),
        "hideFinishedGoals" to Field("hideFinishedGoals", Kind.BOOLEAN),
        "showNotifications" to Field("showNotifications", Kind.BOOLEAN),
        "showNotificationsControls" to Field("showNotificationsControls", Kind.BOOLEAN),
        "showNotificationEvenWithNoTimers" to Field("showNotificationEvenWithNoTimers", Kind.BOOLEAN),
        "inactivityReminderDuration" to Field("inactivityReminderDuration", Kind.LONG),
        "inactivityReminderRecurrent" to Field("inactivityReminderRecurrent", Kind.BOOLEAN),
        "inactivityReminderDoNotDisturbStart" to Field("inactivityReminderDndStart", Kind.LONG),
        "inactivityReminderDoNotDisturbEnd" to Field("inactivityReminderDndEnd", Kind.LONG),
        "activityReminderDuration" to Field("activityReminderDuration", Kind.LONG),
        "activityReminderRecurrent" to Field("activityReminderRecurrent", Kind.BOOLEAN),
        "activityReminderDoNotDisturbStart" to Field("activityReminderDndStart", Kind.LONG),
        "activityReminderDoNotDisturbEnd" to Field("activityReminderDndEnd", Kind.LONG),
        "ignoreShortRecordsDuration" to Field("ignoreShortRecordsDuration", Kind.LONG),
        "ignoreShortUntrackedDuration" to Field("ignoreShortUntrackedDuration", Kind.LONG),
        "untrackedRangeEnabled" to Field("untrackedRangeEnabled", Kind.BOOLEAN),
        "untrackedRangeStart" to Field("untrackedRangeStart", Kind.LONG),
        "untrackedRangeEnd" to Field("untrackedRangeEnd", Kind.LONG),
        "darkMode" to Field("darkMode2", Kind.INT),
        "numberOfCards" to Field("numberOfCards", Kind.INT),
        "useMilitaryTimeFormat" to Field("useMilitaryTimeFormat", Kind.BOOLEAN),
        "useMonthDayTimeFormat" to Field("useMonthDayTimeFormat", Kind.BOOLEAN),
        "durationFormat" to Field("durationPresentationFormat", Kind.INT),
        "showSeconds" to Field("showSeconds", Kind.BOOLEAN),
        "keepScreenOn" to Field("keepScreenOn", Kind.BOOLEAN),
        "startTimerByLongClick" to Field("startTimerByLongClick", Kind.BOOLEAN),
        "showRecordTagSelection" to Field("showRecordTagSelection", Kind.BOOLEAN),
        "recordTagSelectionCloseAfterOne" to Field("recordTagSelectionCloseAfterOne", Kind.BOOLEAN),
        "recordTagSelectionExcludeActivities" to Field("showRecordTagSelectionExcludeActivities", Kind.SET),
        "closeAfterOneTagExcludeActivities" to Field("closeAfterOneTagExcludeActivities", Kind.SET),
        "showCommentInput" to Field("showCommentInput", Kind.BOOLEAN),
        "commentInputExcludeActivities" to Field("showCommentInputExcludeActivities", Kind.SET),
        "repeatButtonType" to Field("repeatButtonType", Kind.INT),
        "widgetBackgroundTransparencyPercent" to Field("widgetTransparencyPercent", Kind.LONG),
        "defaultTypesHidden" to Field("defaultTypesHidden", Kind.BOOLEAN),
        "isNavBarAtTheBottom" to Field("isNavBarAtTheBottom", Kind.BOOLEAN),
        "isCategoriesSearchEnabled" to Field("isCategoriesSearchEnabled", Kind.BOOLEAN),
        "isCategoriesRelationsEnabled" to Field("isCategoriesRelationsEnabled", Kind.BOOLEAN),
        "isArchiveSearchEnabled" to Field("isArchiveSearchEnabled", Kind.BOOLEAN),
        "isTagSearchEnabled" to Field("isTagSearchEnabled", Kind.BOOLEAN),
        "isShowAllTagsEnabled" to Field("isShowAllTagsEnabled", Kind.BOOLEAN),
        "hiddenCommentFilters" to Field("hiddenCommentFilters", Kind.SET),
        "durationSuggestionsWasPrepopulated" to Field("durationSuggestionsWasPrepopulated", Kind.BOOLEAN),
        "typeAdditionalFieldsShown" to Field("typeAdditionalFieldsShown", Kind.BOOLEAN),
        "categoryAdditionalFieldsShown" to Field("categoryAdditionalFieldsShown", Kind.BOOLEAN),
        "tagAdditionalFieldsShown" to Field("tagAdditionalFieldsShown", Kind.BOOLEAN),
        "statisticsDetailStreakType" to Field("statisticsDetailStreakType", Kind.INT),
        "hiddenContainerOptions" to Field("hiddenContainerOptions", Kind.SET),
    )

    fun read(prefs: SharedPreferences): JsonObject = JsonObject().also { result ->
        fields.forEach { (name, field) ->
            if (prefs.contains(field.storageKey)) {
                val value = prefs.all[field.storageKey]
                result.add(name, SnapshotJson.value(if (value is Set<*>) value.filterIsInstance<String>().sorted() else value))
            }
        }
    }

    fun validate(data: JsonObject) {
        require(data.keySet().all { it in fields }) { "Unsupported business setting" }
        data.entrySet().forEach { (key, value) ->
            when (requireNotNull(fields[key]).kind) {
                Kind.BOOLEAN -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
                Kind.INT -> require(SnapshotJson.number(data, key) in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                Kind.LONG -> SnapshotJson.number(data, key)
                Kind.STRING -> SnapshotJson.text(data, key)
                Kind.SET -> {
                    require(value.isJsonArray)
                    value.asJsonArray.forEach { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
                }
            }
        }
    }

    fun apply(prefs: SharedPreferences, data: JsonObject): Boolean {
        validate(data)
        val editor = prefs.edit()
        fields.values.forEach { editor.remove(it.storageKey) }
        data.entrySet().forEach { (key, value) ->
            val field = requireNotNull(fields[key])
            when (field.kind) {
                Kind.BOOLEAN -> editor.putBoolean(field.storageKey, value.asBoolean)
                Kind.INT -> editor.putInt(field.storageKey, value.asBigDecimal.intValueExact())
                Kind.LONG -> editor.putLong(field.storageKey, value.asBigDecimal.longValueExact())
                Kind.STRING -> editor.putString(field.storageKey, value.asString)
                Kind.SET -> editor.putStringSet(field.storageKey, value.asJsonArray.map { it.asString }.toSet())
            }
        }
        return editor.commit()
    }
}

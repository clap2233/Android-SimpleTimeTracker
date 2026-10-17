package com.example.util.simpletimetracker.feature_main.provider

import androidx.annotation.DrawableRes
import com.example.util.simpletimetracker.core.model.NavigationTab
import com.example.util.simpletimetracker.core.model.NavigationTabProvider
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_RECORDS
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_SETTINGS
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_STATISTICS
import com.example.util.simpletimetracker.domain.extension.orZero
import com.example.util.simpletimetracker.feature_main.R
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class MainTabsProvider @Inject constructor(
    val tabProviders: Map<Class<out NavigationTab>, @JvmSuppressWildcards NavigationTabProvider>,
    private val resourceRepo: ResourceRepo,
) {

    val tabsList: List<NavigationTab> by lazy { loadTabsList() }
    val mainTab: NavigationTab by lazy { loadMainTab() }

    fun mapPositionToTab(position: Int): NavigationTab? {
        return tabsList.getOrNull(position)
    }

    fun mapTabToPosition(tab: NavigationTab): Int {
        return tabsList.indexOf(tab).takeUnless { it == -1 }.orZero()
    }

    @DrawableRes
    fun mapPositionToIcon(position: Int): Int {
        return position
            .let(::mapPositionToTab)
            .let(::mapToIcon)
    }

    fun mapPositionToDescription(position: Int): String? {
        return position
            .let(::mapPositionToTab)
            .let(::mapToDescription)
    }

    fun mapPositionToLabel(position: Int): String? = when (mapPositionToTab(position)) {
        NavigationTab.RunningRecords -> "现在"
        NavigationTab.Records -> "时间"
        NavigationTab.Planning -> "规划"
        NavigationTab.Statistics -> "回顾"
        NavigationTab.Settings -> "设置"
        NavigationTab.Goals -> "目标"
        null -> null
    }

    fun mapNavigationToPosition(value: String): Int? {
        return mapNavigationToTab(value)?.let(::mapTabToPosition)
    }

    @DrawableRes
    private fun mapToIcon(tab: NavigationTab?): Int {
        return when (tab) {
            NavigationTab.RunningRecords -> R.drawable.life_clock_ui2_nav_now
            NavigationTab.Records -> R.drawable.life_clock_ui2_nav_time
            NavigationTab.Planning -> R.drawable.life_clock_ui2_nav_time
            NavigationTab.Statistics -> R.drawable.life_clock_ui2_nav_review
            NavigationTab.Settings -> R.drawable.life_clock_ui2_nav_settings
            NavigationTab.Goals -> R.drawable.tab_goals
            null -> R.drawable.unknown
        }
    }

    private fun mapToDescription(tab: NavigationTab?): String? {
        return when (tab) {
            NavigationTab.RunningRecords -> R.string.shortcut_navigation_timers
            NavigationTab.Records -> R.string.shortcut_navigation_records
            NavigationTab.Planning -> return "规划"
            NavigationTab.Statistics -> R.string.shortcut_navigation_statistics
            NavigationTab.Settings -> R.string.shortcut_navigation_settings
            NavigationTab.Goals -> R.string.change_record_type_goal_time_hint
            null -> return null
        }.let(resourceRepo::getString)
    }

    private fun mapNavigationToTab(value: String): NavigationTab? {
        return when (value) {
            SHORTCUT_NAVIGATION_RECORDS -> NavigationTab.Records
            SHORTCUT_NAVIGATION_STATISTICS -> NavigationTab.Statistics
            SHORTCUT_NAVIGATION_SETTINGS -> NavigationTab.Settings
            else -> null
        }
    }

    private fun loadTabsList(): List<NavigationTab> {
        return listOfNotNull(
            NavigationTab.RunningRecords,
            NavigationTab.Records,
            NavigationTab.Planning,
            NavigationTab.Statistics,
            NavigationTab.Settings,
        )
    }

    private fun loadMainTab(): NavigationTab {
        return runBlocking {
            NavigationTab.RunningRecords
        }
    }
}

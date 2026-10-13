package com.example.util.simpletimetracker

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.feature_settings.view.SettingsFragment
import com.example.util.simpletimetracker.feature_settings.viewModel.FullBackupViewModel
import com.example.util.simpletimetracker.utils.BaseUiTest
import com.example.util.simpletimetracker.utils.NavUtils
import com.example.util.simpletimetracker.utils.clickOnViewWithText
import com.example.util.simpletimetracker.utils.tryAction
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.core.R as coreR
import com.example.util.simpletimetracker.feature_settings.R as settingsR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockFullBackupUiTest : BaseUiTest() {
    @Inject lateinit var fullBackup: FullBackupRepo
    @Inject lateinit var records: RecordInteractor
    @Inject lateinit var database: AppDatabase

    @Test fun visibleBackupEntryShowsPreviewAndCancelsOrAppliesThePreparedReplacement() {
        testUtils.addActivity("Backup UI", color = firstColor, icon = firstIcon)
        testUtils.addRecord("Backup UI", comment = "original")
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("m6-ui-", ".zip", context.cacheDir)
        try {
            runBlocking { fullBackup.export(Uri.fromFile(file).toString()) }
            NavUtils.openSettingsScreen()
            onView(withId(com.example.util.simpletimetracker.feature_settings.R.id.settingsData)).perform(androidx.test.espresso.action.ViewActions.click())
            onView(withId(settingsR.id.lifeClockBackupButton)).check(matches(isCompletelyDisplayed())).perform(click())
            onView(withText(getString(coreR.string.life_clock_export_full))).check(matches(isCompletelyDisplayed()))
            pressBack()
            fun prepare() {
                activityScenarioRule.scenario.onActivity { activity ->
                    fun find(fragments: List<Fragment>): SettingsFragment? = fragments.firstNotNullOfOrNull { fragment ->
                        if (fragment is SettingsFragment) fragment else find(fragment.childFragmentManager.fragments)
                    }
                    val fragment = requireNotNull(find(activity.supportFragmentManager.fragments))
                    ViewModelProvider(fragment)[FullBackupViewModel::class.java].prepare(Uri.fromFile(file).toString())
                }
                tryAction { onView(withText(getString(coreR.string.life_clock_restore_apply))).check(matches(isCompletelyDisplayed())) }
            }
            prepare()
            clickOnViewWithText(coreR.string.cancel)
            runBlocking { assertEquals("original", records.getAll().single().comment) }
            runBlocking {
                val original = database.recordDao().getAll().single().record
                database.recordDao().insert(original.copy(comment = "changed"))
            }
            prepare()
            clickOnViewWithText(coreR.string.life_clock_restore_apply)
            tryAction { runBlocking { assertEquals("original", records.getAll().single().comment) } }
        } finally {
            file.delete()
        }
    }
}

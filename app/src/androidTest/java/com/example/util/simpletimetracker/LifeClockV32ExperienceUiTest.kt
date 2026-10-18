package com.example.util.simpletimetracker

import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.utils.*
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_change_record.R as detailsR
import com.example.util.simpletimetracker.feature_base_adapter.R as baseR
import com.example.util.simpletimetracker.core.R as coreR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockV32ExperienceUiTest : BaseUiTest() {
    @Inject lateinit var records: RecordInteractor
    @Inject lateinit var settings: LifeClockSettingsRepo

    @Test fun bothScoresAcceptNegativeZeroPositiveAndMissingWithOneSave() {
        val name = "V3.2 experience"
        testUtils.addActivity(name, color = firstColor, icon = firstIcon)
        testUtils.addRecord(name)
        NavUtils.openRecordsScreen()
        fun open() = tryAction {
            clickOnView(allOf(withId(baseR.id.viewRecordItem), isDisplayed(), hasDescendant(withSubstring(name))))
        }
        fun choose(button: Int, score: Int?) {
            onView(withId(button)).perform(scrollTo(), click())
            onData(equalTo(score?.toString() ?: getString(coreR.string.life_clock_empty))).perform(click())
        }
        fun sum(text: String) {
            onView(withId(detailsR.id.lifeClockCombinedScore)).perform(scrollTo()).check(matches(withText(text)))
        }
        fun save() {
            clickOnViewWithText(coreR.string.change_record_save)
            tryAction { assertEquals(1, runBlocking { records.getAll().size }) }
        }

        open()
        onView(withText("确认时间")).check(doesNotExist())
        choose(detailsR.id.lifeClockHappinessButton, -5)
        choose(detailsR.id.lifeClockMeaningButton, -5)
        sum("这条经历合计 · -10")
        save()
        runBlocking {
            val record = records.getAll().single()
            val identity = settings.getOrCreate()
            assertEquals(-5.0, record.tags.single { it.tagId == identity.happinessTagId }.numericValue)
            assertEquals(-5.0, record.tags.single { it.tagId == identity.meaningTagId }.numericValue)
        }

        open()
        activityScenarioRule.scenario.recreate()
        sum("这条经历合计 · -10")
        choose(detailsR.id.lifeClockHappinessButton, 0)
        choose(detailsR.id.lifeClockMeaningButton, 0)
        sum("这条经历合计 · 0")
        save()
        runBlocking {
            val identity = settings.getOrCreate()
            assertEquals(0.0, records.getAll().single().tags.single { it.tagId == identity.happinessTagId }.numericValue)
        }

        open()
        choose(detailsR.id.lifeClockHappinessButton, 5)
        choose(detailsR.id.lifeClockMeaningButton, 5)
        sum("这条经历合计 · +10")
        save()
        open()
        choose(detailsR.id.lifeClockHappinessButton, null)
        sum("两项都填写后显示合计")
        save()
        runBlocking {
            val identity = settings.getOrCreate()
            assertNull(records.getAll().single().tags.single { it.tagId == identity.happinessTagId }.numericValue)
            assertEquals(5.0, records.getAll().single().tags.single { it.tagId == identity.meaningTagId }.numericValue)
        }
    }
}

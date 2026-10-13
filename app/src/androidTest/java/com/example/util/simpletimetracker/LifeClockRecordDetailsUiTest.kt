package com.example.util.simpletimetracker

import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.utils.BaseUiTest
import com.example.util.simpletimetracker.utils.NavUtils
import com.example.util.simpletimetracker.utils.clickOnView
import com.example.util.simpletimetracker.utils.clickOnViewWithText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_change_record.R as detailsR
import com.example.util.simpletimetracker.feature_base_adapter.R as baseR
import com.example.util.simpletimetracker.core.R as coreR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockRecordDetailsUiTest : BaseUiTest() {
    @Inject lateinit var records: RecordInteractor
    @Inject lateinit var settings: LifeClockSettingsRepo
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var tags: com.example.util.simpletimetracker.domain.recordTag.interactor.RecordTagInteractor
    @Inject lateinit var running: com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor

    @Test
    fun runningRecordShowsDirectScoreAndPhotoEntries() {
        val name = "Quick rating"
        testUtils.addActivity(name, color = firstColor, icon = firstIcon)
        NavUtils.openRunningRecordsScreen()
        clickOnViewWithText(name)
        onView(withId(baseR.id.runningEdit)).check(matches(isCompletelyDisplayed())).perform(click())
        onView(withId(detailsR.id.lifeClockHappinessButton)).perform(scrollTo()).check(matches(isCompletelyDisplayed())).perform(click())
        clickOnViewWithText("2")
        onView(withId(detailsR.id.lifeClockMeaningButton)).perform(scrollTo()).check(matches(isCompletelyDisplayed())).perform(click())
        clickOnViewWithText("3")
        onView(withId(detailsR.id.lifeClockPhotosButton)).perform(scrollTo()).check(matches(isCompletelyDisplayed())).perform(click())
        onView(withText(getString(coreR.string.life_clock_select_photo))).check(matches(isCompletelyDisplayed()))
        pressBack()
        clickOnViewWithText(coreR.string.change_record_save)
        runBlocking {
            val identity = settings.getOrCreate()
            val saved = running.getAll().single()
            assertEquals(2.0, saved.tags.single { it.tagId == identity.happinessTagId }.numericValue)
            assertEquals(3.0, saved.tags.single { it.tagId == identity.meaningTagId }.numericValue)
        }
        onView(withId(baseR.id.runningStop)).check(matches(isCompletelyDisplayed())).perform(click())
        runBlocking {
            assertEquals(0, running.getAll().size)
            val saved = records.getAll().single()
            assertEquals(2.0, saved.tags.single { it.tagId == settings.getOrCreate().happinessTagId }.numericValue)
            assertEquals(3.0, saved.tags.single { it.tagId == settings.getOrCreate().meaningTagId }.numericValue)
        }
    }

    @Test
    fun summaryAndRatingCanBeSavedClearedAndCancelledThroughTheEditor() {
        val name = "M4 reading"
        testUtils.addActivity(name, color = firstColor, icon = firstIcon)
        runBlocking {
            for ((tagName, purpose) in listOf(
                "Alice" to com.example.util.simpletimetracker.domain.recordTag.model.RecordTagPurpose.PERSON,
                "Bob" to com.example.util.simpletimetracker.domain.recordTag.model.RecordTagPurpose.PERSON,
                "Home" to com.example.util.simpletimetracker.domain.recordTag.model.RecordTagPurpose.ENVIRONMENT,
            )) {
                tags.add(
                    com.example.util.simpletimetracker.domain.recordTag.model.RecordTag(
                        name = tagName, icon = "", color = com.example.util.simpletimetracker.domain.color.model.AppColor(0, ""),
                        iconColorSource = 0, note = "", valueType = com.example.util.simpletimetracker.domain.recordTag.model.RecordTagValueType.NONE, valueSuffix = "", purpose = purpose,
                    ),
                )
            }
        }
        testUtils.addRecord(name)
        NavUtils.openRecordsScreen()
        fun openDetails() {
            clickOnView(allOf(withId(baseR.id.viewRecordItem), hasDescendant(withSubstring(name))))
            onView(withId(detailsR.id.lifeClockHappinessButton)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
            onView(withId(detailsR.id.lifeClockPhotosButton)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
        }
        openDetails()
        activityScenarioRule.scenario.recreate()
        activityScenarioRule.scenario.recreate()
        onView(withId(detailsR.id.lifeClockSummaryInput)).perform(scrollTo(), replaceText("morning reading"))
        closeSoftKeyboard()
        onView(withId(detailsR.id.lifeClockPeopleButton)).perform(scrollTo(), click())
        clickOnViewWithText("Alice")
        clickOnViewWithText("Bob")
        clickOnViewWithText(coreR.string.ok)
        onView(withId(detailsR.id.lifeClockEnvironmentButton)).perform(scrollTo(), click())
        clickOnViewWithText("Home")
        clickOnViewWithText(coreR.string.ok)
        onView(withId(detailsR.id.lifeClockHappinessButton)).perform(scrollTo(), click())
        clickOnViewWithText("4")
        clickOnViewWithText(coreR.string.change_record_save)
        runBlocking {
            val record = records.getAll().single()
            assertEquals("morning reading", record.summary)
            assertEquals(setOf("Alice", "Bob", "Home"), tags.getAll().filter { tag -> record.tags.any { it.tagId == tag.id } && tag.id !in settings.getOrCreate().ratingTagIds }.map { it.name }.toSet())
            assertEquals(4.0, record.tags.single { it.tagId == settings.getOrCreate().happinessTagId }.numericValue)
        }
        openDetails()
        onView(withId(detailsR.id.lifeClockSummaryInput)).perform(scrollTo(), replaceText(""))
        closeSoftKeyboard()
        onView(withId(detailsR.id.lifeClockHappinessButton)).perform(scrollTo(), click())
        clickOnViewWithText(coreR.string.life_clock_empty)
        clickOnViewWithText(coreR.string.change_record_save)
        runBlocking {
            val record = records.getAll().single()
            assertEquals("", record.summary)
            assertNull(record.tags.single { it.tagId == settings.getOrCreate().happinessTagId }.numericValue)
        }
        openDetails()
        onView(withId(detailsR.id.lifeClockSummaryInput)).perform(scrollTo(), replaceText("discard this"))
        closeSoftKeyboard()
        pressBack()
        runBlocking { assertEquals("", records.getAll().single().summary) }
        runBlocking {
            val id = settings.getOrCreate().happinessTagId
            assertFalse(tags.remove(id))
            assertNotNull(tags.get(id))
        }
    }

    @After
    fun clearIdentityForIsolatedUiFixture() {
        // BaseUiTest clears legacy tables; this test also owns the new settings row.
        database.openHelper.writableDatabase.execSQL("DELETE FROM lifeClockSettings")
    }
}

package com.example.util.simpletimetracker

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordRepo
import com.example.util.simpletimetracker.domain.record.repo.RunningRecordRepo
import com.example.util.simpletimetracker.domain.recordShortcut.interactor.RecordShortcutInteractor
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.recordShortcut.repo.RecordShortcutRepo
import com.example.util.simpletimetracker.domain.review.repo.ReviewRepo
import com.example.util.simpletimetracker.feature_statistics.review.WeekReviewFragment
import com.example.util.simpletimetracker.feature_statistics.review.WeekReviewViewModel
import com.example.util.simpletimetracker.utils.BaseUiTest
import com.example.util.simpletimetracker.utils.NavUtils
import com.example.util.simpletimetracker.utils.tryAction
import com.example.util.simpletimetracker.utils.selectTabAtPosition
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_statistics.R as reviewR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockWeekReviewUiTest : BaseUiTest() {
    @Inject lateinit var reviews: ReviewRepo
    @Inject lateinit var records: RecordRepo
    @Inject lateinit var recordActions: RecordInteractor
    @Inject lateinit var running: RunningRecordRepo
    @Inject lateinit var mutations: RecordMutationRepo
    @Inject lateinit var identity: LifeClockSettingsRepo
    @Inject lateinit var templates: RecordShortcutRepo
    @Inject lateinit var templateInteractor: RecordShortcutInteractor
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var fullBackup: FullBackupRepo
    @Inject lateinit var gate: DataRestoreGate

    @Before fun clearReviewFixtures() = runBlocking {
        database.openHelper.writableDatabase.execSQL("DELETE FROM reviewTrials")
        database.openHelper.writableDatabase.execSQL("DELETE FROM weeklyReviews")
    }

    @Test fun timeTabDefaultsToTheWeekAndOpensExperienceEditingDirectly() {
        addExperience()
        onView(withId(com.example.util.simpletimetracker.feature_main.R.id.mainTabs)).perform(selectTabAtPosition(1))
        tryAction { onView(allOf(withId(reviewR.id.reviewGrid), isDisplayed())).check(matches(isDisplayed())) }
        onView(allOf(withId(reviewR.id.reviewRecords), isDescendantOfA(withId(com.example.util.simpletimetracker.feature_records.R.id.timeWeekOverview))))
            .perform(scrollTo(), click())
        onData(anything()).atPosition(0).perform(click())
        onView(withId(com.example.util.simpletimetracker.feature_change_record.R.id.lifeClockHappinessButton)).perform(scrollTo()).check(matches(isDisplayed()))
        pressBack()
        onView(withId(com.example.util.simpletimetracker.feature_records.R.id.timeWeekToggle)).perform(click())
        onView(withText("周视图")).check(matches(isDisplayed()))
    }

    @Test fun visibleReviewEntryKeepsOptionalWordsAndDraftAcrossRecreation() {
        NavUtils.openReviewScreen()
        onView(withText("回顾")).check(matches(isDisplayed()))
        onView(allOf(withText("展开一周时间图"), isDisplayed())).perform(click())
        onView(allOf(withId(reviewR.id.reviewGrid), isDisplayed())).check(matches(isDisplayed()))
        onView(allOf(withId(reviewR.id.reviewDecisions), isDisplayed())).check(matches(isCompletelyDisplayed())).perform(click())
        tryAction { onView(withId(reviewR.id.reviewKeep)).check(matches(isDisplayed())) }
        onView(withId(reviewR.id.reviewKeep)).perform(scrollTo(), replaceText("Keep walking"))
        closeSoftKeyboard()
        activityScenarioRule.scenario.recreate()
        onView(withId(reviewR.id.reviewKeep)).perform(scrollTo()).check(matches(withText("Keep walking")))
        tryAction { onView(withId(reviewR.id.reviewSave)).perform(scrollTo()).check(matches(isEnabled())).perform(click()) }
        tryAction { runBlocking { assertEquals("Keep walking", reviews.getReviews().single().keep?.text) } }
        runBlocking {
            val review = reviews.getReviews().single()
            assertNull(review.timeAbundance)
            assertNull(review.adjust)
            assertTrue(review.pleasantRecords.isEmpty())
        }
        onView(withId(reviewR.id.reviewKeep)).perform(scrollTo(), replaceText(""))
        closeSoftKeyboard()
        tryAction { onView(withId(reviewR.id.reviewSave)).perform(scrollTo()).check(matches(isEnabled())).perform(click()) }
        tryAction { runBlocking { assertNull(reviews.getReviews().single().keep) } }
    }

    @Test fun chosenExperienceRetainsItsCaptionWhenTheSourceIsDeleted() {
        val record = addExperience()
        NavUtils.openReviewScreen()
        onView(withId(reviewR.id.reviewPleasant)).perform(scrollTo(), click())
        onData(anything()).atPosition(0).perform(click())
        onView(withText("保存")).perform(click())
        tryAction { runBlocking { assertEquals(record.id, reviews.getReviews().single().pleasantRecords.single().recordId) } }
        runBlocking { recordActions.remove(record.id) }
        withModel { it.refresh() }
        tryAction {
            onView(withText(containsString("原记录已删除，回顾文字保留"))).perform(scrollTo()).check(matches(isDisplayed()))
        }
        runBlocking { assertEquals("Evening reading", reviews.getReviews().single().pleasantRecords.single().caption) }
    }

    @Test fun pinsRejectFourSelectionsAndTemplateStartKeepsIdentityWithoutHistoricalRatings() {
        testUtils.addActivity("Template UI", color = firstColor, icon = firstIcon)
        val ids = runBlocking {
            val type = recordTypeRepo.get("Template UI").single()
            val identity = identity.getOrCreate()
            (1..4).map { index ->
                templateInteractor.add(
                    RecordShortcut(
                        target = RecordShortcut.Target.Record(
                            type.id, "",
                            listOf(RecordBase.Tag(identity.happinessTagId, 5.0)), "Template $index", "Morning reading",
                        ),
                    ),
                )
                templates.getAll().maxOf { it.id }
            }
        }
        NavUtils.openReviewScreen()
        onView(withId(reviewR.id.reviewPins)).perform(scrollTo(), click())
        (0..3).forEach { onData(anything()).atPosition(it).perform(click()) }
        onView(withText("保存")).perform(click())
        onView(withText("选择重要活动（最多三项）")).check(matches(isDisplayed()))
        runBlocking { assertTrue(reviews.getReviews().isEmpty()) }
        onData(anything()).atPosition(3).perform(click())
        onView(withText("保存")).perform(click())
        tryAction { runBlocking { assertEquals(ids.take(3), reviews.getReviews().single().pinnedTemplateIds) } }
        onView(withText("开始 · Template 1")).perform(scrollTo(), click())
        tryAction {
            runBlocking {
                val active = running.getAll().single()
                assertEquals(ids.first(), active.templateId)
                assertEquals("Morning reading", active.summary)
                assertTrue(active.tags.none { it.tagId in identity.getOrCreate().ratingTagIds })
            }
        }
        onView(withText("开始 · Template 1")).perform(scrollTo(), click())
        runBlocking { assertEquals(1, running.getAll().size) }
        withModel { }
        onView(withText("开始 · Template 2")).perform(scrollTo(), click())
        tryAction {
            runBlocking {
                val active = running.getAll().single()
                assertEquals(ids[1], active.templateId)
                val previous = records.getAll().single()
                assertEquals(ids.first(), previous.templateId)
                assertEquals(previous.timeEnded, active.timeStarted)
                assertTrue(previous.duration > 0)
            }
        }
    }

    @Test fun trialLinksAndWeeklyChoicesSurviveCompleteBackupRestore() {
        val source = addExperience()
        NavUtils.openReviewScreen()
        withModel { model -> model.chooseRecord(source, "adjust") }
        tryAction { runBlocking { assertNotNull(reviews.getReviews().single().adjust) } }
        withModel { model -> model.addTrial("Try morning reading", source) }
        tryAction { runBlocking { assertEquals("Try morning reading", reviews.getTrials().single().description) } }
        val next = runBlocking {
            val value = source.copy(
                id = 0, timeStarted = source.timeEnded, timeEnded = source.timeEnded + 900_000,
                summary = "Morning reading", tags = emptyList(),
            )
            value.copy(id = mutations.save(listOf(value)).single())
        }
        val trial = runBlocking { reviews.getTrials().single() }
        withModel { it.linkTrial(trial, setOf(next.id)) }
        tryAction { runBlocking { assertEquals(listOf(next.id), reviews.getTrials().single().relatedRecordIds) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("m5-roundtrip-", ".zip", context.cacheDir)
        onView(withId(reviewR.id.reviewKeep)).perform(scrollTo(), replaceText("Draft from before restore"))
        closeSoftKeyboard()
        try {
            runBlocking {
                val beforeReview = reviews.getReviews().single()
                val beforeTrial = reviews.getTrials().single()
                fullBackup.export(Uri.fromFile(file).toString())
                reviews.removeReview(beforeReview.id)
                reviews.removeTrial(beforeTrial.id)
                val preview = fullBackup.prepareRestore(Uri.fromFile(file).toString())
                fullBackup.restore(preview.token)
                assertEquals(beforeReview, reviews.getReviews().single())
                assertEquals(beforeTrial, reviews.getTrials().single())
                assertEquals(2, records.getAll().size)
            }
        } finally {
            file.delete()
        }
        withModel { it.refresh() }
        tryAction { onView(withId(reviewR.id.reviewKeep)).perform(scrollTo()).check(matches(withText(""))) }
        tryAction { onView(withText("Try morning reading")).perform(scrollTo()).check(matches(isDisplayed())) }
    }

    private fun addExperience(): Record {
        testUtils.addActivity("Review UI", color = firstColor, icon = firstIcon)
        return runBlocking {
            val type = recordTypeRepo.get("Review UI").single()
            val identity = identity.getOrCreate()
            val start = System.currentTimeMillis() - 7_200_000
            val record = Record(
                typeId = type.id, timeStarted = start, timeEnded = start + 900_000,
                comment = "", summary = "Evening reading", reviewState = RecordReviewState.CONFIRMED,
                tags = listOf(RecordBase.Tag(identity.happinessTagId, 2.0), RecordBase.Tag(identity.meaningTagId, 5.0)),
            )
            record.copy(id = mutations.save(listOf(record)).single())
        }
    }

    private fun withModel(action: (WeekReviewViewModel) -> Unit) {
        tryAction {
            activityScenarioRule.scenario.onActivity { activity ->
                fun find(fragments: List<Fragment>): WeekReviewFragment? = fragments.firstNotNullOfOrNull {
                    if (it is WeekReviewFragment && it.arguments?.getBoolean("timeOnly") != true) it else find(it.childFragmentManager.fragments)
                }
                val model = ViewModelProvider(requireNotNull(find(activity.supportFragmentManager.fragments)))[WeekReviewViewModel::class.java]
                assertNotNull(model.screen.value)
                assertEquals(false, model.busy.value)
                action(model)
            }
        }
    }
}

package com.example.util.simpletimetracker

import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.rhythm.ActivityDefaultsInstaller
import com.example.util.simpletimetracker.utils.*
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_base_adapter.R as baseR
import com.example.util.simpletimetracker.feature_change_record.R as detailsR
import com.example.util.simpletimetracker.feature_running_records.R as nowR
import com.example.util.simpletimetracker.feature_statistics.R as reviewR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UnfinishedFocusUiTest : BaseUiTest() {
    @Inject lateinit var defaults: ActivityDefaultsInstaller
    @Inject lateinit var records: RecordInteractor
    @Inject lateinit var running: RunningRecordInteractor
    @Inject lateinit var mutations: RecordMutationRepo

    private fun loadDefaults() {
        runBlocking { defaults.ensure() }
        NavUtils.openRecordsScreen()
        NavUtils.openRunningRecordsScreen()
    }

    private fun start(name: String) {
        val card = allOf(withId(baseR.id.viewRecordTypeItem), hasDescendant(withText(name)))
        tryAction { scrollRecyclerToView(nowR.id.rvRunningRecordsList, card) }
        onView(card).perform(click())
    }

    private fun stop() = tryAction {
        scrollRecyclerToView(nowR.id.rvRunningRecordsList, hasDescendant(withId(baseR.id.runningStop)))
        onView(allOf(withId(baseR.id.runningStop), isDisplayed())).perform(click())
    }

    @Test fun shortEnglishIsSeparateAndReasonCanBeSavedClearedAndKeptWhenEditingDetails() {
        loadDefaults()
        start("英语")
        tryAction { assertEquals(1, runBlocking { running.getAll().size }) }
        stop()
        onView(withText(containsString("不计入正式总时长"))).check(matches(isDisplayed()))
        clickOnViewWithText("继续专注")
        assertTrue(runBlocking { records.getAllIncludingUnfinished().isEmpty() })
        stop()
        clickOnViewWithText("现在结束")
        tryAction { assertTrue(runBlocking { running.getAll().isEmpty() }) }
        val unfinished = runBlocking { records.getAllIncludingUnfinished().single() }
        assertTrue(unfinished.isUnfinishedFocus)
        assertTrue(runBlocking { records.getAll().isEmpty() })
        NavUtils.openReviewScreen()
        tryAction { onView(withId(reviewR.id.reviewUnfinishedSummary)).perform(scrollTo()).check(matches(withText(containsString("1 次")))) }
        onView(withText("未完成原因 · 未填写")).perform(scrollTo(), click())
        onView(withId(reviewR.id.reviewUnfinishedReason)).perform(replaceText("临时接电话"))
        closeSoftKeyboard()
        clickOnViewWithText("保存")
        tryAction { assertEquals("临时接电话", runBlocking { records.get(unfinished.id)?.unfinishedReason }) }
        tryAction { onView(withText("未完成原因 · 临时接电话")).perform(scrollTo(), click()) }
        clickOnViewWithText("清除")
        tryAction { assertNull(runBlocking { records.get(unfinished.id)?.unfinishedReason }) }
        tryAction { onView(withText("未完成原因 · 未填写")).perform(scrollTo(), click()) }
        onView(withId(reviewR.id.reviewUnfinishedReason)).perform(replaceText("下次关闭消息通知"))
        closeSoftKeyboard()
        clickOnViewWithText("保存")
        tryAction { onView(withText("查看详情 · 英语")).perform(scrollTo(), click()) }
        onView(withId(detailsR.id.lifeClockUnfinishedStatus)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(detailsR.id.lifeClockSummaryInput)).perform(scrollTo(), replaceText("英语复习被中断"))
        closeSoftKeyboard()
        clickOnViewWithText(com.example.util.simpletimetracker.core.R.string.change_record_save)
        tryAction {
            val after = runBlocking { records.get(unfinished.id)!! }
            assertEquals("英语复习被中断", after.summary)
            assertEquals("下次关闭消息通知", after.unfinishedReason)
            assertTrue(after.isUnfinishedFocus)
            assertTrue(runBlocking { records.getAll().isEmpty() })
        }
        tryAction { onView(withText("未完成原因 · 下次关闭消息通知")).perform(scrollTo()).check(matches(isDisplayed())) }
        activityScenarioRule.scenario.recreate()
        tryAction { onView(withText("未完成原因 · 下次关闭消息通知")).perform(scrollTo()).check(matches(isDisplayed())) }
    }

    @Test fun switchingKeepsShortFocusAndStartsTheRequestedActivityOnce() {
        loadDefaults()
        start("英语")
        tryAction { assertEquals(1, runBlocking { running.getAll().size }) }
        val session = runBlocking { running.getAll().single().timerSession!!.id }
        start("日语")
        clickOnViewWithText("现在切换")
        tryAction {
            val active = runBlocking { running.getAll().single() }
            assertEquals("日语", runBlocking { recordTypeRepo.get(active.id)?.name })
            assertNotEquals(session, active.timerSession?.id)
            assertTrue(runBlocking { records.getAllIncludingUnfinished().single().isUnfinishedFocus })
        }
        assertTrue(runBlocking { records.getAll().isEmpty() })
    }

    @Test fun twentyTwoMinutesStaysARealFormalExperience() {
        loadDefaults()
        val startedAt = System.currentTimeMillis() - 22 * 60_000L
        runBlocking {
            val english = recordTypeRepo.getAll().single { it.presetKey == "learning.english" }
            mutations.start(RunningRecord(english.id, startedAt, "", emptyList()), startedAt)
        }
        NavUtils.openRecordsScreen()
        NavUtils.openRunningRecordsScreen()
        stop()
        clickOnViewWithText("现在结束")
        tryAction { assertTrue(runBlocking { running.getAll().isEmpty() }) }
        val record = runBlocking { records.getAll().single() }
        assertFalse(record.isUnfinishedFocus)
        assertEquals(startedAt, record.timeStarted)
        assertTrue(record.duration in 22 * 60_000L until 23 * 60_000L)
        assertEquals(1, runBlocking { records.getAllIncludingUnfinished().size })
        NavUtils.openReviewScreen()
        tryAction { onView(withId(reviewR.id.reviewUnfinishedSummary)).perform(scrollTo()).check(matches(withText(containsString("0 次")))) }
    }
}

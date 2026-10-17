package com.example.util.simpletimetracker

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.rhythm.Recovery
import com.example.util.simpletimetracker.utils.*
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_change_record.R as detailsR
import com.example.util.simpletimetracker.feature_base_adapter.R as baseR
import com.example.util.simpletimetracker.feature_main.R as mainR
import com.example.util.simpletimetracker.core.R as coreR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockV3UiTest : BaseUiTest() {
    @Inject lateinit var planning: PlanningRepo
    @Inject lateinit var records: RecordInteractor
    @Inject lateinit var settings: LifeClockSettingsRepo
    @Inject lateinit var mutations: com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
    @Inject lateinit var running: com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
    @Inject lateinit var reminders: com.example.util.simpletimetracker.domain.notifications.interactor.NotificationActivityInteractor
    @Inject lateinit var planningReminder: PlanningReminder

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 23)
    fun simultaneousPlansEachNotifyWithoutStartingAnActivity() = runBlocking {
        prefsInteractor.setActivityReminderDoNotDisturbStart(0)
        prefsInteractor.setActivityReminderDoNotDisturbEnd(0)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val clock = java.util.Calendar.getInstance()
        val first = PlanSchedule(title = "V3 planned reading", firstDate = PlanCalendar.dateText(clock),
            startMinute = clock.get(java.util.Calendar.HOUR_OF_DAY) * 60 + clock.get(java.util.Calendar.MINUTE), durationMinutes = 30, remind = true)
        val second = first.copy(id = java.util.UUID.randomUUID().toString(), title = "V3 buffer", kind = PlanKind.BUFFER)
        planning.save(first)
        planning.save(second)
        val date = PlanCalendar.date(first.firstDate, first.zoneId).timeInMillis
        val items = PlanCalendar.occurrences(listOf(first, second), emptyList(), date, date + 86_400_000)
        notifications.cancelAll()
        planningReminder.show(items[0].id, items[0].start)
        tryAction { assertEquals(items.map { it.id }.toSet(), notifications.activeNotifications.filter { it.id == 5832 }.map { it.tag }.toSet()) }
        planningReminder.show(items[0].id, items[0].start)
        tryAction { assertEquals(2, notifications.activeNotifications.count { it.id == 5832 }) }
        assertTrue(running.getAll().isEmpty())
        assertTrue(records.getAll().isEmpty())
        notifications.cancelAll()
        planningReminder.show(items[0].id, items[0].start)
        assertTrue(notifications.activeNotifications.none { it.id == 5832 })
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 23)
    fun intervalReminderAcceptsCurrentLatePhaseAndRejectsMutedOrStaleSessions() = runBlocking {
        prefsInteractor.setActivityReminderDoNotDisturbStart(0)
        prefsInteractor.setActivityReminderDoNotDisturbEnd(0)
        testUtils.addActivity("V3 reminder", color = firstColor, icon = firstIcon)
        val type = recordTypeRepo.getAll().single { it.name == "V3 reminder" }
        recordTypeRepo.add(type.copy(timerPolicy = com.example.util.simpletimetracker.domain.rhythm.TimerPolicy(com.example.util.simpletimetracker.domain.rhythm.TimerPolicy.Mode.INTERVAL, 30)))
        val start = System.currentTimeMillis() - 31 * 60_000
        val session = requireNotNull(mutations.start(com.example.util.simpletimetracker.domain.record.model.RunningRecord(type.id, start, "", emptyList()), start).started?.timerSession)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notifications.cancelAll()
        reminders.show("stale", start + 30 * 60_000)
        assertTrue(notifications.activeNotifications.isEmpty())
        reminders.show(session.id, start + 30 * 60_000)
        tryAction { assertTrue(notifications.activeNotifications.any { it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString() == "可以考虑收尾" }) }
        mutations.muteReminder(session.id)
        notifications.cancelAll()
        reminders.show(session.id, start + 30 * 60_000)
        assertTrue(notifications.activeNotifications.isEmpty())
        assertTrue(records.getAll().isEmpty())
    }

    @Test fun focusExitCanContinueOrEndImmediatelyWithoutRoundingTime() {
        val name = "V3 focus"
        testUtils.addActivity(name, color = firstColor, icon = firstIcon)
        runBlocking {
            val type = recordTypeRepo.getAll().single { it.name == name }
            recordTypeRepo.add(type.copy(timerPolicy = com.example.util.simpletimetracker.domain.rhythm.TimerPolicy(com.example.util.simpletimetracker.domain.rhythm.TimerPolicy.Mode.FOCUS_EXIT)))
            val start = System.currentTimeMillis() - 8 * 60_000
            mutations.start(com.example.util.simpletimetracker.domain.record.model.RunningRecord(type.id, start, "", emptyList()), start)
        }
        NavUtils.openRecordsScreen()
        NavUtils.openRunningRecordsScreen()
        tryAction { onView(allOf(withId(baseR.id.runningStop), isDisplayed())).perform(click()) }
        clickOnViewWithText("继续专注")
        assertEquals(1, runBlocking { running.getAll().size })
        assertTrue(runBlocking { records.getAll().isEmpty() })
        onView(allOf(withId(baseR.id.runningStop), isDisplayed())).perform(click())
        clickOnViewWithText("现在结束")
        tryAction { assertTrue(runBlocking { running.getAll().isEmpty() }) }
        val saved = runBlocking { records.getAll().single() }
        assertTrue(saved.timeEnded - saved.timeStarted in 480_000..600_000)
        assertEquals(com.example.util.simpletimetracker.domain.record.model.RecordReviewState.CONFIRMED, saved.reviewState)
    }

    @Test fun negativeMeaningNeutralMissingAndRecoveryRemainDistinct() {
        val name = "V3 experience"
        testUtils.addActivity(name, color = firstColor, icon = firstIcon)
        testUtils.addRecord(name)
        NavUtils.openRecordsScreen()
        fun open() = tryAction { clickOnView(allOf(withId(baseR.id.viewRecordItem), isDisplayed(), hasDescendant(withSubstring(name)))) }
        open()
        onView(withId(detailsR.id.lifeClockHappinessButton)).perform(scrollTo(), click())
        clickOnViewWithText("4")
        onView(withId(detailsR.id.lifeClockMeaningButton)).perform(scrollTo(), click())
        clickOnViewWithText("-3")
        onView(withId(detailsR.id.lifeClockCombinedScore)).perform(scrollTo()).check(matches(withText("这条经历合计 · +1")))
        onView(withId(detailsR.id.lifeClockRecoveryButton)).perform(scrollTo(), click())
        clickOnViewWithText("更累")
        clickOnViewWithText(coreR.string.change_record_save)
        runBlocking {
            val record = records.getAll().single()
            assertEquals(Recovery.MORE_TIRED, record.recovery)
            assertEquals(-3.0, record.tags.single { it.tagId == settings.getOrCreate().meaningTagId }.numericValue)
        }
        open()
        activityScenarioRule.scenario.recreate()
        onView(withId(detailsR.id.lifeClockMeaningButton)).perform(scrollTo(), click())
        clickOnViewWithText("0")
        onView(withId(detailsR.id.lifeClockCombinedScore)).perform(scrollTo()).check(matches(withText("这条经历合计 · +4")))
        clickOnViewWithText(coreR.string.change_record_save)
        runBlocking { assertEquals(0.0, records.getAll().single().tags.single { it.tagId == settings.getOrCreate().meaningTagId }.numericValue) }
        open()
        onView(withId(detailsR.id.lifeClockMeaningButton)).perform(scrollTo(), click())
        onData(org.hamcrest.CoreMatchers.equalTo(getString(coreR.string.life_clock_empty))).perform(click())
        onView(withId(detailsR.id.lifeClockRecoveryButton)).perform(scrollTo(), click())
        clickOnViewWithText("未填写")
        onView(withId(detailsR.id.lifeClockCombinedScore)).perform(scrollTo()).check(matches(withText("两项都填写后显示合计")))
        clickOnViewWithText(coreR.string.change_record_save)
        runBlocking {
            assertNull(records.getAll().single().recovery)
            assertNull(records.getAll().single().tags.single { it.tagId == settings.getOrCreate().meaningTagId }.numericValue)
        }
    }

    @Test fun titleOnlyTaskSurvivesRecreationAndCalendarDoesNotCreateActualTime() {
        val before = runBlocking { records.getAll().size }
        onView(withId(mainR.id.mainTabs)).perform(selectTabAtPosition(2))
        tryAction { onView(withText("新增待办")).perform(scrollTo(), click()) }
        onView(withHint("标题（必填）")).perform(replaceText("V3 important reading"))
        closeSoftKeyboard()
        clickOnViewWithText("保存")
        tryAction { runBlocking { assertTrue(planning.tasks().any { it.title == "V3 important reading" }) } }
        val task = runBlocking { planning.tasks().single { it.title == "V3 important reading" } }
        assertNull(task.important)
        assertNull(task.urgent)
        assertNull(task.templateId)
        runBlocking {
            planning.save(PlanSchedule(taskId = task.id, title = task.title, firstDate = PlanCalendar.dateText(java.util.Calendar.getInstance()),
                startMinute = 540, durationMinutes = 22, repeat = PlanRepeat.DAILY))
        }
        activityScenarioRule.scenario.recreate()
        tryAction { onView(withText("日历")).check(matches(isEnabled())).perform(scrollTo(), click()) }
        tryAction { onView(withText("周历")).check(matches(isDisplayed())) }
        assertEquals(before, runBlocking { records.getAll().size })
        NavUtils.openSettingsScreen()
        NavUtils.openRunningRecordsScreen()
    }

    @Test fun unsavedTaskDraftSurvivesRecreationAndCanStillBeCancelled() {
        onView(withId(mainR.id.mainTabs)).perform(selectTabAtPosition(2))
        tryAction { onView(withText("新增待办")).check(matches(isEnabled())).perform(scrollTo(), click()) }
        onView(withHint("标题（必填）")).perform(replaceText("Draft reading"))
        onView(withHint("备注（可选）")).perform(replaceText("After tea"))
        closeSoftKeyboard()
        activityScenarioRule.scenario.recreate()
        tryAction { onView(withHint("标题（必填）")).check(matches(withText("Draft reading"))) }
        onView(withHint("备注（可选）")).check(matches(withText("After tea")))
        clickOnViewWithText("取消")
        assertTrue(runBlocking { planning.tasks().isEmpty() })
    }

    @Test fun unsavedScheduleTitleAndExactDurationSurviveRecreation() {
        onView(withId(mainR.id.mainTabs)).perform(selectTabAtPosition(2))
        tryAction { onView(withText("安排时段／缓冲")).check(matches(isEnabled())).perform(scrollTo(), click()) }
        onData(org.hamcrest.Matchers.anything()).atPosition(0).perform(click())
        onView(withHint("时段名称（必填）")).perform(scrollTo(), replaceText("Draft buffer"))
        onView(withHint("时长（分钟，可精确到 1 分钟）")).perform(scrollTo(), replaceText("22"))
        closeSoftKeyboard()
        activityScenarioRule.scenario.recreate()
        tryAction { onView(withHint("时段名称（必填）")).check(matches(withText("Draft buffer"))) }
        onView(withHint("时长（分钟，可精确到 1 分钟）")).perform(scrollTo()).check(matches(withText("22")))
        clickOnViewWithText("保存")
        tryAction { runBlocking { assertEquals(22, planning.schedules().single { it.title == "Draft buffer" }.durationMinutes) } }
        assertTrue(runBlocking { records.getAll().isEmpty() })
    }
}

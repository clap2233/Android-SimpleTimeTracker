package com.example.util.simpletimetracker

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.rhythm.*
import com.example.util.simpletimetracker.utils.*
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import com.example.util.simpletimetracker.feature_running_records.R as nowR
import com.example.util.simpletimetracker.feature_base_adapter.R as baseR
import com.example.util.simpletimetracker.feature_change_record_type.R as typeR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockV32NowUiTest : BaseUiTest() {
    @Inject lateinit var defaults: ActivityDefaultsInstaller
    @Inject lateinit var running: RunningRecordInteractor
    @Inject lateinit var records: RecordInteractor

    private fun loadDefaults() {
        runBlocking { defaults.ensure() }
        NavUtils.openRecordsScreen()
        NavUtils.openRunningRecordsScreen()
    }
    private fun card(name: String) = allOf(withId(baseR.id.viewRecordTypeItem), hasDescendant(withText(name)))
    private fun start(name: String) {
        tryAction { scrollRecyclerToView(nowR.id.rvRunningRecordsList, card(name)) }
        onView(card(name)).perform(click())
        tryAction { assertEquals(name, runBlocking { recordTypeRepo.get(running.getAll().single().id)?.name }) }
    }
    private fun stop() = tryAction {
        scrollRecyclerToView(nowR.id.rvRunningRecordsList, hasDescendant(withId(baseR.id.runningStop)))
        onView(allOf(withId(baseR.id.runningStop), isDisplayed())).perform(click())
    }

    @Test fun defaultEnglishStartsFocusAndCanEndWithoutConfirmationState() {
        loadDefaults()
        start("英语")
        assertEquals(TimerPolicy.Mode.FOCUS_EXIT, runBlocking { running.getAll().single().timerSession?.policy?.mode })
        stop()
        clickOnViewWithText("继续专注")
        assertEquals(1, runBlocking { running.getAll().size })
        stop()
        clickOnViewWithText("现在结束")
        tryAction { assertTrue(runBlocking { running.getAll().isEmpty() }) }
        assertTrue(runBlocking { records.getAll().isEmpty() })
        assertTrue(runBlocking { records.getAllIncludingUnfinished().single().isUnfinishedFocus })
        activityScenarioRule.scenario.recreate()
        start("日语")
        stop()
        clickOnViewWithText("现在结束")
    }

    @Test fun defaultsPreserveLegacyIdsColorsAndRespectLaterEditsAndDeletions() = runBlocking {
        testUtils.addActivity("学习", color = firstColor, icon = firstIcon)
        val before = recordTypeRepo.getAll().single()
        defaults.ensure()
        val learning = recordTypeRepo.get(before.id)!!
        assertEquals(before.color, learning.color)
        assertEquals(before.icon, learning.icon)
        assertEquals("learning", learning.presetKey)
        assertEquals(TimerPolicy.Mode.FOCUS_EXIT, learning.timerPolicy?.mode)
        val all = recordTypeRepo.getAll()
        val english = all.single { it.presetKey == "learning.english" }
        assertEquals(learning.id, english.parentTypeId)
        assertEquals(listOf(30, 90, 45, 45, 15), all.filter { it.parentTypeId == all.single { r -> r.presetKey == "entertainment" }.id }.map { it.timerPolicy!!.intervalMinutes })
        recordTypeRepo.add(learning.copy(timerPolicy = TimerPolicy()))
        recordTypeRepo.remove(english.id)
        defaults.ensure()
        assertNull(recordTypeRepo.get(english.id))
        assertEquals(TimerPolicy(), recordTypeRepo.get(learning.id)?.timerPolicy)
        recordTypeRepo.remove(learning.id)
        val japanese = recordTypeRepo.getAll().single { it.presetKey == "learning.japanese" }
        assertNull(japanese.parentTypeId)
        assertEquals(TimerPolicy(), japanese.timerPolicy)
    }

    @Test @SdkSuppress(minSdkVersion = 23)
    fun actualAlarmRepeatsAfterUiConfigWithoutLegacyRecurrentSwitch() {
        loadDefaults()
        runBlocking {
            prefsInteractor.setActivityReminderDoNotDisturbStart(0)
            prefsInteractor.setActivityReminderDoNotDisturbEnd(0)
            prefsInteractor.setActivityReminderRecurrent(false)
        }
        tryAction { scrollRecyclerToView(nowR.id.rvRunningRecordsList, card("竞技游戏")) }
        onView(card("竞技游戏")).perform(longClick())
        tryAction { onView(withId(typeR.id.lifeClockTimerPolicy)).perform(scrollTo(), click()) }
        clickOnViewWithText("周期收尾提醒")
        onView(withHint("1—1440 分钟")).perform(replaceText("1"))
        closeSoftKeyboard()
        clickOnViewWithText("确定")
        onView(withId(typeR.id.btnChangeRecordTypeSave)).perform(click())
        start("竞技游戏")
        val session = runBlocking { running.getAll().single().timerSession!! }
        assertEquals(TimerPolicy(TimerPolicy.Mode.INTERVAL, 1), session.policy)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.cancelAll()
        repeat(2) {
            val deadline = SystemClock.elapsedRealtime() + 95_000
            while (notifications.activeNotifications.none { n -> n.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "可以考虑收尾" } && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(500)
            assertTrue("Real AlarmManager → receiver reminder did not arrive", notifications.activeNotifications.any { n -> n.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "可以考虑收尾" })
            notifications.cancelAll()
        }
        assertTrue(runBlocking { records.getAll().isEmpty() })
        assertEquals(session.id, runBlocking { running.getAll().single().timerSession?.id })
        stop()
    }
}

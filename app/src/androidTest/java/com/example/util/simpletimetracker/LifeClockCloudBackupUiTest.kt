package com.example.util.simpletimetracker

import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupRepo
import com.example.util.simpletimetracker.utils.BaseUiTest
import com.example.util.simpletimetracker.utils.NavUtils
import com.example.util.simpletimetracker.utils.tryAction
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_settings.R as settingsR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockCloudBackupUiTest : BaseUiTest() {
    @Inject lateinit var cloud: CloudBackupRepo
    @Test fun cloudEntryAndConnectionFieldsAreVisibleWithoutInstructions() {
        runBlocking { cloud.discardPending() }
        NavUtils.openSettingsScreen()
        onView(withId(settingsR.id.settingsData)).perform(click())
        onView(withId(settingsR.id.lifeClockCloudBackupButton)).check(matches(isCompletelyDisplayed())).perform(click())
        onView(withText("配置服务器与写入凭据")).perform(click())
        onView(withId(settingsR.id.cloudServerUrl)).check(matches(isCompletelyDisplayed())).perform(replaceText("https://124.221.50.129"))
        onView(withId(settingsR.id.cloudWriteCredential)).check(matches(isCompletelyDisplayed())).perform(replaceText("T".repeat(43)))
        closeSoftKeyboard()
        onView(withText("保存")).perform(click())
        tryAction { runBlocking { assertEquals(true, cloud.state().configured) } }
        runBlocking { assertEquals("https://124.221.50.129", cloud.state().serverUrl) }
    }
}

package com.example.util.simpletimetracker

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.simpletimetracker.utils.BaseUiTest
import com.example.util.simpletimetracker.utils.NavUtils
import com.example.util.simpletimetracker.utils.tryAction
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith
import com.example.util.simpletimetracker.feature_settings.R as settingsR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockAppearanceUiTest : BaseUiTest() {
    @Test fun settingsGroupsSurviveRecreationAndBackReturnsHome() {
        NavUtils.openSettingsScreen()
        onView(withId(settingsR.id.settingsData)).perform(scrollTo(), click())
        onView(withId(settingsR.id.lifeClockBackupButton)).check(matches(isDisplayed()))
        activityScenarioRule.scenario.recreate()
        tryAction { onView(withId(settingsR.id.lifeClockBackupButton)).check(matches(isDisplayed())) }
        pressBack()
        onView(withId(settingsR.id.settingsAppearance)).perform(scrollTo(), click())
        onView(withId(settingsR.id.settingsSectionTitle)).check(matches(withText("外观与显示")))
        pressBack()
        onView(withId(settingsR.id.settingsAbout)).perform(scrollTo(), click())
        onView(withText(getString(com.example.util.simpletimetracker.core.R.string.settings_rate))).check(doesNotExist())
        onView(withText(getString(com.example.util.simpletimetracker.core.R.string.settings_support_development))).check(doesNotExist())
        onView(withId(settingsR.id.settingsHelp)).perform(click())
        onView(withText("从这里开始")).check(matches(isDisplayed()))
        pressBack()
        pressBack()
        onView(withId(settingsR.id.settingsActivities)).perform(scrollTo()).check(matches(isDisplayed()))
    }
}

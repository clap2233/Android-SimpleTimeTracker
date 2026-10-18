package com.example.util.simpletimetracker.domain.rhythm

import org.junit.Assert.*
import org.junit.Test

class ActivityDefaultsTest {
    @Test fun learningIncludesEnglishAndJapaneseAndChildrenInheritFocus() {
        val root = ActivityDefaults.roots.single { it.presetKey == "learning" }.copy(id = 17)
        val children = ActivityDefaults.children(root)
        assertTrue(children.map { it.name }.containsAll(listOf("日语", "英语")))
        children.forEach { assertEquals(TimerPolicy.Mode.FOCUS_EXIT, ActivityPolicy.resolve(it, listOf(root) + children).mode) }
        val overridden = children.first().copy(timerPolicy = TimerPolicy())
        assertEquals(TimerPolicy.Mode.ORDINARY, ActivityPolicy.resolve(overridden, listOf(root)).mode)
    }
    @Test fun exerciseAndThinkingFocusWhileTravelAndOrdinaryWorkStayFree() {
        listOf("health" to "exercise", "work" to "thinking").forEach { (group, child) ->
            val root = ActivityDefaults.roots.single { it.presetKey == group }.copy(id = 17)
            assertEquals(TimerPolicy.Mode.ORDINARY, ActivityPolicy.resolve(root, listOf(root)).mode)
            val children = ActivityDefaults.children(root)
            assertEquals(TimerPolicy.Mode.FOCUS_EXIT, ActivityPolicy.resolve(children.single { it.presetKey == "$group.$child" }, listOf(root)).mode)
            assertEquals(1, children.count { ActivityPolicy.resolve(it, listOf(root)).mode == TimerPolicy.Mode.ORDINARY })
        }
    }
}

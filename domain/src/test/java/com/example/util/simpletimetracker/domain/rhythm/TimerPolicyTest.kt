package com.example.util.simpletimetracker.domain.rhythm

import org.junit.Assert.*
import org.junit.Test

class TimerPolicyTest {
    @Test fun exitPromptsProtectEveryUnfinishedSegment() {
        val policy = TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT)
        listOf(0L, 8, 14, 16, 22, 29, 37).forEach { minutes ->
            assertTrue(policy.needsExitConfirmation(1_000, 1_000 + minutes * 60_000))
        }
        listOf(15L, 30, 45).forEach { minutes ->
            assertFalse(policy.needsExitConfirmation(1_000, 1_000 + minutes * 60_000))
            assertFalse(policy.needsExitConfirmation(1_000, 1_000 + minutes * 60_000 + 59_999))
        }
    }
    @Test fun focusNeverSchedulesAnActiveReminder() {
        val policy = TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT)
        listOf(2_000L, 901_000, 1_321_000).forEach { now ->
            assertNull(policy.nextReminder(1_000, now, 900_000))
        }
    }
    @Test fun unfinishedFocusOnlyCoversTheFirstFifteenMinutes() {
        val policy = TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT)
        listOf(0L, 8 * 60_000, 899_999).forEach { assertTrue(policy.isUnfinished(1_000, 1_000 + it)) }
        listOf(900_000L, 959_999, 22 * 60_000, 37 * 60_000).forEach { assertFalse(policy.isUnfinished(1_000, 1_000 + it)) }
        assertFalse(policy.isUnfinished(1_000, 999))
        assertFalse(TimerPolicy().isUnfinished(1_000, 481_000))
        assertFalse(TimerPolicy(TimerPolicy.Mode.INTERVAL, 30).isUnfinished(1_000, 481_000))
    }
    @Test fun periodicRemindersStayOnStartRelativePhase() {
        val policy = TimerPolicy(TimerPolicy.Mode.INTERVAL, 30)
        assertEquals(3_601_000L, policy.nextReminder(1_000, 2_700_000, 900_000))
        assertEquals(5_401_000L, policy.nextReminder(1_000, 3_601_000, 900_000))
    }
    @Test fun ordinaryModePreservesDisabledRemindersAndNeverBlocksExit() {
        assertNull(TimerPolicy().nextReminder(1_000, 2_000, 0))
        assertNull(TimerPolicy().nextReminder(1_000, 2_000, 900_000))
        assertFalse(TimerPolicy().needsExitConfirmation(1_000, 481_000))
    }
    @Test fun intervalAndModeMustAgree() {
        assertThrows(IllegalArgumentException::class.java) { TimerPolicy(TimerPolicy.Mode.INTERVAL, 0) }
        assertThrows(IllegalArgumentException::class.java) { TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT, 30) }
    }
}

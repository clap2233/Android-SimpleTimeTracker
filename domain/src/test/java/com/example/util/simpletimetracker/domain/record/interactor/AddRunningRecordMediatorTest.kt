package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.notifications.interactor.ActivityStartedStoppedBroadcastInteractor
import com.example.util.simpletimetracker.domain.notifications.interactor.UpdateExternalViewsInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.TimerMutation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddRunningRecordMediatorTest {
    private val mutations = mock<RecordMutationRepo>()
    private val feedback = mock<RecordMutationFeedback>()
    private val running = mock<RunningRecordInteractor>()
    private val records = mock<RecordInteractor>()
    private val broadcast = mock<ActivityStartedStoppedBroadcastInteractor>()
    private val selection = mock<ShouldShowRecordDataSelectionInteractor>()
    private val rules = mock<ProcessRulesInteractor>()
    private val external = mock<UpdateExternalViewsInteractor>()
    private val clock = mock<CurrentTimestampProvider>()
    private val subject = AddRunningRecordMediator(mutations, feedback, running, records, broadcast, selection, rules, external, clock)
    private val now = 1_700_000_000_123L
    private fun record(id: Long, start: Long = now) = RunningRecord(id, start, "", emptyList())

    @Test
    fun startCommitsBeforePublishingAndUsesTheCapturedSwitchTime() = runBlocking {
        whenever(clock.get()).thenReturn(now)
        val previous = record(1, now - 760_123)
        whenever(mutations.start(any(), eq(now))).thenReturn(TimerMutation(listOf(previous), record(2)))
        assertTrue(subject.startTimer(2, emptyList(), "", useSelectedTags = true))
        val ordered = inOrder(mutations, broadcast, external)
        ordered.verify(mutations).start(eq(record(2)), eq(now))
        ordered.verify(broadcast).onActivityStopped(1, emptyList(), "", previous.timeStarted)
        ordered.verify(external).onRunningRecordRemove(1, emptyList(), false, false)
        ordered.verify(broadcast).onActionActivityStarted(2, emptyList(), "")
        ordered.verify(external).onRunningRecordAdd(2, emptyList(), true)
    }

    @Test
    fun rejectedTransitionDoesNotPublishAStart() = runBlocking {
        whenever(clock.get()).thenReturn(now)
        whenever(mutations.start(any(), any())).thenThrow(RecordMutationException(RecordMutationError.OVERLAP))
        assertFalse(subject.startTimer(2, emptyList(), "", useSelectedTags = true))
        verify(feedback).show(RecordMutationError.OVERLAP)
        verify(broadcast, never()).onActionActivityStarted(any(), any(), any())
    }

    @Test
    fun duplicateStartDoesNotPublishOrRestartTheTimer() = runBlocking {
        whenever(clock.get()).thenReturn(now)
        whenever(mutations.start(any(), any())).thenReturn(TimerMutation(emptyList(), null))
        assertFalse(subject.startTimer(2, emptyList(), "", useSelectedTags = true))
        verify(external, never()).onRunningRecordAdd(any(), any(), any())
    }

    @Test
    fun editingChangesOwnershipThroughOneMutationAndKeepsMetadata() = runBlocking {
        val original = record(1).copy(summary = "reading", zoneId = null, templateId = 7)
        assertTrue(subject.addAfterChange(2, now, "notes", emptyList(), original))
        verify(mutations).editRunning(1, original.copy(id = 2, comment = "notes"))
    }
}
package com.example.util.simpletimetracker.domain.record.interactor

import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RecordRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import com.example.util.simpletimetracker.domain.recordTag.repo.RecordToRecordTagRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LifeClockStopRecordTest {

    @Test
    fun stoppingKeepsActualTimeAndMetadataWithoutInventingAScore() = runBlocking {
        val records = mock<RecordRepo>()
        val tags = mock<RecordToRecordTagRepo>()
        val mutations = mock<RecordMutationRepo>()
        whenever(mutations.save(any())).thenReturn(listOf(91))
        val running = RunningRecord(
            id = 2,
            timeStarted = 1_700_000_000_123,
            comment = "notes",
            tags = listOf(RecordBase.Tag(5, 4.0, 1_700_000_100_456), RecordBase.Tag(6, null)),
            summary = "dishes + podcast",
            zoneId = null,
            templateId = 7,
        )
        val endedAt = running.timeStarted + 760_123

        RecordInteractor(records, tags, mutations, mock<RecordMutationFeedback>()).addFromRunning(running, endedAt)

        val stored = argumentCaptor<List<Record>>()
        verify(mutations).save(stored.capture())
        assertEquals(
            Record(
                typeId = running.id,
                timeStarted = running.timeStarted,
                timeEnded = endedAt,
                comment = running.comment,
                tags = running.tags,
                summary = running.summary,
                zoneId = null,
                templateId = running.templateId,
                reviewState = RecordReviewState.CONFIRMED,
            ),
            stored.firstValue.single(),
        )
    }
}

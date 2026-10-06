package com.example.util.simpletimetracker.data_local.record

import com.example.util.simpletimetracker.data_local.recordTag.RecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordTag.RunningRecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.domain.color.model.AppColor
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTag
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTagPurpose
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTagValueType
import org.junit.Assert.assertEquals
import org.junit.Test

class LifeClockRecordMapperTest {

    @Test
    fun endedRecordRoundTripPreservesMillisecondsMetadataAndOptionalScores() {
        val record = Record(
            id = 19,
            typeId = 0,
            timeStarted = 1_700_000_000_123,
            timeEnded = 1_700_000_900_123,
            comment = "washed dishes while listening",
            tags = listOf(RecordBase.Tag(5, 4.0, 1_700_090_000_456), RecordBase.Tag(6, null)),
            summary = "dishes + podcast",
            zoneId = "Asia/Shanghai",
            templateId = 7,
            reviewState = RecordReviewState.CONFIRMED,
        )
        val mapper = RecordDataLocalMapper()
        val tagMapper = RecordToRecordTagDataLocalMapper()
        val stored = RecordWithRecordTagsDBO(mapper.map(record), record.tags.map { tagMapper.map(record.id, it) })

        assertEquals(record, mapper.map(stored))
        assertEquals(record.copy(zoneId = null), mapper.map(stored.copy(record = stored.record.copy(zoneId = null))))
    }

    @Test
    fun runningRecordRoundTripPreservesMetadataAndRatingTime() {
        val record = RunningRecord(
            id = 2,
            timeStarted = 1_700_000_000_123,
            comment = "notes",
            tags = listOf(RecordBase.Tag(5, 3.0, 1_700_000_090_456)),
            summary = "reading",
            zoneId = null,
            templateId = 7,
        )
        val mapper = RunningRecordDataLocalMapper()
        val tagMapper = RunningRecordToRecordTagDataLocalMapper()
        val stored = RunningRecordWithRecordTagsDBO(mapper.map(record), record.tags.map { tagMapper.map(record.id, it) })

        assertEquals(record, mapper.map(stored))
    }

    @Test
    fun tagPurposeSurvivesStorageWithoutInterpretingNames() {
        val tag = RecordTag(
            id = 5,
            name = "Alex",
            icon = "person",
            color = AppColor(0, ""),
            iconColorSource = 0,
            note = "",
            valueType = RecordTagValueType.NONE,
            valueSuffix = "",
            purpose = RecordTagPurpose.PERSON,
        )
        val mapper = RecordTagDataLocalMapper()

        assertEquals(tag, mapper.map(mapper.map(tag)))
        assertEquals(tag.copy(purpose = RecordTagPurpose.GENERAL), mapper.map(mapper.map(tag).copy(purpose = 999)))
    }
}

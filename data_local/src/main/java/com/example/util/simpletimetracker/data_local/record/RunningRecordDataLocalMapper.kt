package com.example.util.simpletimetracker.data_local.record

import com.example.util.simpletimetracker.data_local.recordTag.RunningRecordToRecordTagDBO
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import javax.inject.Inject

class RunningRecordDataLocalMapper @Inject constructor() {

    fun map(dbo: RunningRecordWithRecordTagsDBO): RunningRecord {
        return RunningRecord(
            id = dbo.runningRecord.id,
            timeStarted = dbo.runningRecord.timeStarted,
            comment = dbo.runningRecord.comment,
            tags = dbo.recordTags.map(::map),
            summary = dbo.runningRecord.summary,
            zoneId = dbo.runningRecord.zoneId,
            templateId = dbo.runningRecord.templateId,
        )
    }

    fun map(domain: RunningRecord): RunningRecordDBO {
        return RunningRecordDBO(
            id = domain.id,
            timeStarted = domain.timeStarted,
            comment = domain.comment,
            tagId = 0,
            summary = domain.summary,
            zoneId = domain.zoneId,
            templateId = domain.templateId,
        )
    }

    fun map(dbo: RunningRecordToRecordTagDBO): RecordBase.Tag {
        return RecordBase.Tag(
            tagId = dbo.recordTagId,
            numericValue = dbo.recordTagNumericValue,
            ratedAt = dbo.ratedAt,
        )
    }
}

package com.example.util.simpletimetracker.data_local.record

import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDBO
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import javax.inject.Inject

class RecordDataLocalMapper @Inject constructor() {

    fun map(dbo: RecordWithRecordTagsDBO): Record {
        return Record(
            id = dbo.record.id,
            typeId = dbo.record.typeId,
            timeStarted = dbo.record.timeStarted,
            timeEnded = dbo.record.timeEnded,
            comment = dbo.record.comment,
            tags = dbo.recordTags.map(::map),
            summary = dbo.record.summary,
            zoneId = dbo.record.zoneId,
            templateId = dbo.record.templateId,
            recovery = dbo.record.recovery?.let(com.example.util.simpletimetracker.domain.rhythm.Recovery::valueOf),
            isUnfinishedFocus = dbo.record.isUnfinishedFocus,
            unfinishedReason = dbo.record.unfinishedReason,
            reviewState = RecordReviewState.fromStorageValue(dbo.record.reviewState),
        )
    }

    fun map(domain: Record): RecordDBO {
        return RecordDBO(
            id = domain.id,
            typeId = domain.typeId,
            timeStarted = domain.timeStarted,
            timeEnded = domain.timeEnded,
            comment = domain.comment,
            tagId = 0,
            summary = domain.summary,
            zoneId = domain.zoneId,
            templateId = domain.templateId,
            recovery = domain.recovery?.name,
            isUnfinishedFocus = domain.isUnfinishedFocus,
            unfinishedReason = domain.unfinishedReason,
            reviewState = domain.reviewState.storageValue,
        )
    }

    fun map(dbo: RecordToRecordTagDBO): RecordBase.Tag {
        return RecordBase.Tag(
            tagId = dbo.recordTagId,
            numericValue = dbo.recordTagNumericValue,
            ratedAt = dbo.ratedAt,
        )
    }
}

package com.example.util.simpletimetracker.data_local.recordTag

import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.recordTag.model.RunningRecordToRecordTag
import javax.inject.Inject

class RunningRecordToRecordTagDataLocalMapper @Inject constructor() {

    fun map(dbo: RunningRecordToRecordTagDBO): RunningRecordToRecordTag {
        return RunningRecordToRecordTag(
            runningRecordId = dbo.runningRecordId,
            recordTagId = dbo.recordTagId,
            recordTagNumericValue = dbo.recordTagNumericValue,
            ratedAt = dbo.ratedAt,
        )
    }

    fun map(recordId: Long, recordTag: RecordBase.Tag): RunningRecordToRecordTagDBO {
        return RunningRecordToRecordTagDBO(
            runningRecordId = recordId,
            recordTagId = recordTag.tagId,
            recordTagNumericValue = recordTag.numericValue,
            ratedAt = recordTag.ratedAt,
        )
    }

    fun map(domain: RunningRecordToRecordTag): RunningRecordToRecordTagDBO {
        return RunningRecordToRecordTagDBO(
            runningRecordId = domain.runningRecordId,
            recordTagId = domain.recordTagId,
            recordTagNumericValue = domain.recordTagNumericValue,
            ratedAt = domain.ratedAt,
        )
    }
}

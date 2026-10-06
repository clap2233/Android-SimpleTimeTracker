package com.example.util.simpletimetracker.data_local.recordTag

import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.recordTag.model.RecordToRecordTag
import javax.inject.Inject

class RecordToRecordTagDataLocalMapper @Inject constructor() {

    fun map(dbo: RecordToRecordTagDBO): RecordToRecordTag {
        return RecordToRecordTag(
            recordId = dbo.recordId,
            recordTagId = dbo.recordTagId,
            recordTagNumericValue = dbo.recordTagNumericValue,
            ratedAt = dbo.ratedAt,
        )
    }

    fun map(recordId: Long, recordTag: RecordBase.Tag): RecordToRecordTagDBO {
        return RecordToRecordTagDBO(
            recordId = recordId,
            recordTagId = recordTag.tagId,
            recordTagNumericValue = recordTag.numericValue,
            ratedAt = recordTag.ratedAt,
        )
    }

    fun map(domain: RecordToRecordTag): RecordToRecordTagDBO {
        return RecordToRecordTagDBO(
            recordId = domain.recordId,
            recordTagId = domain.recordTagId,
            recordTagNumericValue = domain.recordTagNumericValue,
            ratedAt = domain.ratedAt,
        )
    }
}

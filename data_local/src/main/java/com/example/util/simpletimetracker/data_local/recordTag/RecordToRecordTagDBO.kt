package com.example.util.simpletimetracker.data_local.recordTag

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "recordToRecordTag", primaryKeys = ["record_id", "record_tag_id"])
data class RecordToRecordTagDBO(
    @ColumnInfo(name = "record_id")
    val recordId: Long,

    @ColumnInfo(name = "record_tag_id")
    val recordTagId: Long,

    @ColumnInfo(name = "record_tag_numeric_value")
    val recordTagNumericValue: Double?,

    @ColumnInfo(name = "rated_at")
    val ratedAt: Long? = null,
)

package com.example.util.simpletimetracker.data_local.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runningRecords")
data class RunningRecordDBO(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "time_started")
    val timeStarted: Long,

    @ColumnInfo(name = "comment")
    val comment: String,

    @Deprecated("storing tag ids moved to a separate database")
    @ColumnInfo(name = "tag_id")
    val tagId: Long,

    @ColumnInfo(name = "summary", defaultValue = "''")
    val summary: String = "",

    @ColumnInfo(name = "zone_id")
    val zoneId: String? = null,

    @ColumnInfo(name = "template_id")
    val templateId: Long? = null,
    @ColumnInfo(name = "timer_session")
    val timerSession: String? = null,
)

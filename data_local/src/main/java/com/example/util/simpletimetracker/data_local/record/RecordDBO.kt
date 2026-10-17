package com.example.util.simpletimetracker.data_local.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    indices = [Index(value = ["time_started"]), Index(value = ["time_ended"])],
)
data class RecordDBO(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "type_id")
    val typeId: Long,

    @ColumnInfo(name = "time_started")
    val timeStarted: Long,

    @ColumnInfo(name = "time_ended")
    val timeEnded: Long,

    @ColumnInfo(name = "comment")
    val comment: String,

    @Deprecated("storing tag ids moved to a separate database")
    @ColumnInfo(name = "tag_id")
    val tagId: Long,

    @ColumnInfo(name = "review_state", defaultValue = "0")
    val reviewState: Int = 0,

    @ColumnInfo(name = "summary", defaultValue = "''")
    val summary: String = "",

    @ColumnInfo(name = "zone_id")
    val zoneId: String? = null,

    @ColumnInfo(name = "template_id")
    val templateId: Long? = null,

    @ColumnInfo(name = "recovery")
    val recovery: String? = null,
)

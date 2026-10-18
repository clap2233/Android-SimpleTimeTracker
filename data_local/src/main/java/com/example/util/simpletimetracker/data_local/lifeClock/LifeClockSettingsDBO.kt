package com.example.util.simpletimetracker.data_local.lifeClock

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lifeClockSettings")
data class LifeClockSettingsDBO(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,
    @ColumnInfo(name = "data_set_id")
    val dataSetId: String,
    @ColumnInfo(name = "happiness_tag_id")
    val happinessTagId: Long,
    @ColumnInfo(name = "meaning_tag_id")
    val meaningTagId: Long,
    @ColumnInfo(name = "restore_operation_id", defaultValue = "NULL")
    val restoreOperationId: String? = null,
    @ColumnInfo(name = "activity_defaults_version", defaultValue = "0")
    val activityDefaultsVersion: Int = 0,
)

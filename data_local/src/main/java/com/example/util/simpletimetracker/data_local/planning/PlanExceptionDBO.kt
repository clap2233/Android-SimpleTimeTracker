package com.example.util.simpletimetracker.data_local.planning

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planExceptions")
data class PlanExceptionDBO(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "content_json") val contentJson: String,
)

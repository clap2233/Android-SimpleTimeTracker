package com.example.util.simpletimetracker.data_local.planning

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planTasks")
data class PlanTaskDBO(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "content_json") val contentJson: String,
)

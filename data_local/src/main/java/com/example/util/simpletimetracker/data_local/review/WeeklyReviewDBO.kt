package com.example.util.simpletimetracker.data_local.review

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weeklyReviews", indices = [Index(value = ["week_start", "zone_id"], unique = true)])
data class WeeklyReviewDBO(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "week_start")
    val weekStart: String,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "content_json")
    val contentJson: String,
)

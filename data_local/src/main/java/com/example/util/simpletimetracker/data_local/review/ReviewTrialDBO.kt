package com.example.util.simpletimetracker.data_local.review

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reviewTrials",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyReviewDBO::class,
            parentColumns = ["id"], childColumns = ["review_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["review_id"])],
)
data class ReviewTrialDBO(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "review_id")
    val reviewId: Long? = null,
    @ColumnInfo(name = "content_json")
    val contentJson: String,
)

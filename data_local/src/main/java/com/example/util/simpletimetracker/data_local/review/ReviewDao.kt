package com.example.util.simpletimetracker.data_local.review

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReviewDao {
    @Query("SELECT * FROM weeklyReviews ORDER BY week_start DESC, zone_id, id")
    suspend fun getReviews(): List<WeeklyReviewDBO>

    @Query("SELECT * FROM weeklyReviews WHERE week_start = :weekStart AND zone_id = :zoneId")
    suspend fun getReview(weekStart: String, zoneId: String): WeeklyReviewDBO?

    @Query("SELECT * FROM weeklyReviews WHERE id = :id")
    suspend fun getReview(id: Long): WeeklyReviewDBO?

    @Upsert
    suspend fun upsertReview(review: WeeklyReviewDBO): Long

    @Query("DELETE FROM weeklyReviews WHERE id = :id")
    suspend fun removeReview(id: Long)

    @Query("SELECT * FROM reviewTrials ORDER BY id")
    suspend fun getTrials(): List<ReviewTrialDBO>

    @Upsert
    suspend fun upsertTrial(trial: ReviewTrialDBO): Long

    @Query("DELETE FROM reviewTrials WHERE id = :id")
    suspend fun removeTrial(id: Long)
}

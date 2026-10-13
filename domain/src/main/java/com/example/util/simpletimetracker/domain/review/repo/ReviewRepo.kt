package com.example.util.simpletimetracker.domain.review.repo

import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview

interface ReviewRepo {
    suspend fun getReviews(): List<WeeklyReview>
    suspend fun getReview(weekStart: String, zoneId: String): WeeklyReview?
    suspend fun saveReview(review: WeeklyReview): Long
    suspend fun removeReview(id: Long)
    suspend fun getTrials(): List<ReviewTrial>
    suspend fun saveTrial(trial: ReviewTrial): Long
    suspend fun saveTrialWithReview(review: WeeklyReview, trial: ReviewTrial): Long
    suspend fun removeTrial(id: Long)
}

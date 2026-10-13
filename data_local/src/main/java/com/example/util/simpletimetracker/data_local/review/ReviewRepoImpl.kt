package com.example.util.simpletimetracker.data_local.review

import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import com.example.util.simpletimetracker.domain.review.repo.ReviewRepo
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ReviewRepoImpl @Inject constructor(
    private val database: AppDatabase,
    private val mapper: ReviewDataLocalMapper,
    private val gate: DataRestoreGate = DataRestoreGate(),
) : ReviewRepo {

    override suspend fun getReviews(): List<WeeklyReview> = withContext(Dispatchers.IO) {
        database.reviewDao().getReviews().map(mapper::map)
    }

    override suspend fun getReview(weekStart: String, zoneId: String): WeeklyReview? = withContext(Dispatchers.IO) {
        database.reviewDao().getReview(weekStart, zoneId)?.let(mapper::map)
    }

    override suspend fun saveReview(review: WeeklyReview): Long = mutate {
        saveReviewInTransaction(review)
    }

    private suspend fun saveReviewInTransaction(review: WeeklyReview): Long {
        val dao = database.reviewDao()
        val existing = dao.getReview(review.weekStart, review.zoneId)
        require(existing == null || review.id == 0L || existing.id == review.id) { "Review week already exists." }
        val original = if (review.id > 0) dao.getReview(review.id) else null
        require(original == null || (original.weekStart == review.weekStart && original.zoneId == review.zoneId)) {
            "A review cannot replace a different week."
        }
        val id = review.id.takeIf { it > 0 } ?: existing?.id ?: 0L
        val insertedId = dao.upsertReview(mapper.map(review.copy(id = id)))
        return if (insertedId == -1L) id else insertedId
    }

    override suspend fun removeReview(id: Long) = mutate {
        database.reviewDao().removeReview(id)
    }

    override suspend fun getTrials(): List<ReviewTrial> = withContext(Dispatchers.IO) {
        database.reviewDao().getTrials().map(mapper::map)
    }

    override suspend fun saveTrial(trial: ReviewTrial): Long = mutate {
        val insertedId = database.reviewDao().upsertTrial(mapper.map(trial))
        if (insertedId == -1L) trial.id else insertedId
    }

    override suspend fun removeTrial(id: Long) = mutate {
        database.reviewDao().removeTrial(id)
    }

    override suspend fun saveTrialWithReview(review: WeeklyReview, trial: ReviewTrial): Long = mutate {
        val reviewId = saveReviewInTransaction(review)
        val insertedId = database.reviewDao().upsertTrial(mapper.map(trial.copy(reviewId = reviewId)))
        if (insertedId == -1L) trial.id else insertedId
    }

    private suspend fun <T> mutate(action: suspend () -> T): T {
        val token = gate.token()
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                if (!gate.canWrite(token)) throw RecordMutationException(RecordMutationError.DATA_RESTORING)
                action()
            }
        }
    }
}

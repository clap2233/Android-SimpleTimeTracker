package com.example.util.simpletimetracker.core.interactor

import android.content.Context
import android.widget.Toast
import com.example.util.simpletimetracker.core.R
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordMutationFeedbackImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecordMutationFeedback {
    override suspend fun show(reason: RecordMutationError) = withContext(Dispatchers.Main) {
        val resource = when (reason) {
            RecordMutationError.EMPTY_INTERVAL -> R.string.life_clock_empty_interval
            RecordMutationError.FUTURE_TIME -> R.string.life_clock_future_time
            RecordMutationError.OVERLAP -> R.string.life_clock_overlap
            RecordMutationError.MULTIPLE_RUNNING -> R.string.life_clock_multiple_running
            RecordMutationError.GAP -> R.string.life_clock_gap
            RecordMutationError.TAG_CONFLICT -> R.string.life_clock_tag_conflict
            RecordMutationError.FOCUS_RESULT_CONFLICT -> R.string.life_clock_focus_result_conflict
            RecordMutationError.INVALID_SCORE -> R.string.life_clock_invalid_score
            RecordMutationError.RESERVED_TAG -> R.string.life_clock_reserved_rating
            RecordMutationError.DATA_RESTORING -> R.string.life_clock_data_restoring
            else -> R.string.life_clock_record_changed
        }
        Toast.makeText(context, resource, Toast.LENGTH_LONG).show()
    }
}

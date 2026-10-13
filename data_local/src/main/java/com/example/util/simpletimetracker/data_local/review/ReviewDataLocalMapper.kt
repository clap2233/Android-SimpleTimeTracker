package com.example.util.simpletimetracker.data_local.review

import androidx.annotation.Keep
import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import com.google.gson.Gson
import javax.inject.Inject

class ReviewDataLocalMapper @Inject constructor() {

    private val gson = Gson()

    fun map(review: WeeklyReview): WeeklyReviewDBO = WeeklyReviewDBO(
        id = review.id,
        weekStart = review.weekStart,
        zoneId = review.zoneId,
        contentJson = gson.toJson(
            ReviewContent(
                timeAbundance = review.timeAbundance,
                pleasantRecords = review.pleasantRecords.map(::map),
                unpleasantRecords = review.unpleasantRecords.map(::map),
                keep = review.keep?.let(::map),
                adjust = review.adjust?.let(::map),
                attentionRecordIds = review.attentionRecordIds,
                pinnedTemplateIds = review.pinnedTemplateIds,
            ),
        ),
    )

    fun map(dbo: WeeklyReviewDBO): WeeklyReview {
        val content = requireNotNull(gson.fromJson(dbo.contentJson, ReviewContent::class.java))
        return WeeklyReview(
            id = dbo.id,
            weekStart = dbo.weekStart,
            zoneId = dbo.zoneId,
            timeAbundance = content.timeAbundance,
            pleasantRecords = content.pleasantRecords.orEmpty().map { map(requireNotNull(it)) },
            unpleasantRecords = content.unpleasantRecords.orEmpty().map { map(requireNotNull(it)) },
            keep = content.keep?.let(::map),
            adjust = content.adjust?.let(::map),
            attentionRecordIds = content.attentionRecordIds.orEmpty().map { requireNotNull(it) },
            pinnedTemplateIds = content.pinnedTemplateIds.orEmpty().map { requireNotNull(it) },
        )
    }

    fun map(trial: ReviewTrial): ReviewTrialDBO = ReviewTrialDBO(
        id = trial.id,
        reviewId = trial.reviewId,
        contentJson = gson.toJson(
            TrialContent(
                source = trial.source?.let(::map),
                description = trial.description,
                relatedRecordIds = trial.relatedRecordIds,
            ),
        ),
    )

    fun map(dbo: ReviewTrialDBO): ReviewTrial {
        val content = requireNotNull(gson.fromJson(dbo.contentJson, TrialContent::class.java))
        return ReviewTrial(
            id = dbo.id,
            reviewId = dbo.reviewId,
            source = content.source?.let(::map),
            description = requireNotNull(content.description),
            relatedRecordIds = content.relatedRecordIds.orEmpty().map { requireNotNull(it) },
        )
    }

    private fun map(reference: ReviewRecordReference): ReferenceContent =
        ReferenceContent(reference.recordId, reference.caption)

    private fun map(content: ReferenceContent): ReviewRecordReference =
        ReviewRecordReference(requireNotNull(content.recordId), content.caption.orEmpty())

    private fun map(choice: ReviewChoice): ChoiceContent =
        ChoiceContent(choice.text, choice.recordId, choice.templateId)

    private fun map(content: ChoiceContent): ReviewChoice =
        ReviewChoice(content.text.orEmpty(), content.recordId, content.templateId)

    @Keep
    private data class ReviewContent(
        val timeAbundance: String? = null,
        val pleasantRecords: List<ReferenceContent?>? = null,
        val unpleasantRecords: List<ReferenceContent?>? = null,
        val keep: ChoiceContent? = null,
        val adjust: ChoiceContent? = null,
        val attentionRecordIds: List<Long?>? = null,
        val pinnedTemplateIds: List<Long?>? = null,
    )

    @Keep
    private data class ReferenceContent(val recordId: Long? = null, val caption: String? = null)
    @Keep
    private data class ChoiceContent(val text: String? = null, val recordId: Long? = null, val templateId: Long? = null)
    @Keep
    private data class TrialContent(
        val source: ReferenceContent? = null,
        val description: String? = null,
        val relatedRecordIds: List<Long?>? = null,
    )
}

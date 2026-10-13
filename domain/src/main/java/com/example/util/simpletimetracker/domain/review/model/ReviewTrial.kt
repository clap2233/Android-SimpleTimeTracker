package com.example.util.simpletimetracker.domain.review.model

data class ReviewTrial(
    val id: Long = 0,
    val reviewId: Long? = null,
    val source: ReviewRecordReference? = null,
    val description: String,
    val relatedRecordIds: List<Long> = emptyList(),
) {
    init {
        require(id >= 0 && (reviewId == null || reviewId > 0))
        require(description.isNotBlank())
        require(relatedRecordIds.all { it > 0 } && relatedRecordIds.distinct().size == relatedRecordIds.size)
    }
}

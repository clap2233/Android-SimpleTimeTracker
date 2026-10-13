package com.example.util.simpletimetracker.domain.review.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class WeeklyReview(
    val id: Long = 0,
    val weekStart: String,
    val zoneId: String,
    val timeAbundance: String? = null,
    val pleasantRecords: List<ReviewRecordReference> = emptyList(),
    val unpleasantRecords: List<ReviewRecordReference> = emptyList(),
    val keep: ReviewChoice? = null,
    val adjust: ReviewChoice? = null,
    val attentionRecordIds: List<Long> = emptyList(),
    val pinnedTemplateIds: List<Long> = emptyList(),
) {
    init {
        require(id >= 0)
        val zone = TimeZone.getTimeZone(zoneId)
        require(zoneId in TimeZone.getAvailableIDs() || zone.id == zoneId) { "Unknown review time zone." }
        require(weekStart.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = zone
        }
        val date = requireNotNull(format.parse(weekStart))
        val calendar = Calendar.getInstance(zone).apply { time = date }
        require(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) { "A review starts on Monday." }
        require(pleasantRecords.map { it.recordId }.distinct().size == pleasantRecords.size)
        require(unpleasantRecords.map { it.recordId }.distinct().size == unpleasantRecords.size)
        require(attentionRecordIds.all { it > 0 } && attentionRecordIds.distinct().size == attentionRecordIds.size)
        require(pinnedTemplateIds.size <= 3 && pinnedTemplateIds.all { it > 0 })
        require(pinnedTemplateIds.distinct().size == pinnedTemplateIds.size)
    }
}

data class ReviewRecordReference(
    val recordId: Long,
    val caption: String = "",
) {
    init {
        require(recordId > 0)
    }
}

data class ReviewChoice(
    val text: String = "",
    val recordId: Long? = null,
    val templateId: Long? = null,
) {
    init {
        require(recordId == null || recordId > 0)
        require(templateId == null || templateId > 0)
        require(recordId == null || templateId == null) { "Choose either a record or a template." }
    }
}

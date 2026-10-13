package com.example.util.simpletimetracker.data_local.review

import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReviewDataLocalMapperTest {

    private val mapper = ReviewDataLocalMapper()

    @Test
    fun missingOptionalFieldsRestoreAsAnEmptyReview() {
        val empty = WeeklyReview(weekStart = "2026-10-05", zoneId = "Asia/Shanghai")
        assertEquals(empty, mapper.map(WeeklyReviewDBO(weekStart = empty.weekStart, zoneId = empty.zoneId, contentJson = "{}")))
        assertEquals(empty, mapper.map(mapper.map(empty)))
    }

    @Test
    fun choicesAndCaptionsSurviveRoundTripEvenWithDeletedSources() {
        val review = WeeklyReview(
            id = 3,
            weekStart = "2026-10-05",
            zoneId = "Asia/Shanghai",
            timeAbundance = "有时间散步",
            pleasantRecords = listOf(ReviewRecordReference(999, "散步")),
            unpleasantRecords = listOf(ReviewRecordReference(998, "晚间阅读")),
            keep = ReviewChoice("保留散步", recordId = 999),
            adjust = ReviewChoice("换到早晨阅读", templateId = 7),
            attentionRecordIds = listOf(998),
            pinnedTemplateIds = listOf(7, 8, 9),
        )
        assertEquals(review, mapper.map(mapper.map(review)))
    }

    @Test
    fun trialKeepsItsSourceAndExplicitlyLinkedRecords() {
        val trial = ReviewTrial(
            id = 4, reviewId = 3, source = ReviewRecordReference(998, "晚间阅读"),
            description = "下周试一次早晨阅读", relatedRecordIds = listOf(12, 13),
        )
        assertEquals(trial, mapper.map(mapper.map(trial)))
        assertEquals(trial.copy(reviewId = null), mapper.map(mapper.map(trial).copy(reviewId = null)))
    }

    @Test
    fun invalidStoredReferencesAndTooManyPinsAreRejected() {
        val row = WeeklyReviewDBO(weekStart = "2026-10-05", zoneId = "Asia/Shanghai", contentJson = "{}")
        listOf(
            "{\"pleasantRecords\":[{}]}",
            "{\"pleasantRecords\":[null]}",
            "{\"attentionRecordIds\":[0]}",
            "{\"pinnedTemplateIds\":[1,2,3,4]}",
            "{\"pinnedTemplateIds\":[1,1]}",
            "{\"keep\":{\"recordId\":1,\"templateId\":2}}",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) { mapper.map(row.copy(contentJson = json)) }
        }
    }

    @Test
    fun sharedSnapshotReviewAndTrialMapWithoutChangingTheirIdentityOrText() {
        val snapshot = JsonParser.parseString(File("../shared/fixtures/snapshot-v1.json").readText()).asJsonObject
        val reviewJson = snapshot.getAsJsonArray("weeklyReviews").first().asJsonObject
        val review = mapper.map(
            WeeklyReviewDBO(
                id = reviewJson.get("id").asString.toLong(),
                weekStart = reviewJson.get("weekStart").asString,
                zoneId = reviewJson.get("zoneId").asString,
                contentJson = reviewJson.toString(),
            ),
        )
        assertEquals(1L, review.id)
        assertEquals(listOf(7L, 8L), review.pinnedTemplateIds)
        assertEquals("保留户外阅读", review.keep?.text)
        assertEquals(review, mapper.map(mapper.map(review)))
        val trialJson = snapshot.getAsJsonArray("trials").first().asJsonObject
        val trial = mapper.map(
            ReviewTrialDBO(
                id = trialJson.get("id").asString.toLong(),
                reviewId = trialJson.get("reviewId").asString.toLong(),
                contentJson = trialJson.toString(),
            ),
        )
        assertEquals(ReviewRecordReference(4, "家中阅读"), trial.source)
        assertEquals(listOf(5L), trial.relatedRecordIds)
        assertEquals(trial, mapper.map(mapper.map(trial)))
    }

    @Test
    fun unknownZoneAndNonMondayWeekAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { WeeklyReview(weekStart = "2026-10-05", zoneId = "unknown") }
        assertThrows(IllegalArgumentException::class.java) { WeeklyReview(weekStart = "2026-10-06", zoneId = "Asia/Shanghai") }
        val fixedOffset = WeeklyReview(weekStart = "2026-10-05", zoneId = "GMT+08:00")
        assertEquals(fixedOffset, mapper.map(mapper.map(fixedOffset)))
    }
}

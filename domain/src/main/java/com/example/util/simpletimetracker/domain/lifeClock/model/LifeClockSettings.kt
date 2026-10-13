package com.example.util.simpletimetracker.domain.lifeClock.model

data class LifeClockSettings(
    val dataSetId: String,
    val happinessTagId: Long,
    val meaningTagId: Long,
) {
    val ratingTagIds: Set<Long> get() = setOf(happinessTagId, meaningTagId)
}

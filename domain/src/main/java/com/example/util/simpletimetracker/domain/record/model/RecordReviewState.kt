package com.example.util.simpletimetracker.domain.record.model

// Retained for older backups. Validity is determined by actual intervals, not this value.
enum class RecordReviewState(val storageValue: Int) {
    PENDING(0),
    CONFIRMED(1),
    ;

    companion object {
        fun fromStorageValue(value: Int): RecordReviewState {
            return entries.firstOrNull { it.storageValue == value } ?: PENDING
        }
    }
}

package com.example.util.simpletimetracker.domain.record.model

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

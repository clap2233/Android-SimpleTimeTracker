package com.example.util.simpletimetracker.domain.recordTag.model

enum class RecordTagPurpose(val storageValue: Int) {
    GENERAL(0),
    PERSON(1),
    ENVIRONMENT(2),
    ;

    companion object {
        fun fromStorageValue(value: Int): RecordTagPurpose {
            return entries.firstOrNull { it.storageValue == value } ?: GENERAL
        }
    }
}

package com.example.util.simpletimetracker.domain.record.model

enum class RecordMutationError {
    EMPTY_INTERVAL,
    FUTURE_TIME,
    OVERLAP,
    FOCUS_CONFIRMATION_REQUIRED,
    NOT_FOUND,
    INVALID_TYPE,
    MULTIPLE_RUNNING,
    GAP,
    TAG_CONFLICT,
    INVALID_SCORE,
    RESERVED_TAG,
    DATA_RESTORING,
}

class RecordMutationException(val reason: RecordMutationError) : IllegalArgumentException(reason.name)

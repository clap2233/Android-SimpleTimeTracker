package com.example.util.simpletimetracker.domain.rhythm

import java.util.UUID

data class TimerSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long,
    val policy: TimerPolicy,
    val muted: Boolean = false,
    val focusNotified: Boolean = false,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(startedAt >= 0)
    }
}

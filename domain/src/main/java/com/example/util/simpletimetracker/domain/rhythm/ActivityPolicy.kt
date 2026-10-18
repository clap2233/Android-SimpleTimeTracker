package com.example.util.simpletimetracker.domain.rhythm

import com.example.util.simpletimetracker.domain.recordType.model.RecordType

object ActivityPolicy {
    fun resolve(type: RecordType, all: List<RecordType>): TimerPolicy = type.timerPolicy
        ?: all.firstOrNull { it.id == type.parentTypeId }?.timerPolicy
        ?: TimerPolicy()

    fun label(policy: TimerPolicy): String = when (policy.mode) {
        TimerPolicy.Mode.ORDINARY -> "自由计时"
        TimerPolicy.Mode.FOCUS_EXIT -> "专注 · 至少 15 分钟"
        TimerPolicy.Mode.INTERVAL -> "每 ${policy.intervalMinutes} 分钟提醒"
    }
}

interface ActivityDefaultsInstaller {
    suspend fun ensure()
    suspend fun enableRoot(presetKey: String): Long
}

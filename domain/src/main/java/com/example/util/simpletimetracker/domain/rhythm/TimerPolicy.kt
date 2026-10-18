package com.example.util.simpletimetracker.domain.rhythm

data class TimerPolicy(
    val mode: Mode = Mode.ORDINARY,
    val intervalMinutes: Int = 0,
) {
    enum class Mode { ORDINARY, FOCUS_EXIT, INTERVAL }

    init {
        require(if (mode == Mode.INTERVAL) intervalMinutes in 1..1440 else intervalMinutes == 0)
    }

    fun nextReminder(start: Long, now: Long, ordinaryInterval: Long): Long? {
        val interval = when (mode) {
            Mode.ORDINARY -> return null
            Mode.FOCUS_EXIT -> return null
            Mode.INTERVAL -> intervalMinutes * 60_000L
        }
        if (interval <= 0 || now < start || start > Long.MAX_VALUE - interval) return null
        val periods = (now - start) / interval + 1
        if (periods > (Long.MAX_VALUE - start) / interval) return null
        return start + periods * interval
    }

    fun needsExitConfirmation(start: Long, now: Long): Boolean =
        mode == Mode.FOCUS_EXIT && ((now - start).coerceAtLeast(0) / 60_000L).let { it == 0L || it % 15L != 0L }

    fun isUnfinished(start: Long, end: Long): Boolean =
        mode == Mode.FOCUS_EXIT && end >= start && end - start < 15 * 60_000L

    companion object {
        val entertainmentPresets = listOf(
            "竞技游戏" to 30, "剧情游戏" to 90, "漫画" to 45, "长视频" to 45, "浏览推荐流" to 15,
        )
    }
}

enum class Recovery { MORE_ENERGY, UNCHANGED, MORE_TIRED }

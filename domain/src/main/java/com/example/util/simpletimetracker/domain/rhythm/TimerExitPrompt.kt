package com.example.util.simpletimetracker.domain.rhythm

interface TimerExitPrompt {
    suspend fun confirm(session: TimerSession, switching: Boolean): Boolean

    object Decline : TimerExitPrompt {
        override suspend fun confirm(session: TimerSession, switching: Boolean) = false
    }
}

package com.example.util.simpletimetracker.domain.lifeClock.repo

import com.example.util.simpletimetracker.domain.lifeClock.model.LifeClockSettings

interface LifeClockSettingsRepo {
    suspend fun getOrCreate(): LifeClockSettings
}

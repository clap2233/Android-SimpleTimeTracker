package com.example.util.simpletimetracker.data_local.lifeClock

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LifeClockSettingsDao {
    @Query("SELECT * FROM lifeClockSettings WHERE id = 1")
    suspend fun get(): LifeClockSettingsDBO?

    @Insert
    suspend fun insert(settings: LifeClockSettingsDBO)

    @Query("DELETE FROM lifeClockSettings")
    suspend fun clear()
}

package com.example.util.simpletimetracker.data_local.lifeClock

import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.recordTag.RecordTagDBO
import com.example.util.simpletimetracker.domain.lifeClock.model.LifeClockSettings
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LifeClockSettingsRepoImpl @Inject constructor(
    private val database: AppDatabase,
) : LifeClockSettingsRepo {

    override suspend fun getOrCreate(): LifeClockSettings = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.lifeClockSettingsDao()
            val settings = dao.get() ?: LifeClockSettingsDBO(
                dataSetId = UUID.randomUUID().toString(),
                happinessTagId = createRatingTag("快乐"),
                meaningTagId = createRatingTag("意义"),
            ).also { dao.insert(it) }
            check(settings.happinessTagId != settings.meaningTagId)
            check(database.recordTagDao().get(settings.happinessTagId) != null) { "Happiness tag is missing." }
            check(database.recordTagDao().get(settings.meaningTagId) != null) { "Meaning tag is missing." }
            LifeClockSettings(settings.dataSetId, settings.happinessTagId, settings.meaningTagId)
        }
    }

    private suspend fun createRatingTag(name: String): Long {
        return database.recordTagDao().insert(
            RecordTagDBO(
                id = 0, typeId = 0, name = name, icon = "", color = 0, colorInt = "",
                iconColorSource = 0, archived = false, note = "", valueType = 1, valueSuffix = null,
            ),
        )
    }
}

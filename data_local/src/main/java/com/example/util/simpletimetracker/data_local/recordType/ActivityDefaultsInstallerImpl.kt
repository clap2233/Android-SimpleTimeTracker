package com.example.util.simpletimetracker.data_local.recordType

import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.rhythm.ActivityDefaults
import com.example.util.simpletimetracker.domain.rhythm.ActivityDefaultsInstaller
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityDefaultsInstallerImpl @Inject constructor(
    private val database: AppDatabase,
    private val identity: LifeClockSettingsRepo,
    private val mapper: RecordTypeDataLocalMapper,
    private val types: RecordTypeRepoImpl,
) : ActivityDefaultsInstaller {
    override suspend fun ensure() = withContext(Dispatchers.IO) {
        identity.getOrCreate()
        database.withTransaction {
            val settings = checkNotNull(database.lifeClockSettingsDao().get())
            if (settings.activityDefaultsVersion < ActivityDefaults.VERSION) {
                ActivityDefaults.roots.forEach { install(it, upgradeDefaults = true) }
                database.lifeClockSettingsDao().setActivityDefaultsVersion(ActivityDefaults.VERSION)
            }
        }
        types.invalidateCache()
    }

    override suspend fun enableRoot(presetKey: String): Long = withContext(Dispatchers.IO) {
        val root = ActivityDefaults.roots.single { it.presetKey == presetKey }
        val id = database.withTransaction { install(root) }
        types.invalidateCache()
        id
    }

    private suspend fun install(default: RecordType, upgradeDefaults: Boolean = false): Long {
        val dao = database.recordTypeDao()
        val all = dao.getAll()
        val existing = all.singleOrNull { it.presetKey == default.presetKey }
            ?: all.filter { it.parentTypeId == null && it.presetKey == null && it.name == default.name }.singleOrNull()
        val root = existing?.let(mapper::map)?.let { old ->
            val policy = if (upgradeDefaults && (old.timerPolicy == null || old.timerPolicy?.mode == TimerPolicy.Mode.ORDINARY)) default.timerPolicy else old.timerPolicy
            old.copy(presetKey = default.presetKey, timerPolicy = policy)
        } ?: default
        val id = dao.insert(mapper.map(root))
        ActivityDefaults.children(root.copy(id = id)).forEach { child ->
            if (dao.getAll().none { it.presetKey == child.presetKey }) dao.insert(mapper.map(child))
        }
        return id
    }
}

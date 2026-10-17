package com.example.util.simpletimetracker.feature_change_record.viewModel

import androidx.lifecycle.MutableLiveData
import com.example.util.simpletimetracker.core.base.SingleLiveEvent
import com.example.util.simpletimetracker.domain.lifeClock.model.LifeClockSettings
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.record.model.RecordPhoto
import com.example.util.simpletimetracker.domain.record.repo.RecordPhotoRepo
import com.example.util.simpletimetracker.feature_change_record.R
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RecordDetailsDraft @Inject constructor(
    private val settings: LifeClockSettingsRepo,
    val photoRepo: RecordPhotoRepo,
) {
    val recovery = MutableLiveData<com.example.util.simpletimetracker.domain.rhythm.Recovery?>(null)
    var running = false
    val summaryChanges = MutableLiveData("")
    var summary: String = ""
        set(value) {
            field = value
            summaryChanges.value = value
        }
    var identity: LifeClockSettings? = null
        private set
    val photos = MutableLiveData<List<RecordPhoto>>(emptyList())
    val busy = MutableLiveData(false)
    val ready = MutableLiveData(false)
    val error = SingleLiveEvent<Int>()
    val imported = mutableListOf<RecordPhoto>()
    private val mutex = Mutex()

    suspend fun load(summary: String, ownerId: Long, running: Boolean) = mutex.withLock {
        if (ready.value == true) return@withLock
        this.running = running
        identity = settings.getOrCreate()
        this.summary = summary
        photos.value = if (ownerId > 0) photoRepo.get(ownerId, running) else emptyList()
        ready.value = true
    }

    suspend fun importPhotos(uris: List<String>) {
        busy.value = true
        try {
            uris.forEach { uri ->
                try {
                    val photo = photoRepo.importPhoto(uri)
                    imported.add(photo)
                    photos.value = photos.value.orEmpty() + photo
                } catch (_: IOException) {
                    error.setValue(R.string.life_clock_photo_import_failed)
                } catch (_: SecurityException) {
                    error.setValue(R.string.life_clock_photo_import_failed)
                }
            }
        } finally {
            busy.value = false
        }
    }

    fun remove(photo: RecordPhoto) {
        photos.value = photos.value.orEmpty().filterNot { it == photo }
    }
}

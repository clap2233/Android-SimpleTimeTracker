package com.example.util.simpletimetracker.feature_settings.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.SingleLiveEvent
import com.example.util.simpletimetracker.domain.backup.model.FullBackupException
import com.example.util.simpletimetracker.domain.backup.model.FullBackupFailure
import com.example.util.simpletimetracker.domain.backup.model.FullBackupPreview
import com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo
import com.example.util.simpletimetracker.domain.notifications.interactor.UpdateExternalViewsInteractor
import com.example.util.simpletimetracker.domain.statistics.interactor.SettingsDataUpdateInteractor
import com.example.util.simpletimetracker.feature_settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel
class FullBackupViewModel @Inject constructor(
    private val backup: FullBackupRepo,
    private val external: UpdateExternalViewsInteractor,
    private val updates: SettingsDataUpdateInteractor,
) : ViewModel() {
    val busy = MutableLiveData(false)
    val preview = SingleLiveEvent<FullBackupPreview>()
    val message = SingleLiveEvent<Int>()
    val restored = SingleLiveEvent<Unit>()

    fun export(uri: String, kind: String) = runWork {
        when (kind) {
            "zip" -> backup.export(uri)
            "csv" -> backup.exportCsv(uri)
            "ics" -> backup.exportIcs(uri)
            "recovery" -> backup.exportRecoveryCopy(uri)
            else -> error("Unknown export")
        }
        message.setValue(R.string.life_clock_backup_done)
    }
    fun prepare(uri: String) = runWork { preview.setValue(backup.prepareRestore(uri)) }
    fun discard(token: String) = viewModelScope.launch { backup.discard(token) }
    fun restore(token: String) = runWork {
        backup.restore(token)
        external.onBackupRestore()
        updates.send()
        message.setValue(R.string.life_clock_restore_done)
        restored.setValue(Unit)
    }
    suspend fun hasRecoveryCopy(): Boolean = backup.hasRecoveryCopy()

    private fun runWork(action: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value == true) return@launch
        busy.value = true
        try {
            action()
        } catch (error: FullBackupException) {
            message.setValue(
                when (error.failure) {
                    FullBackupFailure.INVALID_FORMAT -> R.string.life_clock_backup_invalid
                    FullBackupFailure.UNSUPPORTED_VERSION -> R.string.life_clock_backup_unsupported
                    FullBackupFailure.INVALID_MEDIA -> R.string.life_clock_backup_media
                    FullBackupFailure.NO_SPACE -> R.string.life_clock_backup_space
                    FullBackupFailure.BUSY -> R.string.life_clock_backup_busy
                    FullBackupFailure.IO -> R.string.life_clock_backup_io
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            message.setValue(R.string.life_clock_backup_io)
        } finally {
            busy.value = false
        }
    }
}

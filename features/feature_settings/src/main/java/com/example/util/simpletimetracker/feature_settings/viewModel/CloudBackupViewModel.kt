package com.example.util.simpletimetracker.feature_settings.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.SingleLiveEvent
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupException
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupRepo
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel
class CloudBackupViewModel @Inject constructor(private val backup: CloudBackupRepo) : ViewModel() {
    val state = MutableLiveData<CloudBackupState>()
    val busy = MutableLiveData(false)
    val message = SingleLiveEvent<String>()

    fun refresh() = viewModelScope.launch { if (busy.value != true) state.value = backup.state() }
    fun configure(url: String, token: String) = work {
        backup.configure(url, token)
        message.setValue("连接设置已保存，写入凭据由手机加密保存。")
    }
    fun discard() = work {
        backup.discardPending()
        message.setValue("未完成的上传已放弃。本地记录和服务器备份仍保留。")
    }
    fun upload() = work {
        val result = backup.upload()
        message.setValue(if (result.isLatest) "云备份完成，电脑可以读取这一份完整备份。" else "这份备份已提交；服务器已有较新的完整备份，没有覆盖它。")
    }
    private fun work(action: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value == true) return@launch
        busy.value = true
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: CloudBackupException) {
            message.setValue(explain(error.code))
        } catch (_: Exception) {
            message.setValue("暂时未能完成云备份。本地记录保留，请检查网络后重试。")
        } finally {
            busy.value = false
            state.value = backup.state()
        }
    }
    private fun explain(code: String): String = when (code) {
        "INVALID_ADDRESS" -> "请输入 HTTPS 服务器地址，不包含路径、账号或查询参数。"
        "INVALID_CREDENTIAL", "AUTH_REQUIRED" -> "写入凭据不正确，请使用手机专用的写入凭据。"
        "NOT_CONFIGURED" -> "请先配置服务器地址和手机写入凭据。"
        "PENDING_UPLOAD" -> "尚有未完成的上传，请先重试或放弃这次上传，再修改连接。"
        "DATASET_CONFLICT" -> "服务器保存的是另一个数据集。这次没有覆盖它，请先核对或恢复对应的完整备份。"
        "DATASET_CHANGED" -> "本地数据集已恢复或更换，请放弃旧上传，再备份当前记录。"
        "STALE_PARENT", "OLDER_SNAPSHOT", "SNAPSHOT_ID_CONFLICT" -> "服务器已有新备份，旧上传不能覆盖它。请放弃这次上传，再创建当前记录的新备份。"
        "PHOTO_MISSING", "PHOTO_CHANGED", "PHOTO_MISSING_OR_INVALID", "PHOTO_INVALID", "INVALID_BACKUP" -> "照片或快照未通过校验，没有替换服务器已有备份。请保留本地完整 ZIP 并检查记录。"
        "UPLOAD_BUSY", "BACKUP_UNAVAILABLE" -> "服务器暂时无法接收备份，上一份完整备份仍保留，请稍后重试。"
        "DATA_RESTORING" -> "数据正在恢复，稍后再进行云备份。"
        "MANIFEST_TOO_LARGE" -> "记录清单超过 64 MiB，请先使用本地完整备份保存数据。"
        else -> "上传没有完成。本地记录保留，失败重试会继续上次的快照。"
    }
}

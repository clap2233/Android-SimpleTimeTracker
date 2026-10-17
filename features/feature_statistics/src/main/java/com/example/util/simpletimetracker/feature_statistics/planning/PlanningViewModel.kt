package com.example.util.simpletimetracker.feature_statistics.planning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.domain.record.interactor.AddRunningRecordMediator
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.recordShortcut.interactor.RecordShortcutInteractor
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class PlanningScreen(
    val tasks: List<PlanTask>, val schedules: List<PlanSchedule>, val exceptions: List<PlanException>,
    val links: List<PlanRecordLink>, val templates: List<RecordShortcut>, val types: List<RecordType>, val records: List<Record>,
    val writeToken: Long,
)

@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val repo: PlanningRepo, private val types: RecordTypeInteractor,
    private val templates: RecordShortcutInteractor, private val records: RecordInteractor,
    private val timer: AddRunningRecordMediator, private val reminder: PlanningReminder,
    private val saved: SavedStateHandle,
    private val gate: DataRestoreGate,
) : ViewModel() {
    val screen = MutableLiveData<PlanningScreen>()
    val error = MutableLiveData<String?>(null)
    val busy = MutableLiveData(false)
    var date: String
        get() = saved.get<String>("date") ?: PlanCalendar.dateText(Calendar.getInstance())
        set(value) { saved["date"] = value }
    var week: Boolean
        get() = saved.get<Boolean>("week") ?: false
        set(value) { saved["week"] = value }
    var quadrant: Int
        get() = saved.get<Int>("quadrant") ?: 0
        set(value) { saved["quadrant"] = value }

    fun refresh() {
        if (busy.value == true) return
        viewModelScope.launch {
            busy.value = true
            try { load() } finally { busy.value = false }
        }
    }
    private suspend fun load() {
        try {
            val token = gate.token()
            val data = PlanningScreen(repo.tasks(), repo.schedules(), repo.exceptions(), repo.links(), templates.getAll(), types.getAll(), records.getAll(), token)
            if (gate.canWrite(token)) screen.value = data
            reminder.reschedule()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = "无法读取规划：${e.message}" }
    }
    private fun write(action: suspend () -> Unit) {
        if (busy.value == true) return
        val token = screen.value?.writeToken ?: return
        viewModelScope.launch {
            busy.value = true
            try { check(gate.canWrite(token)) { "数据已恢复，请重新打开规划后再修改" }; action(); load() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = "未保存：${e.message}" }
            finally { busy.value = false }
        }
    }
    fun save(task: PlanTask) = write { repo.save(task) }
    fun save(schedule: PlanSchedule) = write { repo.save(schedule) }
    fun status(value: PlanOccurrence, status: PlanStatus) = write {
        val existing = repo.exceptions().firstOrNull { it.id == value.id }
        repo.save((existing ?: PlanException(value.schedule.id, value.date)).copy(status = status))
    }
    fun edit(value: PlanOccurrence, replacement: PlanSchedule?, following: Boolean) = write {
        repo.editSchedule(value.schedule.id, value.date, replacement, following)
    }
    fun start(value: PlanOccurrence, typeId: Long, templateId: Long?) = write {
        val target = templateId?.let { templates.get(it)?.target as? RecordShortcut.Target.Record }
        timer.startTimer(typeId, target?.tags.orEmpty(), target?.comment.orEmpty(), summary = target?.summary.orEmpty(), templateId = templateId, plan = value)
    }
    fun addPreset(name: String, minutes: Int, typeId: Long) = write {
        templates.add(RecordShortcut(target = RecordShortcut.Target.Record(typeId, "", emptyList(), name = name,
            timerPolicy = TimerPolicy(TimerPolicy.Mode.INTERVAL, minutes))))
    }
}

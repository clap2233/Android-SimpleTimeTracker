package com.example.util.simpletimetracker.feature_statistics.review

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.SingleLiveEvent
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.lifeClock.model.LifeClockSettings
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.record.interactor.AddRunningRecordMediator
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.recordShortcut.repo.RecordShortcutRepo
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTag
import com.example.util.simpletimetracker.domain.recordTag.repo.RecordTagRepo
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.recordType.repo.RecordTypeRepo
import com.example.util.simpletimetracker.domain.review.interactor.WeekReviewCalculator
import com.example.util.simpletimetracker.domain.review.interactor.WeekReviewStats
import com.example.util.simpletimetracker.domain.review.model.ReviewChoice
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.domain.review.model.WeeklyReview
import com.example.util.simpletimetracker.domain.review.repo.ReviewRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import timber.log.Timber

data class WeekReviewScreen(
    val review: WeeklyReview,
    val stats: WeekReviewStats,
    val identity: LifeClockSettings,
    val types: List<RecordType>,
    val tags: List<RecordTag>,
    val templates: List<RecordShortcut>,
    val trials: List<ReviewTrial>,
    val allRecords: List<Record>,
    val writeToken: Long,
    val runningRecords: List<RunningRecord>,
    val comparisonRecords: List<Record>,
)

@HiltViewModel
class WeekReviewViewModel @Inject constructor(
    private val reviews: ReviewRepo,
    private val records: RecordInteractor,
    private val mutations: RecordMutationRepo,
    private val running: RunningRecordInteractor,
    private val types: RecordTypeRepo,
    private val tags: RecordTagRepo,
    private val shortcuts: RecordShortcutRepo,
    private val settings: LifeClockSettingsRepo,
    private val start: AddRunningRecordMediator,
    private val gate: DataRestoreGate,
    private val saved: SavedStateHandle,
    val calculator: WeekReviewCalculator,
) : ViewModel() {
    var gridExpanded: Boolean
        get() = saved["gridExpanded"] ?: false
        set(value) {
            saved["gridExpanded"] = value
        }
    val screen = MutableLiveData<WeekReviewScreen>()
    val busy = MutableLiveData(false)
    val message = SingleLiveEvent<String>()
    private var loading: Job? = null
    val zoneId: String = saved.get<String>("zone") ?: TimeZone.getDefault().id.also { saved["zone"] = it }
    private var week: String
        get() = saved.get<String>("week") ?: calculator.weekStart(System.currentTimeMillis(), zoneId)
        set(value) {
            saved["week"] = value
        }
    val people: Set<Long> get() = saved.get<LongArray>("people")?.toSet().orEmpty()
    val environments: Set<Long> get() = saved.get<LongArray>("environments")?.toSet().orEmpty()
    val selectedDay: Int get() = saved.get<Int>("selectedDay") ?: calculator.days(week, zoneId)
        .indexOfFirst { System.currentTimeMillis() in it.start until it.end }.coerceIn(0, 6)
    fun selectDay(day: Int) {
        if (busy.value == true) return
        saved["selectedDay"] = day
        screen.value = screen.value
    }
    fun draft(field: Int, fallback: String): String = saved.get<String>("draft:$week:$field") ?: fallback
    fun setDraft(field: Int, text: String) {
        saved["draft:$week:$field"] = text
    }

    fun refresh() {
        if (busy.value == true) return
        loading?.cancel()
        loading = viewModelScope.launch { load() }
    }

    fun moveWeek(amount: Int) {
        if (busy.value == true) return
        week = calculator.shiftWeek(week, zoneId, amount)
        refresh()
    }

    fun currentWeek() {
        if (busy.value == true) return
        week = calculator.weekStart(System.currentTimeMillis(), zoneId)
        refresh()
    }

    fun filter(people: Set<Long>, environments: Set<Long>) {
        if (busy.value == true) return
        saved["people"] = people.toLongArray()
        saved["environments"] = environments.toLongArray()
        refresh()
    }

    fun saveWords(abundance: String, keep: String, adjust: String) = changeReview(clearWords = true) {
        it.copy(
            timeAbundance = abundance.takeIf(String::isNotBlank),
            keep = keep.takeIf(String::isNotBlank)?.let { text -> (it.keep ?: ReviewChoice()).copy(text = text) },
            adjust = adjust.takeIf(String::isNotBlank)?.let { text -> (it.adjust ?: ReviewChoice()).copy(text = text) },
        )
    }

    fun chooseExperiences(ids: Set<Long>, pleasant: Boolean) = changeReview { review ->
        val previous = if (pleasant) review.pleasantRecords else review.unpleasantRecords
        val data = requireNotNull(screen.value)
        val references = ids.map { id ->
            previous.firstOrNull { it.recordId == id }
                ?: ReviewRecordReference(id, caption(requireNotNull(data.allRecords.find { it.id == id }), data))
        }
        if (pleasant) review.copy(pleasantRecords = references) else review.copy(unpleasantRecords = references)
    }

    fun chooseRecord(record: Record, choice: String) = changeReview(
        clearField = when (choice) {
            "keep" -> com.example.util.simpletimetracker.feature_statistics.R.id.reviewKeep
            "adjust" -> com.example.util.simpletimetracker.feature_statistics.R.id.reviewAdjust
            else -> null
        },
    ) { review ->
        val reference = ReviewRecordReference(record.id, caption(record, requireNotNull(screen.value)))
        when (choice) {
            "keep" -> review.copy(keep = ReviewChoice(reference.caption, record.id))
            "adjust" -> review.copy(adjust = ReviewChoice(reference.caption, record.id))
            else -> review.copy(
                attentionRecordIds = if (record.id in review.attentionRecordIds) {
                    review.attentionRecordIds - record.id
                } else {
                    review.attentionRecordIds + record.id
                },
            )
        }
    }

    fun pinTemplates(ids: Set<Long>) = changeReview { it.copy(pinnedTemplateIds = ids.toList()) }

    fun createTemplate(record: Record, name: String) = work {
        if (name.isBlank()) return@work
        val data = requireNotNull(screen.value)
        shortcuts.add(
            RecordShortcut(
                target = RecordShortcut.Target.Record(
                    record.typeId, record.comment, record.tags.filterNot { it.tagId in data.identity.ratingTagIds }, name.trim(), record.summary,
                ),
            ),
        )
        message.setValue("已保存活动模板，可在重要活动中置顶。")
    }

    fun startTemplate(id: Long) = work {
        val target = shortcuts.get(id)?.target as? RecordShortcut.Target.Record ?: return@work
        val success = start.startTimer(
            target.typeId, target.tags, target.comment,
            summary = target.summary, templateId = id,
        )
        message.setValue(if (success) "已开始记录；此前活动按真实时间保存。" else "未开始新记录，请检查当前活动。")
    }

    fun addTrial(description: String, source: Record?) = work {
        if (description.isBlank()) return@work
        val data = requireNotNull(screen.value)
        reviews.saveTrialWithReview(
            data.review,
            ReviewTrial(
                source = source?.let { ReviewRecordReference(it.id, caption(it, data)) }, description = description.trim(),
            ),
        )
    }

    fun editTrial(trial: ReviewTrial, description: String) = work {
        if (description.isBlank()) return@work
        reviews.saveTrial(trial.copy(description = description.trim()))
    }

    fun linkTrial(trial: ReviewTrial, ids: Set<Long>) = work { reviews.saveTrial(trial.copy(relatedRecordIds = ids.toList())) }
    fun removeTrial(trial: ReviewTrial) = work { reviews.removeTrial(trial.id) }
    fun linkTemplate(record: Record, id: Long?) = work {
        if (id != null) check(shortcuts.get(id)?.target is RecordShortcut.Target.Record)
        mutations.save(listOf(record.copy(templateId = id)))
    }

    fun caption(record: Record, data: WeekReviewScreen): String = record.summary.ifBlank {
        data.types.find { it.id == record.typeId }?.name ?: "未分类"
    }

    private fun changeReview(clearWords: Boolean = false, clearField: Int? = null, change: (WeeklyReview) -> WeeklyReview) = work {
        reviews.saveReview(change(requireNotNull(screen.value).review))
        if (clearWords) clearDrafts()
        if (clearField != null) saved.remove<String>("draft:$week:$clearField")
        message.setValue("回顾已保存")
    }

    private fun clearDrafts() {
        listOf(
            com.example.util.simpletimetracker.feature_statistics.R.id.reviewAbundance,
            com.example.util.simpletimetracker.feature_statistics.R.id.reviewKeep,
            com.example.util.simpletimetracker.feature_statistics.R.id.reviewAdjust,
        ).forEach {
            saved.remove<String>("draft:$week:$it")
        }
    }

    private fun work(action: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value == true || screen.value == null) return@launch
        loading?.cancel()
        busy.value = true
        try {
            val identity = settings.getOrCreate()
            check(identity.dataSetId == screen.value?.identity?.dataSetId && gate.canWrite(requireNotNull(screen.value).writeToken))
            action()
            load()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Week review write failed")
            message.setValue("未能保存，请刷新后重试。")
        } finally {
            busy.value = false
        }
    }

    private suspend fun load() {
        try {
            val token = gate.token()
            val identity = settings.getOrCreate()
            if (saved.get<String>("dataSetId") != identity.dataSetId || saved.get<Long>("epoch") != token) {
                saved.keys().filter { it.startsWith("draft:") }.forEach { saved.remove<String>(it) }
                saved["people"] = longArrayOf()
                saved["environments"] = longArrayOf()
                saved["dataSetId"] = identity.dataSetId
                saved["epoch"] = token
            }
            val all = records.getAll()
            val active = running.getAll()
            val review = reviews.getReview(week, zoneId) ?: WeeklyReview(weekStart = week, zoneId = zoneId)
            val now = System.currentTimeMillis()
            val data = WeekReviewScreen(
                review,
                withContext(Dispatchers.Default) {
                    calculator.calculate(
                        week, zoneId, now, all, active,
                        identity.happinessTagId, identity.meaningTagId, people, environments,
                    )
                },
                identity, types.getAll(), tags.getAll(),
                shortcuts.getAll().filter { it.target is RecordShortcut.Target.Record },
                reviews.getTrials().filter { it.reviewId == review.id && review.id != 0L }, all, token, active,
                withContext(Dispatchers.Default) { calculator.comparisonRecords(all, now) },
            )
            if (gate.canWrite(token)) screen.value = data
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Week review read failed")
            message.setValue("无法加载回顾，请稍后刷新。")
        }
    }
}

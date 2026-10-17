package com.example.util.simpletimetracker.feature_statistics.review

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.example.util.simpletimetracker.feature_views.extension.getThemedAttr
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.util.simpletimetracker.domain.color.mapper.AppColorMapper
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTagPurpose
import com.example.util.simpletimetracker.domain.review.interactor.ReviewScores
import com.example.util.simpletimetracker.domain.review.interactor.ReviewTimeState
import com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference
import com.example.util.simpletimetracker.domain.review.model.ReviewTrial
import com.example.util.simpletimetracker.feature_statistics.R
import com.example.util.simpletimetracker.feature_statistics.databinding.WeekReviewFragmentBinding
import com.example.util.simpletimetracker.feature_statistics.view.StatisticsContainerFragment
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import java.util.Calendar

@AndroidEntryPoint
class WeekReviewFragment : BaseFragment<WeekReviewFragmentBinding>() {
    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> WeekReviewFragmentBinding = WeekReviewFragmentBinding::inflate
    override var insetConfiguration: InsetConfiguration = InsetConfiguration.DoNotApply
    private val model: WeekReviewViewModel by viewModels()
    @Inject lateinit var colors: AppColorMapper
    @Inject lateinit var photoViewer: ReviewPhotoViewer
    @Inject lateinit var navigator: ReviewRecordNavigator
    private val dialogs = mutableListOf<AlertDialog>()
    private var sectionContainer: LinearLayout? = null
    private val timeOnly get() = arguments?.getBoolean("timeOnly") == true
    private val compactUi get() = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.3f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = requireNotNull(binding)
        ui.reviewPrevious.setOnClickListener { model.moveWeek(-1) }
        ui.reviewNext.setOnClickListener { model.moveWeek(1) }
        ui.reviewToday.setOnClickListener { model.currentWeek() }
        ui.reviewWeek.setOnClickListener { model.refresh() }
        ui.reviewFilter.setOnClickListener { chooseFilter() }
        ui.reviewStatistics.isVisible = !timeOnly
        ui.reviewDecisions.isVisible = !timeOnly
        ui.reviewDecisions.setOnClickListener {
            ui.reviewContent.findViewById<View>(R.id.reviewAbundance)?.let {
                val bounds = Rect()
                it.getDrawingRect(bounds)
                ui.reviewContent.offsetDescendantRectToMyCoords(it, bounds)
                ui.reviewScroll.smoothScrollTo(0, maxOf(0, bounds.top - dp(24)))
            }
        }
        ui.reviewStatistics.setOnClickListener {
            val show = !ui.reviewOldStatistics.isVisible
            ui.reviewOldStatistics.isVisible = show
            ui.reviewScroll.isVisible = !show
            ui.reviewStatistics.text = if (show) {
                if (compactUi) "回顾" else "返回周回顾"
            } else {
                if (compactUi) "统计" else "详细统计"
            }
            ui.reviewStatistics.contentDescription = if (show) "返回周回顾" else "详细统计"
            if (show && childFragmentManager.findFragmentById(R.id.reviewOldStatistics) == null) {
                childFragmentManager.beginTransaction().replace(R.id.reviewOldStatistics, StatisticsContainerFragment.newInstance()).commit()
            }
            childFragmentManager.findFragmentById(R.id.reviewOldStatistics)?.let {
                childFragmentManager.beginTransaction().setMaxLifecycle(
                    it,
                    if (show) androidx.lifecycle.Lifecycle.State.RESUMED else androidx.lifecycle.Lifecycle.State.STARTED,
                ).commit()
            }
        }
        model.screen.observe(viewLifecycleOwner, ::render)
        model.busy.observe(viewLifecycleOwner) { busy ->
            ui.reviewBusy.isVisible = busy
            fun enabled(view: View) {
                view.isEnabled = !busy
                if (view is ViewGroup) (0 until view.childCount).forEach { enabled(view.getChildAt(it)) }
            }
            enabled(ui.reviewContent)
            ui.reviewPrevious.isEnabled = !busy
            ui.reviewNext.isEnabled = !busy
        }
        model.message.observe(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    override fun onDestroyView() {
        dialogs.toList().forEach(AlertDialog::dismiss)
        dialogs.clear()
        sectionContainer = null
        super.onDestroyView()
    }

    private fun render(data: WeekReviewScreen) {
        val ui = binding
        val weekStart = data.review.weekStart.replace('-', '.')
        val weekEnd = data.stats.days.last().date.takeLast(5).replace('-', '.')
        ui.reviewWeek.text = if (compactUi) "${weekStart.takeLast(5)}–$weekEnd" else "$weekStart — $weekEnd"
        ui.reviewWeek.contentDescription = "$weekStart — $weekEnd"
        val filtered = model.people.isNotEmpty() || model.environments.isNotEmpty()
        ui.reviewFilter.text = if (compactUi) {
            if (filtered) "已筛选" else "筛选"
        } else {
            if (filtered) "人物／环境 · 已筛选" else "人物／环境"
        }
        ui.reviewFilter.contentDescription = if (filtered) "人物／环境 · 已筛选" else "人物／环境"
        ui.reviewDecisions.text = if (compactUi) "调整" else "保留／调整"
        ui.reviewDecisions.contentDescription = "保留／调整"
        if (!ui.reviewOldStatistics.isVisible) ui.reviewStatistics.text = if (compactUi) "统计" else "详细统计"
        ui.reviewStatistics.contentDescription = if (ui.reviewOldStatistics.isVisible) "返回周回顾" else "详细统计"
        val content = ui.reviewContent
        val oldScroll = ui.reviewScroll.scrollY
        content.removeAllViews()
        sectionContainer = null
        if (!timeOnly) {
            val header = layoutInflater.inflate(R.layout.week_review_header, content, false)
            header.findViewById<ImageView>(R.id.reviewJournalCat).apply {
                if (compactUi) {
                    layoutParams.width = dp(64)
                    layoutParams.height = dp(64)
                }
            }
            content.addView(header)
        }
        section("一周的时间", R.attr.lifeClockAccentSurfaceColor)
        line("已记录 ${duration(data.stats.recorded)}").apply {
            textSize = 23f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(4), 0, dp(12))
        }
        line("已确认 ${duration(data.stats.confirmed)} · 待整理 ${duration(data.stats.pending)} · 进行中 ${duration(data.stats.running)}")
        line("已经过 ${duration(data.stats.elapsed)} · ${if (model.people.isEmpty() && model.environments.isEmpty()) "未记录" else "未匹配筛选"} ${duration(data.stats.unrecorded)}").apply {
            setTextColor(context.getThemedAttr(R.attr.appTextHintColor))
        }
        button("时间图例与统计说明") {
            AlertDialog.Builder(requireContext()).setTitle("如何读这周的时间")
                .setMessage("${data.review.zoneId} · 完整 24 小时\n\n实色：已确认；浅色虚线：待整理；实边框：进行中；淡底色：未来。\n\n已确认经历计入评分分布；跨夜经历评分只算一次，空评分不作零分。组合活动的时间只计算一次。")
                .setPositiveButton("知道了", null).create().also(::show)
        }
        val grid = WeekReviewGrid(requireContext()).apply {
            id = R.id.reviewGrid
            stats = data.stats
            zoneId = data.review.zoneId
            colors = data.types.associate { it.id to this@WeekReviewFragment.colors.mapToColorInt(it.color) }
            textColor = context.getThemedAttr(R.attr.appTextPrimaryColor)
            surfaceColor = context.getThemedAttr(R.attr.appCardBackgroundColor)
            accentColor = context.getThemedAttr(R.attr.colorPrimary)
            selectedDay = model.selectedDay
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360))
            onBlock = { block ->
                if (block.state == ReviewTimeState.RUNNING) {
                    navigator.openRunning(block.id)
                } else {
                    data.allRecords.find { it.id == block.id }?.let {
                        if (timeOnly) editRecord(it, data) else showRecord(it, data)
                    }
                }
            }
            onDay = { model.selectDay(it) }
            onGap = { day, minute -> if (timeOnly) openGap(day, minute, data) else model.selectDay(day) }
        }
        if (!timeOnly) {
            val toggle = button(if (model.gridExpanded) "收起一周时间图" else "展开一周时间图") { }
            toggle.setOnClickListener {
                model.gridExpanded = !model.gridExpanded
                grid.isVisible = model.gridExpanded
                toggle.text = if (model.gridExpanded) "收起一周时间图" else "展开一周时间图"
            }
        }
        grid.isVisible = timeOnly || model.gridExpanded
        currentContainer().addView(grid)
        if (data.stats.days.any { it.end - it.start != 86_400_000L }) line("夏令时切换日按本地钟面显示，重复时段分列；时长按实际经过时间计算。")
        section("时间花在哪里")
        line("按已确认时长统计 · ${duration(data.stats.confirmed)}")
        if (data.stats.byType.isEmpty()) emptyWeek()
        data.stats.byType.toList().sortedByDescending { data.stats.confirmedByType[it.first] ?: 0 }.forEach { (id, _) ->
            val type = data.types.find { it.id == id }
            val pending = data.stats.blocks.filter { it.typeId == id && it.state == ReviewTimeState.PENDING }.sumOf { it.end - it.start }
            val running = data.stats.blocks.filter { it.typeId == id && it.state == ReviewTimeState.RUNNING }.sumOf { it.end - it.start }
            val confirmed = data.stats.confirmedByType[id] ?: 0
            val total = data.stats.confirmed
            val color = type?.let { colors.mapToColorInt(it.color) } ?: requireContext().getThemedAttr(R.attr.appTextHintColor)
            val fraction = if (total > 0) confirmed.toFloat() / total else 0f
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(6))
            }
            row.addView(View(requireContext()).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
            row.addView(chartText(type?.name ?: "未分类", 14f), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(chartText(duration(confirmed), 14f).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            currentContainer().addView(row)
            currentContainer().addView(ReviewBarView(requireContext(), fraction, color, requireContext().getThemedAttr(R.attr.lifeClockSoftSurfaceColor)),
                LinearLayout.LayoutParams(-1, dp(7)).apply { bottomMargin = dp(4) })
            line("已确认 ${(fraction * 100).toInt()}%" +
                (if (pending > 0) " · 待整理 ${duration(pending)}" else "") +
                (if (running > 0) " · 进行中 ${duration(running)}" else "")).apply {
                textSize = 12f
                setTextColor(context.getThemedAttr(R.attr.appTextHintColor))
            }
        }
        button("查看本周经历（${data.stats.records.size}）", R.id.reviewRecords) { chooseRecord(data.stats.records, data) { showRecord(it, data) } }
        if (data.stats.repairs.isNotEmpty()) {
            button("待修复区间（${data.stats.repairs.size}），未计入统计") {
                chooseRecord(data.stats.repairs, data) { showRecord(it, data) }
            }
        }
        if (data.stats.future.isNotEmpty()) line("未来区间 ${data.stats.future.size} 条，未计入实际时间。")
        if (timeOnly) {
            section("${data.stats.days[model.selectedDay].date} 的经历")
            button("选择日期") {
                show(
                    AlertDialog.Builder(requireContext()).setTitle("选择这周的一天")
                        .setItems(data.stats.days.map { it.date }.toTypedArray()) { _, index -> model.selectDay(index) }
                        .setNegativeButton("关闭", null).create(),
                )
            }
            val day = data.stats.days[model.selectedDay]
            val selected = data.stats.records.filter { it.timeStarted < day.end && it.timeEnded > day.start }
            if (selected.isEmpty()) line("这一天还没有匹配的经历。可点过去的空档补记。")
            selected.forEach { record -> button(recordLabel(record, data)) { editRecord(record, data) } }
            data.stats.blocks.filter { it.day == model.selectedDay && it.state == ReviewTimeState.RUNNING }.distinctBy { it.id }.forEach { block ->
                val active = data.runningRecords.find { it.id == block.id }
                button("进行中 · ${active?.summary?.ifBlank { data.types.find { type -> type.id == block.id }?.name.orEmpty() }}") { navigator.openRunning(block.id) }
            }
            ui.reviewScroll.post { if (view != null && binding === ui) ui.reviewScroll.scrollTo(0, oldScroll) }
            return
        }
        section("体验分布")
        line("仅统计本周已确认经历；跨夜经历只评分一次，未评分保持为空。")
        showScores("快乐", data.stats.happiness)
        showScores("意义", data.stats.meaning)
        showRecoveryAndPlans(data)
        section("挑选值得记住的经历")
        showMemories("愉快", data.review.pleasantRecords)
        button("选择愉快经历", R.id.reviewPleasant) { selectMemories(data, true) }
        showMemories("不愉快", data.review.unpleasantRecords)
        button("选择不愉快经历") { selectMemories(data, false) }
        section("下周想保留或调整什么？", R.attr.lifeClockApricotSurfaceColor)
        line("填写任意一项即可，也可以全部跳过。评分只提供参考，由你决定。")
        val abundance = input("这周有没有时间做自己想做的事？（可选）", data.review.timeAbundance.orEmpty(), R.id.reviewAbundance)
        val keep = input("本周想保留的一件事（可选）", data.review.keep?.text.orEmpty(), R.id.reviewKeep)
        val adjust = input("下周想减少或换种做法的一件事（可选）", data.review.adjust?.text.orEmpty(), R.id.reviewAdjust)
        button("保存回顾", R.id.reviewSave) { model.saveWords(abundance.text.toString(), keep.text.toString(), adjust.text.toString()) }
        data.review.attentionRecordIds.forEach { id ->
            val record = data.allRecords.find { it.id == id }
            if (record != null) {
                button("关注 · ${model.caption(record, data)}") { showRecord(record, data) }
            } else {
                line("关注的经历已删除（$id）")
            }
        }
        section("最近希望留出时间的事")
        line("最多三项活动模板；这里显示实际发生的次数与时长。置顶与尝试不增加时间账本。")
        data.review.pinnedTemplateIds.forEach { id ->
            val template = data.templates.find { it.id == id }
            val target = template?.target as? RecordShortcut.Target.Record
            if (target == null) {
                line("已删除的模板（$id），可通过选择重要活动取消置顶")
            } else {
                val actual = data.stats.records.filter { it.templateId == id }
                val spent = data.stats.blocks.filter { it.state != ReviewTimeState.RUNNING && it.id in actual.map(Record::id) }.sumOf { it.end - it.start }
                val confirmed = actual.filter { it.reviewState == RecordReviewState.CONFIRMED }.map { it.id }.toSet()
                val confirmedTime = data.stats.blocks.filter { it.state == ReviewTimeState.CONFIRMED && it.id in confirmed }.sumOf { it.end - it.start }
                val activeTime = data.stats.blocks.filter { block ->
                    block.state == ReviewTimeState.RUNNING &&
                        data.runningRecords.any { it.id == block.id && it.templateId == id }
                }.sumOf { it.end - it.start }
                line("${templateName(template, data)} · 已确认 ${confirmed.size} 次 ${duration(confirmedTime)}\n待整理 ${actual.size - confirmed.size} 次 ${duration(spent - confirmedTime)} · 进行中 ${duration(activeTime)}")
                button("查看经历 · ${templateName(template, data)}") { chooseRecord(actual, data) { showRecord(it, data) } }
                button("开始 · ${templateName(template, data)}") { model.startTemplate(id) }
            }
        }
        button("选择重要活动（最多三项）", R.id.reviewPins) { selectPins(data) }
        button("从本周经历保存活动模板") {
            chooseRecord(data.stats.records.filter { it.typeId != 0L && data.types.any { type -> type.id == it.typeId && !type.hidden } }, data) { record ->
                editText("活动模板名称", model.caption(record, data)) { model.createTemplate(record, it) }
            }
        }
        section("换一种方式试一次")
        line("例如：下周试一次早晨阅读。实际发生后，关联经历再比较；评分空缺不作零分。")
        data.trials.forEach { trial ->
            line(trial.description)
            trial.source?.let { line("原经历：${it.caption.ifBlank { "经历 ${it.recordId}" }}") }
            val ids = trial.relatedRecordIds.toSet()
            val linked = data.comparisonRecords.filter { it.id in ids }
            val baseline = trial.source?.let { source -> data.comparisonRecords.find { it.id == source.recordId } }
            baseline?.let { line("原体验：${scores(it, data)} · ${duration(it.duration)}\n${context(it, data)}") }
            linked.forEach { line("尝试：${model.caption(it, data)} · ${scores(it, data)} · ${duration(it.duration)}\n${context(it, data)}") }
            if (linked.isEmpty()) line("尚未关联实际经历")
            if (linked.size < ids.size) line("部分关联经历已删除或尚待修复；描述保留。")
            button("管理尝试 · ${trial.description}") { trialActions(trial, data) }
        }
        button("新增轻量尝试", R.id.reviewTrial) { editText("想怎样试一次？", "") { model.addTrial(it, null) } }
        ui.reviewScroll.post { if (view != null && binding === ui) ui.reviewScroll.scrollTo(0, oldScroll) }
    }

    private fun showScores(name: String, scores: ReviewScores) {
        line("$name").apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textSize = 16f }
        line("${scores.rated} 条已评分 · ${scores.unrated} 条未评分").apply { textSize = 12f; setTextColor(context.getThemedAttr(R.attr.appTextHintColor)) }
        val maximum = scores.distribution.maxOrNull()?.coerceAtLeast(1) ?: 1
        scores.distribution.forEachIndexed { index, count ->
            val value = index + scores.minimum
            val label = if (value > 0 && scores.minimum < 0) "+$value" else value.toString().replace('-', '−')
            val color = when {
                value < 0 -> if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xffd8a28b.toInt() else 0xffa66c52.toInt()
                value == 0 -> requireContext().getThemedAttr(R.attr.appTextHintColor)
                else -> requireContext().getThemedAttr(R.attr.colorPrimary)
            }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(30)
                setPadding(0, dp(4), 0, dp(4))
                contentDescription = "$name $value 分，$count 条经历"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            row.addView(chartText("$label 分", 12f), LinearLayout.LayoutParams(dp(44), -2))
            row.addView(ReviewBarView(requireContext(), count.toFloat() / maximum, color, requireContext().getThemedAttr(R.attr.lifeClockSoftSurfaceColor)), LinearLayout.LayoutParams(0, dp(7), 1f))
            row.addView(chartText("$count 条", 12f).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(48), -2))
            currentContainer().addView(row)
        }
        if (scores.rated == 0) line("还没有已填写的$name 评分，空值没有计入分布。").apply { textSize = 12f }
    }

    private fun chartText(value: String, size: Float) = TextView(requireContext()).apply {
        text = value
        textSize = size
        setTextColor(context.getThemedAttr(R.attr.appTextPrimaryColor))
    }

    private fun showMemories(name: String, references: List<ReviewRecordReference>) {
        if (references.isEmpty()) line("${name}经历：还没有选择，也可以跳过。")
        references.forEach { reference ->
            val data = model.screen.value ?: return@forEach
            val record = data.allRecords.find { it.id == reference.recordId }
            val caption = reference.caption.ifBlank { record?.let { model.caption(it, data) } ?: "经历 ${reference.recordId}" }
            if (record == null) {
                line("$caption（原记录已删除，回顾文字保留）")
            } else {
                button(caption) { showRecord(record, data) }
            }
        }
    }

    private fun selectMemories(data: WeekReviewScreen, pleasant: Boolean) {
        val previous = if (pleasant) data.review.pleasantRecords else data.review.unpleasantRecords
        val candidates = data.stats.records.sortedBy { record ->
            val score = model.calculator.rating(record, data.identity.happinessTagId)
            if (pleasant) -(score ?: 0) else score ?: 6
        }
        val ids = (candidates.map { it.id } + previous.map { it.recordId }).distinct()
        val labels = ids.map { id ->
            candidates.find { it.id == id }?.let { recordLabel(it, data) }
                ?: previous.find { it.recordId == id }?.caption.orEmpty().ifBlank { "已删除经历 $id" }
        }
        selectMany(
            if (pleasant) "自选愉快经历（不限数量，可全不选）" else "自选不愉快经历（可跳过）",
            ids, labels, previous.map { it.recordId }.toSet(),
        ) { model.chooseExperiences(it, pleasant) }
    }

    private fun selectPins(data: WeekReviewScreen) {
        val ids = (data.templates.map { it.id } + data.review.pinnedTemplateIds).distinct()
        selectMany(
            "选择重要活动（最多三项）", ids,
            ids.map { id ->
                data.templates.find { it.id == id }?.let { templateName(it, data) } ?: "已删除模板 $id"
            },
            data.review.pinnedTemplateIds.toSet(), 3,
        ) { model.pinTemplates(it) }
    }

    private fun chooseFilter() {
        val data = model.screen.value ?: return
        val tags = data.tags.filter { !it.archived && it.purpose != RecordTagPurpose.GENERAL }
        selectMany(
            "人物／环境筛选：组内任一，组间同时满足", tags.map { it.id },
            tags.map {
                "${if (it.purpose == RecordTagPurpose.PERSON) "人物" else "环境"} · ${it.name}"
            },
            model.people + model.environments,
        ) { chosen ->
            model.filter(
                tags.filter { it.id in chosen && it.purpose == RecordTagPurpose.PERSON }.map { it.id }.toSet(),
                tags.filter { it.id in chosen && it.purpose == RecordTagPurpose.ENVIRONMENT }.map { it.id }.toSet(),
            )
        }
    }

    private fun selectMany(title: String, ids: List<Long>, labels: List<String>, initial: Set<Long>, maximum: Int = Int.MAX_VALUE, save: (Set<Long>) -> Unit) {
        val selected = initial.toMutableSet()
        val dialog = AlertDialog.Builder(requireContext()).setTitle(title)
            .setMultiChoiceItems(labels.toTypedArray(), ids.map { it in selected }.toBooleanArray()) { _, index, checked ->
                if (checked) selected.add(ids[index]) else selected.remove(ids[index])
            }.setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selected.size > maximum) {
                    Toast.makeText(context, "最多选择 $maximum 项", Toast.LENGTH_SHORT).show()
                } else {
                    save(selected)
                    dialog.dismiss()
                }
            }
        }
        show(dialog)
    }

    private fun chooseRecord(records: List<Record>, data: WeekReviewScreen, selected: (Record) -> Unit) {
        if (records.isEmpty()) {
            Toast.makeText(context, "这一周还没有匹配的经历。", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext()).setTitle("选择经历")
            .setItems(records.map { recordLabel(it, data) }.toTypedArray()) { _, index -> selected(records[index]) }
            .setNegativeButton("关闭", null).create().also(::show)
    }

    private fun showRecord(record: Record, data: WeekReviewScreen) {
        if (timeOnly) {
            editRecord(record, data)
            return
        }
        val tags = record.tags.mapNotNull { tag -> data.tags.find { it.id == tag.tagId } }
            .filter { it.purpose != RecordTagPurpose.GENERAL }.joinToString("、") { it.name }
        val choices = arrayOf("本周想保留", "下周想调整", if (record.id in data.review.attentionRecordIds) "取消关注" else "标记关注", "下次可以怎样做", "查看照片", "编辑评分／照片／时间", "关联活动模板")
        AlertDialog.Builder(requireContext()).setTitle(model.caption(record, data))
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> model.chooseRecord(record, "keep")
                    1 -> model.chooseRecord(record, "adjust")
                    2 -> model.chooseRecord(record, "attention")
                    3 -> editText("下次可以怎样做？", "") { model.addTrial(it, record) }
                    4 -> photoViewer.show(this, record.id)
                    5 -> editRecord(record, data)
                    6 -> show(
                        AlertDialog.Builder(requireContext()).setTitle("关联模板，不改变实际时长")
                            .setItems((listOf("解除关联") + data.templates.map { templateName(it, data) }).toTypedArray()) { _, position ->
                                model.linkTemplate(record, if (position == 0) null else data.templates[position - 1].id)
                            }.setNegativeButton("取消", null).create(),
                    )
                }
            }.setNeutralButton("经历内容") { _, _ ->
                AlertDialog.Builder(requireContext()).setTitle(model.caption(record, data))
                    .setMessage("${recordLabel(record, data)}\n${record.comment}\n$tags\n${record.zoneId.orEmpty()}")
                    .setPositiveButton("关闭", null).create().also(::show)
            }.setNegativeButton("关闭", null).create().also(::show)
    }

    private fun trialActions(trial: ReviewTrial, data: WeekReviewScreen) {
        AlertDialog.Builder(requireContext()).setTitle(trial.description).setItems(arrayOf("关联实际经历", "修改尝试", "删除尝试")) { _, index ->
            when (index) {
                0 -> {
                    val candidates = data.comparisonRecords.filter { it.id != trial.source?.recordId }.sortedByDescending { it.timeStarted }
                    val ids = (candidates.map { it.id } + trial.relatedRecordIds).distinct()
                    selectMany("选择实际经历（可跨周）", ids, ids.map { id -> candidates.find { it.id == id }?.let { recordLabel(it, data) } ?: "已删除经历 $id" }, trial.relatedRecordIds.toSet()) {
                        model.linkTrial(trial, it)
                    }
                }
                1 -> editText("修改尝试", trial.description) { model.editTrial(trial, it) }
                2 -> AlertDialog.Builder(requireContext()).setMessage("删除这个尝试？生活记录会保留。")
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> model.removeTrial(trial) }.create().also(::show)
            }
        }.setNegativeButton("关闭", null).create().also(::show)
    }

    private fun templateName(shortcut: RecordShortcut, data: WeekReviewScreen): String {
        val target = shortcut.target as RecordShortcut.Target.Record
        return target.name.ifBlank { target.summary.ifBlank { data.types.find { it.id == target.typeId }?.name ?: "活动模板 ${shortcut.id}" } }
    }

    private fun recordLabel(record: Record, data: WeekReviewScreen): String {
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(data.review.zoneId) }
        return "${model.caption(record, data)} · ${format.format(Date(record.timeStarted))}\n${duration(record.duration)} · ${if (record.reviewState == RecordReviewState.CONFIRMED) "已确认" else "待整理"} · ${scores(record, data)}"
    }

    private fun scores(record: Record, data: WeekReviewScreen): String {
        val happy = model.calculator.rating(record, data.identity.happinessTagId)
        val meaning = model.calculator.rating(record, data.identity.meaningTagId, -5)
        val total = if (happy == null || meaning == null) "未计算" else (happy + meaning).let { if (it > 0) "+$it" else "$it" }
        val recovery = when (record.recovery) {
            com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_ENERGY -> "更有精神"
            com.example.util.simpletimetracker.domain.rhythm.Recovery.UNCHANGED -> "差不多"
            com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_TIRED -> "更累"
            null -> "未填写"
        }
        return "快乐 ${happy ?: "未评分"} / 意义 ${meaning ?: "未评分"} / 合计 $total / 恢复感 $recovery"
    }

    private fun showRecoveryAndPlans(data: WeekReviewScreen) {
        section("活动之后的恢复感")
        fun counts(records: List<Record>): String {
            val values = records.filter { it.reviewState == RecordReviewState.CONFIRMED }
            return "更有精神 ${values.count { it.recovery == com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_ENERGY }} · 差不多 ${values.count { it.recovery == com.example.util.simpletimetracker.domain.rhythm.Recovery.UNCHANGED }} · 更累 ${values.count { it.recovery == com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_TIRED }} · 未填写 ${values.count { it.recovery == null }}"
        }
        line(counts(data.stats.records))
        button("按模板查看恢复感") {
            val templates = data.templates.filter { it.target is RecordShortcut.Target.Record }
            show(AlertDialog.Builder(requireContext()).setTitle("选择模板")
                .setItems(templates.map { (it.target as RecordShortcut.Target.Record).name.ifBlank { "模板 ${it.id}" } }.toTypedArray()) { _, index ->
                    show(AlertDialog.Builder(requireContext()).setTitle("本周恢复感").setMessage(counts(data.stats.records.filter { it.templateId == templates[index].id })).setPositiveButton("关闭", null).create())
                }.setNegativeButton("取消", null).create())
        }
        section("计划与实际经历")
        line("计划与实际分开呈现，不将空白当作休息。这里不随人物／环境筛选变化。")
        if (data.plans.isEmpty()) line("这周还没有计划时段。可以到「规划」给重要的事留位置。")
        data.plans.forEach { plan ->
            val links = data.planLinks.filter { it.occurrenceId == plan.id && it.recordId != null }
            val actual = com.example.util.simpletimetracker.domain.planning.PlanCalendar.occupiedMillis(links.map { it.timeStarted to requireNotNull(it.timeEnded) }, Long.MIN_VALUE, Long.MAX_VALUE)
            line("${plan.date} · ${plan.schedule.title} · ${if (plan.schedule.kind == com.example.util.simpletimetracker.domain.planning.PlanKind.BUFFER) "缓冲" else "计划"}\n预留 ${duration(plan.end - plan.start)} · 关联实际 ${duration(actual)}")
            links.mapNotNull { link -> data.allRecords.find { it.id == link.recordId } }.distinctBy { it.id }.forEach { record ->
                button(recordLabel(record, data)) { showRecord(record, data) }
            }
        }
    }

    private fun editText(title: String, value: String, save: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            setText(value)
            hint = "可用一句话描述"
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(title).setView(input)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.isBlank()) {
                    input.error = "请填写内容，或取消跳过。"
                } else {
                    save(input.text.toString())
                    dialog.dismiss()
                }
            }
        }
        show(dialog)
    }

    private fun context(record: Record, data: WeekReviewScreen): String =
        (record.tags.mapNotNull { value -> data.tags.find { it.id == value.tagId && it.purpose != RecordTagPurpose.GENERAL }?.name } + record.comment)
            .filter(String::isNotBlank).joinToString(" · ")

    private fun editRecord(record: Record, data: WeekReviewScreen) = viewLifecycleOwner.lifecycleScope.launch { navigator.open(record, data) }

    private fun openGap(dayIndex: Int, minute: Int, data: WeekReviewScreen) {
        val day = data.stats.days[dayIndex]
        val timestamp = Calendar.getInstance(TimeZone.getTimeZone(data.review.zoneId)).apply {
            timeInMillis = day.start
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }.timeInMillis
        val now = System.currentTimeMillis()
        if (timestamp >= now) {
            Toast.makeText(context, "未来时间不能补记为实际经历。", Toast.LENGTH_SHORT).show()
            return
        }
        val intervals = data.allRecords.filter { it.timeEnded > it.timeStarted }.map { it.timeStarted to it.timeEnded } +
            data.runningRecords.map { it.timeStarted to now }
        if (intervals.any { timestamp >= it.first && timestamp < it.second }) {
            Toast.makeText(context, "这里已有记录，请清除筛选或查看待修复区间。", Toast.LENGTH_SHORT).show()
            return
        }
        val start = maxOf(day.start, intervals.filter { it.second <= timestamp }.maxOfOrNull { it.second } ?: day.start)
        val end = minOf(day.end, now, intervals.filter { it.first > timestamp }.minOfOrNull { it.first } ?: day.end)
        if (start < end) viewLifecycleOwner.lifecycleScope.launch { navigator.open(null, data, start, end) }
    }

    private fun section(text: String, surface: Int = R.attr.appCardBackgroundColor) {
        sectionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = roundedSurface(surface, 20)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            binding.reviewContent.addView(this)
        }
        line(text).apply {
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun currentContainer() = sectionContainer ?: binding.reviewContent

    private fun roundedSurface(surface: Int, radius: Int) = GradientDrawable().apply {
        setColor(requireContext().getThemedAttr(surface))
        cornerRadius = dp(radius).toFloat()
    }

    private fun emptyWeek() {
        currentContainer().addView(
            ImageView(requireContext()).apply {
                setImageResource(R.drawable.life_clock_cat_sleep)
                context.obtainStyledAttributes(intArrayOf(R.attr.lifeClockIllustrationAlpha)).let {
                    alpha = it.getFloat(0, 1f)
                    it.recycle()
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(108), dp(80)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(4)
                }
            },
        )
        line("这一周还没有匹配的经历，\n从一件小事开始记录就好。").apply {
            gravity = Gravity.CENTER
            setTextColor(context.getThemedAttr(R.attr.appTextHintColor))
        }
    }

    private fun line(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = 14f
        setTextColor(context.getThemedAttr(R.attr.appTextPrimaryColor))
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(4), 0, dp(8))
        currentContainer().addView(this)
    }
    private fun button(text: String, id: Int = View.NO_ID, action: () -> Unit) = MaterialButton(
        ContextThemeWrapper(
            requireContext(),
            if (id == R.id.reviewSave) R.style.LifeClockUi2ReviewPrimaryButtonTheme else R.style.LifeClockUi2ReviewButtonTheme,
        ),
    ).apply {
        this.id = id
        this.text = text
        isAllCaps = false
        isEnabled = model.busy.value != true
        if (id != R.id.reviewSave) gravity = Gravity.START or Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        }
        setOnClickListener { action() }
        currentContainer().addView(this)
    }
    private fun input(hint: String, text: String, id: Int) = EditText(requireContext()).apply {
        line(hint).labelFor = id
        this.id = id
        setText(model.draft(id, text))
        textSize = 16f
        setTextColor(context.getThemedAttr(R.attr.appTextPrimaryColor))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        minimumHeight = dp(56)
        background = GradientDrawable().apply {
            setColor(context.getThemedAttr(R.attr.appCardBackgroundColor))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), context.getThemedAttr(R.attr.appDividerColor))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        doAfterTextChanged { model.setDraft(id, it.toString()) }
        currentContainer().addView(this)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun duration(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        return listOfNotNull(
            if (hours > 0) "${hours}小时" else null,
            if (minutes > 0 || ms < 1000) "${minutes}分" else null,
            if (seconds > 0) "${seconds}秒" else null,
        ).joinToString(" ")
    }

    private fun show(dialog: AlertDialog) {
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
    }
}

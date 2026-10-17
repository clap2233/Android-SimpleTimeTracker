package com.example.util.simpletimetracker.feature_statistics.planning

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.feature_statistics.R
import com.example.util.simpletimetracker.feature_statistics.databinding.PlanningFragmentBinding
import com.example.util.simpletimetracker.feature_views.extension.getThemedAttr
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class PlanningFragment : BaseFragment<PlanningFragmentBinding>() {
    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> PlanningFragmentBinding = PlanningFragmentBinding::inflate
    override var insetConfiguration: InsetConfiguration = InsetConfiguration.DoNotApply
    private val model: PlanningViewModel by viewModels()
    private val dialogs = mutableListOf<AlertDialog>()
    private var pendingDraft: Bundle? = null
    private var captureDraft: (() -> Bundle)? = null
    private var exportText = ""
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            val content = exportText
            try {
                withContext(Dispatchers.IO) { requireNotNull(requireContext().contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) } }
                message("已导出计划，未包含实际经历")
            } catch (e: Exception) { message("导出失败：${e.message}") }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        exportText = savedInstanceState?.getString("planExport").orEmpty()
        pendingDraft = savedInstanceState?.getBundle("planDraft")
        model.screen.observe(viewLifecycleOwner) {
            render(it)
            restoreDraft(it)
        }
        model.busy.observe(viewLifecycleOwner) { busy -> binding?.planContent?.let { updateActions(it, !busy) } }
        model.error.observe(viewLifecycleOwner) { it?.let { text -> message(text); model.error.value = null } }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("planExport", exportText)
        outState.putBundle("planDraft", captureDraft?.invoke() ?: pendingDraft)
    }
    override fun onDestroyView() { dialogs.toList().forEach { it.dismiss() }; dialogs.clear(); captureDraft = null; super.onDestroyView() }

    private fun range(): Pair<Long, Long> {
        val start = PlanCalendar.date(model.date, TimeZone.getDefault().id)
        val end = (start.clone() as Calendar).apply { add(Calendar.DATE, if (model.week) 7 else 1) }
        return start.timeInMillis to end.timeInMillis
    }
    private fun visible(data: PlanningScreen): List<PlanOccurrence> {
        val (from, until) = range()
        return PlanCalendar.occurrences(data.schedules, data.exceptions, from, until)
    }
    private fun matches(task: PlanTask?): Boolean = when (model.quadrant) {
        1 -> task?.important == true && task.urgent == true
        2 -> task?.important == true && task.urgent != true
        3 -> task?.important != true && task?.urgent == true
        4 -> task?.important != true && task?.urgent != true
        else -> true
    }
    private fun render(data: PlanningScreen) {
        val root = requireNotNull(binding).planContent
        root.removeAllViews()
        text(root, "给重要的事留位置", 23f)
        text(root, "计划是准备，记录是经历。留白不等于已经休息。", 14f)
        val controls = LinearLayout(requireContext()).also { root.addView(it) }
        listOf("上一段", if (model.week) "周历" else "日历", "下一段").forEachIndexed { i, title ->
            button(controls, title, true) {
                if (i == 1) model.week = !model.week else {
                    val date = PlanCalendar.date(model.date, TimeZone.getDefault().id)
                    date.add(Calendar.DATE, (if (model.week) 7 else 1) * if (i == 0) -1 else 1)
                    model.date = PlanCalendar.dateText(date)
                }
                render(data)
            }
        }
        button(root, "${model.date} · 选择日期") { datePicker(model.date) { model.date = it; render(data) } }
        button(root, "新增待办") { taskForm(null, data) }
        button(root, "安排时段／缓冲") { chooseArrangement(data) }
        button(root, "筛选：${filters[model.quadrant]}") {
            dialog(AlertDialog.Builder(requireContext()).setTitle("重要／紧急是可选属性")
                .setItems(filters) { _, which -> model.quadrant = which; render(data) }.setNegativeButton("取消", null))
        }
        val all = visible(data)
        val tasks = data.tasks.associateBy { it.id }
        val (from, until) = range()
        val active = all.filter { it.status != PlanStatus.CANCELLED && it.status != PlanStatus.SKIPPED }
        val occupied = PlanCalendar.occupiedMillis(active.filter { it.schedule.kind != PlanKind.BUFFER }.map { it.start to it.end }, from, until)
        val buffer = PlanCalendar.occupiedMillis(active.filter { it.schedule.kind == PlanKind.BUFFER }.map { it.start to it.end }, from, until)
        val union = PlanCalendar.occupiedMillis(active.map { it.start to it.end }, from, until)
        text(root, "计划占用 ${duration(occupied)} · 缓冲 ${duration(buffer)}\n未安排 ${duration(until - from - union)}", 15f)
        text(root, "计划占用按区间去重；重叠的缓冲单列，不相加为实际时间。", 13f)
        val day = PlanCalendar.date(model.date, TimeZone.getDefault().id)
        repeat(if (model.week) 7 else 1) {
            val a = day.timeInMillis
            day.add(Calendar.DATE, 1)
            val b = day.timeInMillis
            val card = card(root)
            text(card, SimpleDateFormat("MM 月 dd 日 EEEE", Locale.CHINA).format(Date(a)), 18f)
            val entries = all.filter { it.start < b && it.end > a && matches(tasks[it.schedule.taskId]) }
            if (entries.isEmpty()) text(card, "00:00—24:00 · 没有符合筛选的安排", 14f)
            entries.forEach { item ->
                val important = tasks[item.schedule.taskId]?.important == true
                val kind = when (item.schedule.kind) { PlanKind.BUFFER -> "缓冲"; PlanKind.FREE -> "自由活动"; else -> if (important) "重要事项" else "计划" }
                button(card, "${time(item.start)}—${time(item.end)}  $kind\n${item.schedule.title} · ${status(item.status)}") { occurrenceMenu(item, data) }
                val links = data.links.filter { it.occurrenceId == item.id && it.recordId != null }
                if (links.isNotEmpty()) text(card, "已关联实际 ${duration(PlanCalendar.occupiedMillis(links.map { it.timeStarted to requireNotNull(it.timeEnded) }, Long.MIN_VALUE, Long.MAX_VALUE))} · ${links.mapNotNull { it.recordId }.distinct().size} 条经历", 13f)
            }
        }
        text(root, "待办清单", 20f)
        data.tasks.filter { !it.archived && matches(it) }.sortedBy { it.status != PlanStatus.OPEN }.forEach { task ->
            val arranged = data.schedules.any { it.taskId == task.id }
            button(root, "${if (task.important == true) "★ " else ""}${task.title}\n${status(task.status)} · ${if (arranged) "已有时段" else "尚未安排"}${task.deadline?.let { " · 截止 $it" }.orEmpty()}") {
                dialog(AlertDialog.Builder(requireContext()).setTitle(task.title).setItems(arrayOf("安排时段", "编辑待办", "标记完成", "重新打开", "归档待办")) { _, which ->
                    when (which) { 0 -> scheduleForm(task, null, data); 1 -> taskForm(task, data)
                        2 -> model.save(task.copy(status = PlanStatus.DONE)); 3 -> model.save(task.copy(status = PlanStatus.OPEN)); 4 -> model.save(task.copy(archived = true)) }
                }.setNegativeButton("取消", null))
            }
        }
        if (data.tasks.none { !it.archived }) text(root, "只写一个标题就能收进清单，不必立刻安排时间。", 14f)
        button(root, "可选娱乐模板") { presets(data) }
        button(root, "单独导出本页计划 CSV／ICS") {
            dialog(AlertDialog.Builder(requireContext()).setTitle("只导出计划时段").setItems(arrayOf("CSV", "ICS 日历")) { _, choice ->
                exportText = if (choice == 0) PlanningExport.csv(all) else PlanningExport.ics(all)
                export.launch("life-clock-plans-${model.date}.${if (choice == 0) "csv" else "ics"}")
            }.setNegativeButton("取消", null))
        }
    }

    private fun occurrenceMenu(item: PlanOccurrence, data: PlanningScreen) {
        val choices = arrayOf("开始活动", "标记完成", "标记跳过", "调整时段", "取消安排", "重新打开")
        dialog(AlertDialog.Builder(requireContext()).setTitle(item.schedule.title).setMessage(null)
            .setItems(choices) { _, which -> when (which) {
                0 -> if (item.schedule.kind == PlanKind.BUFFER) message("缓冲是预留的余量，不生成活动记录") else if (item.status != PlanStatus.OPEN) message("请先重新打开这个计划") else start(item, data)
                1 -> model.status(item, PlanStatus.DONE)
                2 -> model.status(item, PlanStatus.SKIPPED)
                3 -> scheduleForm(data.tasks.firstOrNull { it.id == item.schedule.taskId }, item, data)
                4 -> scope(item) { model.edit(item, null, it) }
                5 -> model.status(item, PlanStatus.OPEN)
            } }.setNegativeButton("返回", null))
    }
    private fun start(item: PlanOccurrence, data: PlanningScreen) {
        val templateId = data.tasks.firstOrNull { it.id == item.schedule.taskId }?.templateId
        val target = data.templates.firstOrNull { it.id == templateId }?.target as? RecordShortcut.Target.Record
        if (target != null) model.start(item, target.typeId, templateId)
        else chooseType(data) { model.start(item, it, null) }
    }
    private fun chooseType(data: PlanningScreen, selected: (Long) -> Unit) {
        val types = data.types.filter { !it.hidden }
        dialog(AlertDialog.Builder(requireContext()).setTitle("选择要开始的活动").setItems(types.map { it.name }.toTypedArray()) { _, i -> selected(types[i].id) }.setNegativeButton("取消", null))
    }
    private fun chooseArrangement(data: PlanningScreen) {
        val tasks = data.tasks.filter { !it.archived && it.status == PlanStatus.OPEN }
        dialog(AlertDialog.Builder(requireContext()).setTitle("安排什么")
            .setItems((listOf("独立时段／自由活动／缓冲") + tasks.map { it.title }).toTypedArray()) { _, i -> scheduleForm(tasks.getOrNull(i - 1), null, data) }.setNegativeButton("取消", null))
    }
    private fun restoreDraft(data: PlanningScreen) {
        val draft = pendingDraft ?: return
        pendingDraft = null
        if (draft.getLong("writeToken") != data.writeToken) {
            message("数据已恢复，原规划草稿未应用，请重新打开编辑")
            return
        }
        val taskId = draft.getString("taskId")
        val task = data.tasks.firstOrNull { it.id == taskId }
        if (taskId != null && task == null) { message("原待办已不存在，草稿未应用"); return }
        if (draft.getString("form") == "task") {
            taskForm(task, data, draft)
        } else {
            val occurrenceId = draft.getString("occurrenceId")
            val start = draft.getLong("occurrenceStart")
            val item = if (occurrenceId == null) null else PlanCalendar.occurrences(data.schedules, data.exceptions,
                start - 86_400_000L, start + 2 * 86_400_000L).firstOrNull { it.id == occurrenceId }
            if (occurrenceId != null && item == null) { message("原时段已不存在，草稿未应用"); return }
            scheduleForm(task, item, data, draft.getBoolean("following"), draft)
        }
    }

    private fun taskForm(original: PlanTask?, data: PlanningScreen, draft: Bundle? = null) {
        val form = column()
        val title = input(form, "标题（必填）", draft?.getString("title") ?: original?.title.orEmpty())
        val note = input(form, "备注（可选）", draft?.getString("note") ?: original?.note.orEmpty())
        val important = spinner(form, "重要程度", arrayOf("未设置", "重要", "不重要"), draft?.getInt("important") ?: flag(original?.important))
        val urgent = spinner(form, "紧急程度", arrayOf("未设置", "紧急", "不紧急"), draft?.getInt("urgent") ?: flag(original?.urgent))
        val templates = data.templates.filter { it.target is RecordShortcut.Target.Record }
        val selectedTemplate = if (draft == null) original?.templateId else draft.getLong("templateId")
        val template = spinner(form, "活动模板", (listOf("不绑定模板") + templates.map { (it.target as RecordShortcut.Target.Record).name.ifBlank { data.types.firstOrNull { t -> t.id == (it.target as RecordShortcut.Target.Record).typeId }?.name.orEmpty() } }).toTypedArray(), templates.indexOfFirst { it.id == selectedTemplate } + 1)
        var deadline = if (draft == null) original?.deadline else draft.getString("deadline")
        lateinit var date: MaterialButton
        date = button(form, "截止日期：${deadline ?: "未设置"}") { datePicker(deadline ?: model.date) { deadline = it; date.text = "截止日期：$it" } }
        button(form, "清除截止日期") { deadline = null; date.text = "截止日期：未设置" }
        formDialog(if (original == null) "新增待办" else "编辑待办", form, snapshot = {
            Bundle().apply {
                putString("form", "task"); putLong("writeToken", data.writeToken); putString("taskId", original?.id)
                putString("title", title.text.toString()); putString("note", note.text.toString())
                putInt("important", important.selectedItemPosition); putInt("urgent", urgent.selectedItemPosition)
                putLong("templateId", templates.getOrNull(template.selectedItemPosition - 1)?.id ?: 0)
                putString("deadline", deadline)
            }
        }) { close ->
            require(title.text.isNotBlank()) { "请填写标题" }
            model.save((original ?: PlanTask(title = title.text.toString().trim())).copy(title = title.text.toString().trim(), note = note.text.toString(),
                important = unflag(important.selectedItemPosition), urgent = unflag(urgent.selectedItemPosition), deadline = deadline,
                templateId = templates.getOrNull(template.selectedItemPosition - 1)?.id))
            close()
        }
    }

    private fun scheduleForm(task: PlanTask?, item: PlanOccurrence?, data: PlanningScreen, following: Boolean? = null, draft: Bundle? = null) {
        if (item != null && following == null) {
            scope(item) { scheduleForm(task, item, data, it) }
            return
        }
        val old = item?.schedule
        val form = column()
        val title = input(form, "时段名称（必填）", draft?.getString("title") ?: old?.title ?: task?.title.orEmpty())
        var date = draft?.getString("date") ?: item?.let { PlanCalendar.dateText(Calendar.getInstance(TimeZone.getTimeZone(it.schedule.zoneId)).apply { timeInMillis = it.start }) } ?: model.date
        var minute = draft?.getInt("minute") ?: item?.let { Calendar.getInstance(TimeZone.getTimeZone(it.schedule.zoneId)).apply { timeInMillis = it.start }.let { c -> c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) } } ?: 9 * 60
        lateinit var dateButton: MaterialButton
        dateButton = button(form, "日期：$date") { datePicker(date) { date = it; dateButton.text = "日期：$it" } }
        lateinit var clockButton: MaterialButton
        fun clockLabel() = "开始：%02d:%02d".format(minute / 60, minute % 60)
        clockButton = button(form, clockLabel()) { TimePickerDialog(requireContext(), { _, h, m -> minute = h * 60 + m; clockButton.text = clockLabel() }, minute / 60, minute % 60, true).show() }
        val minutes = input(form, "时长（分钟，可精确到 1 分钟）", draft?.getString("minutes") ?: ((item?.let { (it.end - it.start) / 60_000 } ?: 30)).toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        button(form, "以 15 分钟递增时长") { minutes.setText(((minutes.text.toString().toIntOrNull() ?: 0) + 15).coerceAtMost(1440).toString()) }
        val kind = spinner(form, "时段类型", arrayOf("计划活动", "主动安排的自由活动", "缓冲"), draft?.getInt("kind") ?: when (old?.kind) { PlanKind.FREE -> 1; PlanKind.BUFFER -> 2; else -> 0 })
        val repeat = spinner(form, "重复", arrayOf("不重复", "每日", "每周指定星期"), draft?.getInt("repeat") ?: old?.repeat?.ordinal ?: 0)
        repeat.isEnabled = following != false
        val weekdays = if (draft == null) old?.weekdays.orEmpty() else draft.getIntArray("weekdays")?.toList().orEmpty()
        val weekdayChecks = (1..7).map { day -> CheckBox(requireContext()).apply { text = "每周${"一二三四五六日"[day - 1]}"; isChecked = day in weekdays; form.addView(this) } }
        weekdayChecks.forEach { it.isEnabled = following != false }
        text(form, "星期选择仅在每周重复时使用。时区：${old?.zoneId ?: TimeZone.getDefault().id}", 13f)
        var until = if (draft == null) old?.untilDate else draft.getString("until")
        lateinit var untilButton: MaterialButton
        untilButton = button(form, "重复至：${until ?: "不设结束日"}") { datePicker(until ?: date) { until = it; untilButton.text = "重复至：$it" } }
        val clearUntil = button(form, "清除重复结束日") { until = null; untilButton.text = "重复至：不设结束日" }
        untilButton.isEnabled = following != false
        clearUntil.isEnabled = following != false
        if (following == false) text(form, "仅修改这一次的名称、日期、时间、类型和提醒；重复规则保持不变。", 13f)
        val reminder = CheckBox(requireContext()).apply { text = "开始时提醒一次，不自动计时"; isChecked = draft?.getBoolean("remind") ?: (old?.remind == true); form.addView(this) }
        formDialog(if (item == null) "安排时段" else "调整时段", form, snapshot = {
            Bundle().apply {
                putString("form", "schedule"); putLong("writeToken", data.writeToken); putString("taskId", task?.id)
                putString("occurrenceId", item?.id); putLong("occurrenceStart", item?.start ?: 0); putBoolean("following", following != false)
                putString("title", title.text.toString()); putString("date", date); putInt("minute", minute); putString("minutes", minutes.text.toString())
                putInt("kind", kind.selectedItemPosition); putInt("repeat", repeat.selectedItemPosition)
                putIntArray("weekdays", weekdayChecks.mapIndexedNotNull { i, c -> (i + 1).takeIf { c.isChecked } }.toIntArray())
                putString("until", until); putBoolean("remind", reminder.isChecked)
            }
        }) { close ->
            val duration = minutes.text.toString().toIntOrNull()
            require(title.text.isNotBlank() && duration != null && duration in 1..1440) { "请填写名称和 1—1440 分钟时长" }
            val schedule = (old ?: PlanSchedule(title = title.text.toString().trim(), firstDate = date, startMinute = minute, durationMinutes = duration)).copy(
                taskId = task?.id, title = title.text.toString().trim(), firstDate = date, startMinute = minute, durationMinutes = duration,
                kind = listOf(PlanKind.ACTIVITY, PlanKind.FREE, PlanKind.BUFFER)[kind.selectedItemPosition], repeat = PlanRepeat.values()[repeat.selectedItemPosition],
                weekdays = if (repeat.selectedItemPosition == 2) weekdayChecks.mapIndexedNotNull { i, c -> (i + 1).takeIf { c.isChecked } } else emptyList(), untilDate = if (following == false) null else until, remind = reminder.isChecked)
            conflictCheck(schedule, item, following != false, data) {
                if (item == null) model.save(schedule) else model.edit(item, schedule, following != false)
                close()
            }
        }
    }
    private fun conflictCheck(schedule: PlanSchedule, old: PlanOccurrence?, following: Boolean, data: PlanningScreen, save: () -> Unit) {
        val candidate = if (old != null && !following) schedule.copy(repeat = PlanRepeat.NONE, weekdays = emptyList(), untilDate = null) else schedule
        val conflicts = PlanConflictPreview.find(candidate, data.schedules, data.exceptions) { p ->
            old != null && (p.id == old.id || following && p.schedule.id == old.schedule.id && p.date >= old.date)
        }
        if (conflicts.isEmpty()) save() else dialog(AlertDialog.Builder(requireContext()).setTitle("存在重叠时段")
            .setMessage("发现 ${conflicts.size} 个重叠实例，其中 ${conflicts.count { it.schedule.kind == PlanKind.BUFFER }} 个为缓冲。重复计划从双方开始及例外日期分别预览 370 天，未安排空白不会自动移走。\n" + conflicts.take(4).joinToString("\n") { "${it.date} ${it.schedule.title}" })
            .setNegativeButton("返回调整", null).setPositiveButton("保留重叠并保存") { _, _ -> save() })
    }
    private fun scope(item: PlanOccurrence, save: (Boolean) -> Unit) {
        if (item.schedule.repeat == PlanRepeat.NONE) save(true)
        else dialog(AlertDialog.Builder(requireContext()).setTitle("修改范围")
            .setItems(arrayOf("仅本次", "本次及以后")) { _, choice -> save(choice == 1) }.setNegativeButton("取消", null))
    }
    private fun presets(data: PlanningScreen) {
        val presets = TimerPolicy.entertainmentPresets
        dialog(AlertDialog.Builder(requireContext()).setTitle("试用模板 · 选择后预览")
            .setItems(presets.map { "${it.first} · 每 ${it.second} 分钟" }.toTypedArray()) { _, index ->
                val preset = presets[index]
                dialog(AlertDialog.Builder(requireContext()).setTitle(preset.first)
                    .setMessage("每 ${preset.second} 分钟提醒可以考虑收尾。可修改或关闭，不预填任何评分。选择所属活动后添加，不覆盖已有模板。")
                    .setPositiveButton("选择所属活动") { _, _ -> chooseType(data) { model.addPreset(preset.first, preset.second, it) } }.setNegativeButton("取消", null))
            }.setNegativeButton("返回", null))
    }

    private fun formDialog(title: String, content: LinearLayout, snapshot: () -> Bundle, save: (() -> Unit) -> Unit) {
        val dialog = AlertDialog.Builder(requireContext()).setTitle(title).setView(ScrollView(requireContext()).apply { addView(content) })
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialogs += dialog
        captureDraft = snapshot
        dialog.setOnDismissListener { if (captureDraft === snapshot) captureDraft = null; dialogs.remove(dialog) }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (model.busy.value == true) { message("正在读取规划，请稍后保存"); return@setOnClickListener }
            try { save { dialog.dismiss() } } catch (e: IllegalArgumentException) { message(e.message ?: "请检查输入") }
        } }
        dialog.show()
    }
    private fun datePicker(initial: String, changed: (String) -> Unit) {
        val date = PlanCalendar.date(initial, TimeZone.getDefault().id)
        DatePickerDialog(requireContext(), { _, y, m, d -> changed("%04d-%02d-%02d".format(Locale.ROOT, y, m + 1, d)) }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun dialog(builder: AlertDialog.Builder) { dialogs += builder.show() }
    private fun column() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
    private fun card(parent: LinearLayout) = column().also {
        it.background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(requireContext().getThemedAttr(R.attr.appCardBackgroundColor)) }
        parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }
    private fun text(parent: LinearLayout, value: String, size: Float) = TextView(requireContext()).apply {
        text = value; textSize = size; setTextColor(requireContext().getThemedAttr(R.attr.appTextPrimaryColor)); setPadding(0, dp(8), 0, dp(8)); parent.addView(this)
    }
    private fun button(parent: LinearLayout, title: String, weighted: Boolean = false, action: () -> Unit): MaterialButton = MaterialButton(ContextThemeWrapper(requireContext(), R.style.LifeClockUi2Button)).apply {
        text = title; isAllCaps = false; minHeight = dp(48); isEnabled = model.busy.value != true
        if (weighted) setPaddingRelative(dp(8), paddingTop, dp(8), paddingBottom)
        setOnClickListener { if (model.busy.value != true) action() }
        parent.addView(this, if (weighted) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
    }
    private fun updateActions(parent: ViewGroup, enabled: Boolean) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child is MaterialButton) child.isEnabled = enabled
            else if (child is ViewGroup) updateActions(child, enabled)
        }
    }
    private fun input(parent: LinearLayout, label: String, value: String): EditText = EditText(requireContext()).apply { hint = label; setText(value); minHeight = dp(48); parent.addView(this) }
    private fun spinner(parent: LinearLayout, label: String, values: Array<String>, selected: Int): Spinner {
        text(parent, label, 14f)
        return Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, values); setSelection(selected); minimumHeight = dp(48); parent.addView(this) }
    }
    private fun time(value: Long) = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(value))
    private fun duration(ms: Long) = "${ms / 60_000} 分钟"
    private fun status(value: PlanStatus) = when (value) { PlanStatus.OPEN -> "待进行"; PlanStatus.DONE -> "已完成"; PlanStatus.SKIPPED -> "已跳过"; PlanStatus.CANCELLED -> "已取消" }
    private fun flag(value: Boolean?) = if (value == null) 0 else if (value) 1 else 2
    private fun unflag(value: Int): Boolean? = if (value == 0) null else value == 1
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun message(text: String) = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    companion object { private val filters = arrayOf("全部", "重要且紧急", "重要、不紧急或未设紧急", "紧急、未标重要", "未标重要或紧急") }
}

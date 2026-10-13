package com.example.util.simpletimetracker.feature_change_record.view

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import android.widget.EditText
import android.text.InputType
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.core.R
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.di.BaseViewModelFactory
import com.example.util.simpletimetracker.feature_dialogs.api.DateTimeDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.DurationDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.OnTagValueSelectedListener
import com.example.util.simpletimetracker.feature_dialogs.api.TypesSelectionDialogListener
import com.example.util.simpletimetracker.core.extension.setSharedTransitions
import com.example.util.simpletimetracker.core.extension.toViewData
import com.example.util.simpletimetracker.core.sharedViewModel.RemoveRecordViewModel
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import com.example.util.simpletimetracker.core.utils.fragmentArgumentDelegate
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.feature_base_adapter.record.RecordViewData
import com.example.util.simpletimetracker.feature_change_record.viewData.ChangeRecordViewData
import com.example.util.simpletimetracker.feature_change_record.viewModel.ChangeRecordViewModel
import com.example.util.simpletimetracker.feature_comment_selection.api.CommentSelectionViewDelegateProvider
import com.example.util.simpletimetracker.feature_views.extension.animateColor
import com.example.util.simpletimetracker.feature_views.extension.setOnClick
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRecordParams
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRecordsFromScreen
import com.example.util.simpletimetracker.navigation.params.screen.RecordTagValueSelectionParams
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.util.simpletimetracker.feature_change_record.databinding.ChangeRecordFragmentBinding as Binding

@AndroidEntryPoint
class ChangeRecordFragment :
    BaseFragment<Binding>(),
    DateTimeDialogListener,
    DurationDialogListener,
    TypesSelectionDialogListener,
    OnTagValueSelectedListener {

    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> Binding =
        Binding::inflate

    override var insetConfiguration: InsetConfiguration =
        InsetConfiguration.ApplyToView { binding.root }

    @Inject
    lateinit var removeRecordViewModelFactory: BaseViewModelFactory<RemoveRecordViewModel>

    @Inject
    lateinit var commentDelegateProvider: CommentSelectionViewDelegateProvider

    private val viewModel: ChangeRecordViewModel by viewModels()
    private val details = RecordDetailsController(this) { viewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        details.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        details.saveState(outState)
    }
    private val removeRecordViewModel: RemoveRecordViewModel by activityViewModels(
        factoryProducer = { removeRecordViewModelFactory },
    )

    private var typeColorAnimator: ValueAnimator? = null
    private val core by lazy { ChangeRecordCore(viewModel, commentDelegateProvider) }

    private val extra: ChangeRecordParams by fragmentArgumentDelegate(
        key = ARGS_PARAMS, default = ChangeRecordParams.New(0),
    )

    override fun initUi(): Unit = with(binding) {
        postponeEnterTransition()

        setPreview()
        val transitionName: String = when (extra) {
            is ChangeRecordParams.Tracked -> (extra as? ChangeRecordParams.Tracked)?.transitionName.orEmpty()
            is ChangeRecordParams.Untracked -> (extra as? ChangeRecordParams.Untracked)?.transitionName.orEmpty()
            else -> ""
        }
        setSharedTransitions(
            additionalCondition = { transitionName.isNotEmpty() },
            transitionName = transitionName,
            sharedView = previewChangeRecord,
        )
        core.initUi(layoutChangeRecordCore)

        setOnPreDrawListener {
            startPostponedEnterTransition()
        }
    }

    override fun initUx() = with(binding) {
        core.initUx(this@ChangeRecordFragment, layoutChangeRecordCore)
        layoutChangeRecordCore.btnChangeRecordStatistics.setOnClick(viewModel::onStatisticsClick)
        layoutChangeRecordCore.btnChangeRecordDelete.setOnClick(viewModel::onDeleteClick)
        layoutChangeRecordCore.btnChangeRecordSave.setOnClick { viewModel.saveAs(RecordReviewState.PENDING) }
        lifeClockOrganizeButton.setOnClick { viewModel.saveAs(RecordReviewState.CONFIRMED) }
        lifeClockOrganizeButton.text = "确认时间"
        layoutChangeRecordCore.btnChangeRecordMore.setOnClickListener { showOrganizeMenu() }
    }

    override fun initViewModel() = with(binding) {
        details.bind(layoutChangeRecordCore.lifeClockExperienceEntry)
        with(viewModel) {
            extra = this@ChangeRecordFragment.extra
            record.observe(::updatePreview)
            removeRecordId.observe {
                removeRecordViewModel.onDeleteClick(
                    recordIds = setOf(it),
                    from = (extra as? ChangeRecordParams.Tracked)?.from,
                )
            }
            core.initViewModel(this@ChangeRecordFragment, layoutChangeRecordCore)
            reviewStatus.observe { state ->
                val label = when (state) {
                    1 -> R.string.life_clock_confirmed
                    2 -> R.string.life_clock_needs_repair
                    else -> R.string.life_clock_pending
                }
                lifeClockRecordState.text = getString(R.string.life_clock_current_state, getString(label))
            }
            saveButtonEnabled.observe(lifeClockOrganizeButton::setEnabled)
        }
        with(removeRecordViewModel) {
            prepare()
            deleteButtonEnabled.observe(layoutChangeRecordCore.btnChangeRecordDelete::setEnabled)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onVisible()
    }

    private fun showOrganizeMenu() {
        val actions = listOf(
            R.string.life_clock_save_pending, R.string.life_clock_confirm, R.string.life_clock_merge_previous,
            R.string.life_clock_split, R.string.life_clock_boundary_previous, R.string.life_clock_boundary_next,
        ).take(if (extra is ChangeRecordParams.Tracked) 6 else 2).map(::getString) +
            if (extra is ChangeRecordParams.Tracked) listOf("详细统计", "删除记录") else emptyList()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.life_clock_organize)
            .setItems(actions.toTypedArray()) { _, index ->
                when (index) {
                    0 -> viewModel.saveAs(RecordReviewState.PENDING)
                    1 -> viewModel.saveAs(RecordReviewState.CONFIRMED)
                    2 -> prepareMerge()
                    3 -> prepareSplit()
                    4 -> prepareBoundary(previous = true)
                    5 -> prepareBoundary(previous = false)
                    6 -> viewModel.onStatisticsClick()
                    7 -> if (binding.layoutChangeRecordCore.btnChangeRecordDelete.isEnabled) viewModel.onDeleteClick()
                }
            }.show()
    }

    private fun prepareMerge() = viewLifecycleOwner.lifecycleScope.launch {
        val current = viewModel.editorRecord()
        val previous = viewModel.neighbor(previous = true)
        if (previous == null) {
            Toast.makeText(requireContext(), R.string.life_clock_gap, Toast.LENGTH_LONG).show()
            return@launch
        }
        val tags = previous.tags.associateBy { it.tagId }.toMutableMap()
        val conflicts = mutableListOf<Pair<RecordBase.Tag, RecordBase.Tag>>()
        current.tags.forEach {
            val old = tags[it.tagId]
            if (old?.numericValue != null && it.numericValue != null && old.numericValue != it.numericValue) conflicts += old to it
            tags[it.tagId] = if (it.numericValue == null && old?.numericValue != null) old else it
        }
        val names = conflicts.map { viewModel.tagName(it.first.tagId) }
        fun choose(index: Int) {
            if (index == conflicts.size) {
                confirmOperation("${range(previous)}\n${range(current)}\n→ ${range(current.copy(timeStarted = previous.timeStarted))}") {
                    viewModel.mergePrevious(previous.id, tags.values.toList())
                }
                return
            }
            val (old, new) = conflicts[index]
            MaterialAlertDialogBuilder(requireContext()).setTitle(names[index])
                .setItems(
                    arrayOf(
                        "${getString(R.string.life_clock_current_value)}: ${new.numericValue}",
                        "${getString(R.string.life_clock_previous_value)}: ${old.numericValue}",
                        getString(R.string.life_clock_clear_value),
                    ),
                ) { _, choice ->
                    tags[new.tagId] = when (choice) {
                        0 -> new
                        1 -> old
                        else -> new.copy(numericValue = null, ratedAt = null)
                    }
                    choose(index + 1)
                }.show()
        }
        choose(0)
    }

    private fun prepareSplit() = viewLifecycleOwner.lifecycleScope.launch {
        val record = viewModel.editorRecord()
        minutesDialog(range(record), record.timeStarted) { boundary ->
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.life_clock_split)
                .setMessage("${range(record.copy(timeEnded = boundary))}\n${range(record.copy(timeStarted = boundary))}")
                .setPositiveButton(R.string.life_clock_keep_before) { _, _ -> viewModel.splitAt(boundary, true) }
                .setNegativeButton(R.string.life_clock_keep_after) { _, _ -> viewModel.splitAt(boundary, false) }
                .setNeutralButton(R.string.cancel, null).show()
        }
    }

    private fun prepareBoundary(previous: Boolean) = viewLifecycleOwner.lifecycleScope.launch {
        val current = viewModel.editorRecord()
        val adjacent = viewModel.boundaryNeighbor(previous) ?: run {
            Toast.makeText(requireContext(), R.string.life_clock_gap, Toast.LENGTH_LONG).show()
            return@launch
        }
        val neighbor = adjacent.record
        val first = if (previous) neighbor else current
        val second = if (previous) current else neighbor
        minutesDialog("${range(first)}\n${range(second)}", first.timeStarted) { boundary ->
            confirmOperation("${range(first.copy(timeEnded = boundary))}\n${range(second.copy(timeStarted = boundary))}") {
                viewModel.adjustWith(neighbor.id, boundary, previous, adjacent.isRunning)
            }
        }
    }

    private fun minutesDialog(preview: String, start: Long, apply: (Long) -> Unit) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.life_clock_minutes_before)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.life_clock_minutes_before)
            .setMessage(preview).setView(input).setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val boundary = runCatching {
                    BigDecimal.valueOf(start).add(BigDecimal(input.text.toString().trim().replace(',', '.')).multiply(BigDecimal(60_000))).longValueExact()
                }.getOrNull()
                if (boundary == null || boundary <= start) {
                    input.error = getString(R.string.life_clock_empty_interval)
                } else {
                    dialog.dismiss()
                    apply(boundary)
                }
            }
        }
        dialog.show()
    }

    private fun confirmOperation(preview: String, apply: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.life_clock_organize)
            .setMessage("${getString(R.string.life_clock_confirm_operation)}\n\n$preview")
            .setPositiveButton(R.string.ok) { _, _ -> apply() }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun range(record: Record): String {
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = record.zoneId?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
        }
        return "${format.format(Date(record.timeStarted))} → ${format.format(Date(record.timeEnded))} (${(record.timeEnded - record.timeStarted) / 60000.0} min)"
    }

    override fun onDestroy() {
        typeColorAnimator?.cancel()
        super.onDestroy()
    }

    override fun onDateTimeSet(timestamp: Long, tag: String?) {
        viewModel.onDateTimeSet(timestamp, tag)
    }

    override fun onDurationSet(durationSeconds: Long, tag: String?) {
        viewModel.onDurationSet(durationSeconds, tag)
    }

    override fun onTagValueSelected(params: RecordTagValueSelectionParams, data: Double) {
        viewModel.onCategoryValueSelected(params, data)
    }

    override fun onDataSelected(
        tag: String?,
        dataIds: List<Long>,
        tagValues: List<RecordBase.Tag>,
        selectValueOnStartTagIds: List<Long>,
    ) {
        viewModel.onDataSelected(tag, dataIds)
    }

    private fun setPreview() = when (extra) {
        is ChangeRecordParams.Tracked -> (extra as? ChangeRecordParams.Tracked)?.preview
        is ChangeRecordParams.Untracked -> (extra as? ChangeRecordParams.Untracked)?.preview
        else -> null
    }?.let { preview ->
        ChangeRecordViewData(
            recordPreview = RecordViewData.Tracked(
                id = 0, // Doesn't matter for preview.
                timeStartedTimestamp = 0,
                timeEndedTimestamp = 0,
                name = preview.name,
                tagName = preview.tagName,
                timeStarted = preview.timeStarted,
                timeFinished = preview.timeFinished,
                duration = preview.duration,
                iconId = preview.iconId.toViewData(),
                color = preview.color,
                comment = preview.comment,
            ),
            dateTimeStarted = preview.timeStartedDateTime.toViewData(),
            dateTimeFinished = preview.timeEndedDateTime.toViewData(),
        ).let { updatePreview(it, animated = false) }

        core.onSetPreview(
            binding = binding.layoutChangeRecordCore,
            color = preview.color,
            iconId = preview.iconId.toViewData(),
        )
    }

    private fun updatePreview(
        item: ChangeRecordViewData,
        animated: Boolean = true,
    ) = with(binding.layoutChangeRecordCore) {
        with(binding.previewChangeRecord) {
            val compact = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.3f
            itemName = item.recordPreview.name
            itemTagName = if (compact) "" else item.recordPreview.tagName
            itemIcon = item.recordPreview.iconId
            itemTimeStarted = if (compact) "" else item.recordPreview.timeStarted
            itemTimeEnded = if (compact) "" else item.recordPreview.timeFinished
            itemDuration = item.recordPreview.duration
            itemComment = if (compact) "" else item.recordPreview.comment

            if (animated) {
                typeColorAnimator?.cancel()
                typeColorAnimator = animateColor(
                    from = itemColor,
                    to = item.recordPreview.color,
                    doOnUpdate = { value -> itemColor = value },
                )
            } else {
                itemColor = item.recordPreview.color
            }
        }

        core.setDateTime(
            state = item.dateTimeStarted,
            dateView = tvChangeRecordTimeStartedDate,
            timeView = tvChangeRecordTimeStartedTime,
            hintView = tvChangeRecordTimeStartedAdjust,
        )
        core.setDateTime(
            state = item.dateTimeFinished,
            dateView = tvChangeRecordTimeEndedDate,
            timeView = tvChangeRecordTimeEndedTime,
            hintView = tvChangeRecordTimeEndedAdjust,
        )

        core.onSetPreview(
            binding = this,
            color = item.recordPreview.color,
            iconId = item.recordPreview.iconId,
        )
    }

    companion object {
        private const val ARGS_PARAMS = "args_change_record_params"

        fun createBundle(data: ChangeRecordsFromScreen): Bundle = Bundle().apply {
            putParcelable(ARGS_PARAMS, data.params)
        }
    }
}

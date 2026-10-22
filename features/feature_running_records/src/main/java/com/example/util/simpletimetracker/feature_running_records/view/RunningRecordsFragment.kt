package com.example.util.simpletimetracker.feature_running_records.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.di.BaseViewModelFactory
import com.example.util.simpletimetracker.feature_dialogs.api.OnTagSelectedListener
import com.example.util.simpletimetracker.feature_dialogs.api.StandardDialogListener
import com.example.util.simpletimetracker.core.sharedViewModel.MainTabsViewModel
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import com.example.util.simpletimetracker.core.utils.doOnApplyWindowInsetsListener
import com.example.util.simpletimetracker.core.utils.getNavBarInsetsBottom
import com.example.util.simpletimetracker.core.utils.updateRunningRecordPreview
import com.example.util.simpletimetracker.core.viewData.RecordTypeSuggestionType
import com.example.util.simpletimetracker.domain.record.interactor.UpdateRunningRecordsInteractor
import com.example.util.simpletimetracker.feature_base_adapter.BaseRecyclerAdapter
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.activityFilter.createActivityFilterAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.activityFilter.createActivityFilterAddAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.commentField.createCommentFieldAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.divider.createDividerAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.emptySpace.createEmptySpaceAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.hint.createHintAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.hintBig.createHintBigAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.loader.createLoaderAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.recordShortcut.createRecordShortcutAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.recordType.createRecordTypeAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.recordTypeSpecial.createRunningRecordTypeSpecialAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.recordTypeSuggestion.createRecordTypeSuggestionAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.recordWithHint.createRecordWithHintAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.runningRecord.createRunningRecordAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.runningRecord.RunningRecordViewData
import com.example.util.simpletimetracker.feature_running_records.R
import com.example.util.simpletimetracker.feature_running_records.viewModel.RunningRecordsViewModel
import com.example.util.simpletimetracker.feature_views.TransitionNames
import com.example.util.simpletimetracker.feature_views.extension.addOnScrollListenerAdapter
import com.example.util.simpletimetracker.feature_views.extension.pxToDp
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.util.simpletimetracker.feature_running_records.databinding.RunningRecordsFragmentBinding as Binding

@AndroidEntryPoint
class RunningRecordsFragment :
    BaseFragment<Binding>(),
    OnTagSelectedListener,
    StandardDialogListener {

    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> Binding =
        Binding::inflate

    override var insetConfiguration: InsetConfiguration =
        InsetConfiguration.DoNotApply

    @Inject
    lateinit var mainTabsViewModelFactory: BaseViewModelFactory<MainTabsViewModel>

    private val viewModel: RunningRecordsViewModel by viewModels()
    private val mainTabsViewModel: MainTabsViewModel by activityViewModels(
        factoryProducer = { mainTabsViewModelFactory },
    )

    private var pendingRunningRecords: List<ViewHolderType>? = null

    private val runningRecordsAdapter: BaseRecyclerAdapter by lazy {
        BaseRecyclerAdapter(
            createEmptySpaceAdapterDelegate(),
            createLoaderAdapterDelegate(),
            createNowEmptyAdapterDelegate(),
            createNowGroupAdapterDelegate(
                onToggle = viewModel::onGroupToggle,
                onStart = viewModel::onGroupStart,
                onEdit = viewModel::onGroupEdit,
            ),
            createNowTimerStatusAdapterDelegate(viewModel::onTimerStatusClick),
            createHintAdapterDelegate(),
            createHintBigAdapterDelegate(),
            createDividerAdapterDelegate(),
            createRunningRecordAdapterDelegate(
                transitionNamePrefix = TransitionNames.RUNNING_RECORD_FROM_MAIN,
                onItemClick = viewModel::onRunningRecordClick,
                onItemLongClick = viewModel::onRunningRecordLongClick,
                onStop = viewModel::stopFromButton,
                onEdit = viewModel::editFromButton,
                softAppearance = true,
            ),
            createRecordTypeAdapterDelegate(
                onItemClickWithData = viewModel::onRecordTypeClick,
                onItemLongClick = viewModel::onRecordTypeLongClick,
                withTransition = true,
                softAppearance = true,
            ),
            createRecordTypeSuggestionAdapterDelegate(
                type = RecordTypeSuggestionType,
                softAppearance = true,
                onItemClickWithData = viewModel::onRecordTypeClick,
                onItemLongClick = viewModel::onRecordTypeLongClick,
            ),
            createRecordWithHintAdapterDelegate(
                onItemClick = throttle(viewModel::onRecordClick),
                onItemLongClick = throttle(viewModel::onRecordLongClick),
            ),
            createRecordShortcutAdapterDelegate(
                onClickWithTransition = throttle(viewModel::onShortcutClick),
                onLongClickWithTransition = throttle(viewModel::onShortcutLongClick),
                onSpinnerPositionSelected = viewModel::onShortcutSpinnerPositionSelected,
                onButtonClicked = viewModel::onShortcutButtonClick,
                softAppearance = true,
            ),
            createRunningRecordTypeSpecialAdapterDelegate(
                onItemClick = throttle(viewModel::onSpecialRecordTypeClick),
            ),
            createActivityFilterAdapterDelegate(
                onClick = viewModel::onActivityFilterClick,
                onLongClick = viewModel::onActivityFilterLongClick,
            ),
            createActivityFilterAddAdapterDelegate(
                onItemClick = throttle(viewModel::onActivityFilterSpecialClick),
            ),
            createCommentFieldAdapterDelegate(
                afterTextChange = viewModel::onSearchTextChange,
            ),
        )
    }

    override fun initUi(): Unit = with(binding) {
        parentFragment?.postponeEnterTransition()

        if (resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.3f) {
            nowTitle.textSize = 20f
            nowSubtitle.visibility = View.GONE
            val size = (56 * resources.displayMetrics.density).toInt()
            nowCat.layoutParams.width = size
            nowCat.layoutParams.height = size
        }

        rvRunningRecordsList.apply {
            layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.CENTER
                flexWrap = FlexWrap.WRAP
            }
            adapter = runningRecordsAdapter
            setHasFixedSize(false)
        }

        view?.doOnApplyWindowInsetsListener {
            viewModel.onChangeInsets(navBarHeight = it.getNavBarInsetsBottom().pxToDp())
        }

        setOnPreDrawListener {
            parentFragment?.startPostponedEnterTransition()
        }
    }

    override fun initUx() = with(binding) {
        // Problem. There are some problems with flexbox manager and long list of items,
        // one of which is changing (ex. timer on running record).
        // setHasFixedSize(false) causes items to lag on scroll.
        // setHasFixedSize(true) causes items disappear on scrolling to bottom,
        // opening another screen and returning back.
        // Solution. Set to true only on scroll, return back to false on scroll stop
        // and onPause (navigation).
        // Solution 2. Delay adapter update while scrolling, update after, keep setHasFixedSize(false).
        rvRunningRecordsList.addOnScrollListenerAdapter { _, newState ->
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                pendingRunningRecords?.let(::onRunningRecordsUpdate)
                pendingRunningRecords = null
            }
        }
    }

    override fun initViewModel() {
        with(viewModel) {
            runningRecords.observe(::onRunningRecordsUpdate)
            resetScreen.observe { resetScreen() }
            previewUpdate.observe(::onPreviewUpdate)
        }
        with(mainTabsViewModel) {
            tabReselected.observe(viewModel::onTabReselected)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onVisible()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onHidden()
    }

    override fun onTagSelected() {
        viewModel.onTagSelected()
    }

    override fun onPositiveClick(tag: String?, data: Any?) {
        viewModel.onPositiveClick(tag)
    }

    private fun resetScreen() = with(binding) {
        rvRunningRecordsList.smoothScrollToPosition(0)
        mainTabsViewModel.onHandled()
    }

    private fun onRunningRecordsUpdate(items: List<ViewHolderType>) {
        if (binding.rvRunningRecordsList.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            val previous = runningRecordsAdapter.currentList.filterIsInstance<RunningRecordViewData>()
                .map { it.id to it.timeStartedTimestamp }
            val current = items.filterIsInstance<RunningRecordViewData>()
                .map { it.id to it.timeStartedTimestamp }
            runningRecordsAdapter.submitList(items) {
                if (current.isNotEmpty() && current != previous) {
                    view?.findViewById<RecyclerView>(R.id.rvRunningRecordsList)?.scrollToPosition(0)
                }
            }
        } else {
            pendingRunningRecords = items
        }
    }

    private fun onPreviewUpdate(update: UpdateRunningRecordsInteractor.Update) {
        updateRunningRecordPreview(
            currentList = runningRecordsAdapter.currentList,
            recyclerView = binding.rvRunningRecordsList,
            update = update,
        )
    }

    companion object {
        fun newInstance() = RunningRecordsFragment()
    }
}

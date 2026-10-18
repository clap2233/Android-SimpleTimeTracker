package com.example.util.simpletimetracker.feature_settings.view

import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.di.BaseViewModelFactory
import com.example.util.simpletimetracker.feature_dialogs.api.DataExportSettingsDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.DateTimeDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.DurationDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.OptionsListDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.StandardDialogListener
import com.example.util.simpletimetracker.feature_dialogs.api.TypesSelectionDialogListener
import com.example.util.simpletimetracker.core.sharedViewModel.MainTabsViewModel
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.feature_base_adapter.BaseRecyclerAdapter
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.feature_settings.model.AdvancedOptionsBlockClickListener
import com.example.util.simpletimetracker.feature_settings.viewModel.SettingsViewModel
import com.example.util.simpletimetracker.feature_settings.views.getSettingsAdapterDelegates
import com.example.util.simpletimetracker.navigation.params.screen.DataExportSettingsResult
import com.example.util.simpletimetracker.navigation.params.screen.OptionsListParams
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.util.simpletimetracker.feature_settings.databinding.SettingsFragmentBinding as Binding

@AndroidEntryPoint
class SettingsFragment :
    BaseFragment<Binding>(),
    StandardDialogListener,
    DurationDialogListener,
    DateTimeDialogListener,
    DataExportSettingsDialogListener,
    TypesSelectionDialogListener,
    OptionsListDialogListener,
    AdvancedOptionsBlockClickListener {

    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> Binding =
        Binding::inflate

    override var insetConfiguration: InsetConfiguration =
        InsetConfiguration.DoNotApply

    @Inject
    lateinit var mainTabsViewModelFactory: BaseViewModelFactory<MainTabsViewModel>

    private val viewModel: SettingsViewModel by viewModels()
    private val fullBackupModel: com.example.util.simpletimetracker.feature_settings.viewModel.FullBackupViewModel by viewModels()
    private val fullBackup = FullBackupController(this) { fullBackupModel }
    private val cloudBackupModel: com.example.util.simpletimetracker.feature_settings.viewModel.CloudBackupViewModel by viewModels()
    private val cloudBackup = CloudBackupController(this) { cloudBackupModel }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        fullBackup.restoreState(savedInstanceState)
    }
    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        fullBackup.saveState(outState)
    }
    private val mainTabsViewModel: MainTabsViewModel by activityViewModels { mainTabsViewModelFactory }

    private val contentAdapter: BaseRecyclerAdapter by lazy {
        BaseRecyclerAdapter(
            *getSettingsAdapterDelegates(
                onBlockClicked = viewModel::onBlockClicked,
                onBlockClickedThrottled = throttle(viewModel::onBlockClicked),
                onSpinnerPositionSelected = viewModel::onSpinnerPositionSelected,
            ).toTypedArray(),
        )
    }

    private val sectionBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.openSection(null)
        }
    }

    override fun initUi() = with(binding) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, sectionBack)
        settingsBack.setOnClickListener { viewModel.openSection(null) }
        settingsActivities.setOnClickListener { viewModel.openSection("activities") }
        settingsReminders.setOnClickListener { viewModel.openSection("reminders") }
        settingsAppearance.setOnClickListener { viewModel.openSection("appearance") }
        settingsData.setOnClickListener { viewModel.openSection("data") }
        settingsAdvanced.setOnClickListener { viewModel.openSection("advanced") }
        settingsAbout.setOnClickListener { viewModel.openSection("about") }
        settingsHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("从这里开始")
                .setMessage("现在：点活动开始，点运行卡停止。活动或模板可设置专注退出轻提醒、周期收尾提醒。\n\n时间：点色块编辑，点过去的空档补记。真实时长不必凑满 15 分钟，合法记录直接保存。\n\n详情：快乐和意义均为 −5—＋5，两项都填写才显示合计；0 和未填写不同。恢复感、照片和情境都可跳过。点击保存后生效。\n\n规划：待办只需标题，可安排重复时段或缓冲。计划不会自动成为经历。\n\n回顾：看时间与体验，选择下周想保留或调整的事。\n\n数据：完整 ZIP 含照片和规划，可用于恢复；CSV 和 ICS 不能代替完整备份。")
                .setPositiveButton("知道了", null).show()
        }
        settingsLicense.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val license = withContext(Dispatchers.IO) { requireContext().assets.open("life-clock-license.txt").bufferedReader().use { it.readText() } }
                MaterialAlertDialogBuilder(requireContext()).setTitle("项目来源与开源许可")
                    .setMessage("生活时间钟基于 Simple Time Tracker 修改。\n上游：github.com/Razeeman/Android-SimpleTimeTracker\n许可：GNU GPL v3 或更新版本。发布源码随安装包交付。\n\n" + license)
                    .setPositiveButton("关闭", null).show()
            }
        }
        rvSettingsContent.adapter = contentAdapter
        rvSettingsContent.itemAnimator = null
    }

    override fun initViewModel(): Unit = with(binding) {
        fullBackup.bind(lifeClockBackupButton)
        cloudBackup.bind(lifeClockCloudBackupButton)
        viewModel.content.observe(contentAdapter::replaceAsNew)
        viewModel.section.observe { section ->
            settingsHome.isVisible = section == null
            settingsSection.isVisible = section != null
            settingsBack.isVisible = section != null
            sectionBack.isEnabled = section != null && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            settingsBackupActions.isVisible = section == "data"
            settingsAboutContent.isVisible = section == "about"
            settingsSectionTitle.text = when (section) {
                "activities" -> "活动与模板"
                "reminders" -> "提醒与计时"
                "appearance" -> "外观与显示"
                "data" -> "数据与备份"
                "advanced" -> "高级设置"
                "about" -> "关于与帮助"
                else -> "设置"
            }
            settingsSection.scrollTo(0, 0)
        }
        viewModel.resetScreen.observe {
            rvSettingsContent.smoothScrollToPosition(0)
            viewModel.openSection(null)
            viewModel.onResetScreen()
            mainTabsViewModel.onHandled()
        }
        viewModel.themeChanged.observe(::changeTheme)
        viewModel.keepScreenOnCheckbox.observe(::setKeepScreenOn)
        with(mainTabsViewModel) {
            tabReselected.observe(viewModel::onTabReselected)
            isNavBatAtTheBottom.observe(::updateInsetConfiguration)
        }
    }

    override fun onResume() {
        super.onResume()
        sectionBack.isEnabled = viewModel.section.value != null
        viewModel.onVisible()
    }

    override fun onPause() {
        sectionBack.isEnabled = false
        viewModel.onHidden()
        super.onPause()
    }

    override fun onPositiveClick(tag: String?, data: Any?) {
        viewModel.onPositiveClick(tag)
    }

    override fun onDurationSet(durationSeconds: Long, tag: String?) {
        viewModel.onDurationSet(tag, durationSeconds)
    }

    override fun onDisable(tag: String?) {
        viewModel.onDurationDisabled(tag)
    }

    override fun onDateTimeSet(timestamp: Long, tag: String?) {
        viewModel.onDateTimeSet(timestamp, tag)
    }

    override fun onDataExportSettingsSelected(data: DataExportSettingsResult) {
        viewModel.onDataExportSettingsSelected(data)
    }

    override fun onDataSelected(
        tag: String?,
        dataIds: List<Long>,
        tagValues: List<RecordBase.Tag>,
        selectValueOnStartTagIds: List<Long>,
    ) {
        viewModel.onTypesSelected(dataIds, tag)
    }

    override fun onOptionsItemClick(id: OptionsListParams.Item.Id) {
        viewModel.onOptionsItemClick(id)
    }

    override fun onAdvancedOptionsBlockClicked(block: SettingsBlock) {
        viewModel.onBlockClicked(block)
    }

    override fun onAdvancedOptionsSpinnerPositionSelected(block: SettingsBlock, position: Int) {
        viewModel.onSpinnerPositionSelected(block, position)
    }

    private fun setKeepScreenOn(keepScreenOn: Boolean) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun changeTheme(themeChanged: Boolean) {
        if (themeChanged) {
            activity?.recreate()
            // TODO fix fade and save scroll
            activity?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            viewModel.onThemeChanged()
        }
    }

    private fun updateInsetConfiguration(isNavBatAtTheBottom: Boolean) {
        insetConfiguration = if (isNavBatAtTheBottom) {
            InsetConfiguration.DoNotApply
        } else {
            InsetConfiguration.ApplyToView { binding.rvSettingsContent }
        }
        initInsets()
    }

    companion object {
        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}

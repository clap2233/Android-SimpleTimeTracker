package com.example.util.simpletimetracker.feature_settings.viewModel.delegate

import com.example.util.simpletimetracker.core.base.ViewModelDelegate
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_settings.interactor.SettingsRatingViewDataInteractor
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.screen.DebugMenuDialogParams
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsRatingViewModelDelegate @Inject constructor(
    private val router: Router,
    private val settingsRatingViewDataInteractor: SettingsRatingViewDataInteractor,
) : ViewModelDelegate() {

    private var parent: SettingsParent? = null
    private var debugUnlocked = false
    private var debugClicksCount: Int = 0

    fun init(parent: SettingsParent) {
        this.parent = parent
    }

    fun onHidden() {
        debugClicksCount = 0
    }

    fun getViewData(): List<ViewHolderType> {
        return settingsRatingViewDataInteractor.execute(debugUnlocked)
    }

    fun onBlockClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.Version -> onVersionClick()
            SettingsBlock.DebugMenu -> onDebugMenuClick()
            else -> {
                // Do nothing
            }
        }
    }

    private fun onVersionClick() {
        debugClicksCount += 1
        if (debugClicksCount >= DEBUG_CLICKS_TO_UNLOCK) {
            debugUnlocked = true
            delegateScope.launch { parent?.updateContent() }
        }
    }

    private fun onDebugMenuClick() {
        router.navigate(DebugMenuDialogParams)
    }

    companion object {
        private const val DEBUG_CLICKS_TO_UNLOCK = 5
    }
}
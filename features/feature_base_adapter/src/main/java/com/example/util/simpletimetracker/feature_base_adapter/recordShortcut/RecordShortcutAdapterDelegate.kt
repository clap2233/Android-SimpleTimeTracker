package com.example.util.simpletimetracker.feature_base_adapter.recordShortcut

import com.example.util.simpletimetracker.feature_base_adapter.createRecyclerBindingAdapterDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.example.util.simpletimetracker.feature_base_adapter.databinding.ItemRecordShortcutLayoutBinding as Binding
import com.example.util.simpletimetracker.feature_base_adapter.recordShortcut.RecordShortcutViewData as ViewData
import com.example.util.simpletimetracker.feature_views.TransitionNames
import com.example.util.simpletimetracker.feature_views.extension.setOnClick
import com.example.util.simpletimetracker.feature_views.extension.setOnClickWith
import com.example.util.simpletimetracker.feature_views.extension.setOnLongClick
import com.example.util.simpletimetracker.feature_views.extension.setOnLongClickWith

fun createRecordShortcutAdapterDelegate(
    onClick: ((ViewData) -> Unit)? = null,
    onLongClick: ((ViewData) -> Unit)? = null,
    onClickWithTransition: ((ViewData, Pair<Any, String>) -> Unit)? = null,
    onLongClickWithTransition: ((ViewData, Pair<Any, String>) -> Unit)? = null,
    onSpinnerPositionSelected: ((block: ViewData, position: Int) -> Unit)? = null,
    onButtonClicked: ((block: ViewData) -> Unit)? = null,
    softAppearance: Boolean = false,
) = createRecyclerBindingAdapterDelegate<ViewData, Binding>(
    Binding::inflate,
) { binding, item, _ ->

    with(binding.viewRecordShortcutItem) {
        item as ViewData

        binding.tvRecordShortcutItemHint.text = item.hint

        this.softAppearance = softAppearance
        itemColor = item.data.color
        itemName = item.data.name
        itemIconColor = item.data.iconColor
        itemIconAlpha = item.data.iconAlpha
        itemIconVisible = item.data.icon != null
        item.data.icon?.let(this::itemIcon::set)

        if (item.spinnerData != null) {
            binding.btnItemSettings.isVisible = item.spinnerData.isButtonVisible
            binding.spaceRecordShortcutButtonItem.isVisible = item.spinnerData.isButtonVisible
            binding.spinnerItemSettings.isVisible = true
            binding.spinnerItemSettings.setProcessSameItemSelection(item.spinnerData.processSameItemSelected)
            binding.spinnerItemSettings.setData(item.spinnerData.items, item.spinnerData.selectedPosition)
            binding.spinnerItemSettings.onPositionSelected = { onSpinnerPositionSelected?.invoke(item, it) }
        } else {
            binding.btnItemSettings.isVisible = false
            binding.spaceRecordShortcutButtonItem.isVisible = false
            binding.spinnerItemSettings.isVisible = false
        }

        val transitionName = TransitionNames.RECORD_SHORTCUT + item.id
        val sharedElements = this to transitionName
        ViewCompat.setTransitionName(this, transitionName)

        onClick?.let { setOnClickWith(item, it) }
        onLongClick?.let { setOnLongClickWith(item, it) }
        onClickWithTransition?.let { setOnClick { onClickWithTransition(item, sharedElements) } }
        onLongClickWithTransition?.let { setOnLongClick { onLongClickWithTransition(item, sharedElements) } }
        binding.btnItemSettings.setOnClick { onButtonClicked?.invoke(item) }
    }
}
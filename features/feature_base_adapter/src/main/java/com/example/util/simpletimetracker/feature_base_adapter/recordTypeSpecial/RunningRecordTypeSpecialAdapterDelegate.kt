package com.example.util.simpletimetracker.feature_base_adapter.recordTypeSpecial

import com.example.util.simpletimetracker.feature_base_adapter.createRecyclerBindingAdapterDelegate
import com.example.util.simpletimetracker.feature_views.extension.setOnClickWith
import com.example.util.simpletimetracker.feature_base_adapter.databinding.ItemRecordTypeActionBinding as Binding
import com.example.util.simpletimetracker.feature_base_adapter.recordTypeSpecial.RunningRecordTypeSpecialViewData as ViewData

fun createRunningRecordTypeSpecialAdapterDelegate(
    onItemClick: ((ViewData) -> Unit),
) = createRecyclerBindingAdapterDelegate<ViewData, Binding>(
    Binding::inflate,
) { binding, item, _ ->

    with(binding.viewRecordTypeItem) {
        item as ViewData

        text = if (item.type is ViewData.Type.Default) "默认活动" else item.name
        setOnClickWith(item, onItemClick)
    }
}
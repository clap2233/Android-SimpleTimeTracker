package com.example.util.simpletimetracker.feature_running_records.view

import androidx.core.view.isVisible
import com.example.util.simpletimetracker.feature_base_adapter.createRecyclerBindingAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.empty.EmptyViewData
import com.example.util.simpletimetracker.feature_running_records.databinding.ItemNowEmptyBinding

fun createNowEmptyAdapterDelegate() = createRecyclerBindingAdapterDelegate<EmptyViewData, ItemNowEmptyBinding>(
    ItemNowEmptyBinding::inflate,
) { binding, item, _ ->
    item as EmptyViewData
    binding.tvEmptyItem.text = item.message
    binding.tvEmptyItemHint.text = item.hint
    binding.tvEmptyItemHint.isVisible = item.hint.isNotEmpty()
}

package com.example.util.simpletimetracker.feature_running_records.view

import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.createRecyclerBindingAdapterDelegate
import com.example.util.simpletimetracker.feature_running_records.databinding.ItemNowTimerStatusBinding

data class NowTimerStatusViewData(val id: Long, val text: String) : ViewHolderType {
    override fun getUniqueId(): Long = id
    override fun isValidType(other: ViewHolderType): Boolean = other is NowTimerStatusViewData
}

fun createNowTimerStatusAdapterDelegate(onClick: () -> Unit) =
    createRecyclerBindingAdapterDelegate<NowTimerStatusViewData, ItemNowTimerStatusBinding>(
        ItemNowTimerStatusBinding::inflate,
    ) { binding, item, _ ->
        item as NowTimerStatusViewData
        binding.nowTimerStatus.text = item.text
        binding.nowTimerStatus.setOnClickListener { onClick() }
        binding.nowTimerStatus.setOnLongClickListener {
            val context = binding.root.context
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("计时提醒状态", item.text))
            android.widget.Toast.makeText(context, "已复制提醒状态", android.widget.Toast.LENGTH_SHORT).show()
            true
        }
    }

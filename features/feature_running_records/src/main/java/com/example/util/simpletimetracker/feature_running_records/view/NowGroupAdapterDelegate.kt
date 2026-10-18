package com.example.util.simpletimetracker.feature_running_records.view

import androidx.core.view.isVisible
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.createRecyclerBindingAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.recordType.RecordTypeViewData
import com.example.util.simpletimetracker.feature_running_records.databinding.ItemNowGroupBinding

data class NowGroupViewData(
    val id: Long,
    val name: String,
    val expanded: Boolean,
    val policy: String,
    val canStart: Boolean = true,
    val activity: RecordTypeViewData? = null,
) : ViewHolderType {
    override fun getUniqueId(): Long = id
    override fun isValidType(other: ViewHolderType): Boolean = other is NowGroupViewData
}

fun createNowGroupAdapterDelegate(
    onToggle: (NowGroupViewData) -> Unit,
    onStart: (NowGroupViewData) -> Unit,
    onEdit: (NowGroupViewData, Pair<Any, String>) -> Unit,
) = createRecyclerBindingAdapterDelegate<NowGroupViewData, ItemNowGroupBinding>(
    ItemNowGroupBinding::inflate,
) { binding, item, _ ->
    item as NowGroupViewData
    binding.nowGroupIcon.isVisible = item.activity != null
    item.activity?.let { activity ->
        binding.nowGroupIcon.itemIcon = activity.iconId
        binding.nowGroupIcon.itemIconColor = activity.iconColor
        binding.nowGroupIcon.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(activity.color)
        }
    }
    binding.nowGroupTitle.text = "${if (item.expanded) "⌄" else "›"} ${item.name}"
    binding.nowGroupTitle.contentDescription = "${item.name}，${if (item.expanded) "收起" else "展开"}"
    binding.nowGroupTitle.setOnClickListener { onToggle(item) }
    binding.nowGroupTitle.setOnLongClickListener {
        onEdit(item, binding.nowGroupTitle to "now_group_${item.id}")
        true
    }
    binding.nowGroupPolicy.text = item.policy
    binding.nowGroupStart.isVisible = item.canStart
    binding.nowGroupStart.text = "通用计时"
    binding.nowGroupStart.contentDescription = "${item.name}通用计时"
    binding.nowGroupStart.setOnClickListener { onStart(item) }
}

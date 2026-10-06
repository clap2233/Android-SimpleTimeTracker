package com.example.util.simpletimetracker.domain.recordTag.model

import com.example.util.simpletimetracker.domain.color.model.AppColor

/**
 * If [iconColorSource] != 0, take color from activity with this typeId,
 * otherwise take from [icon] and [color] fields.
 */
data class RecordTag(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: AppColor,
    val iconColorSource: Long,
    val note: String,
    val archived: Boolean = false,
    val valueType: RecordTagValueType,
    val valueSuffix: String,
    val purpose: RecordTagPurpose = RecordTagPurpose.GENERAL,
)

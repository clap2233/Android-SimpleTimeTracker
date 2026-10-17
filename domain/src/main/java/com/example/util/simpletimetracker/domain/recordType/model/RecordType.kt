package com.example.util.simpletimetracker.domain.recordType.model

import com.example.util.simpletimetracker.domain.color.model.AppColor

data class RecordType(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: AppColor,
    val defaultDuration: Long,
    val note: String,
    val hidden: Boolean = false,
    val timerPolicy: com.example.util.simpletimetracker.domain.rhythm.TimerPolicy = com.example.util.simpletimetracker.domain.rhythm.TimerPolicy(),
)
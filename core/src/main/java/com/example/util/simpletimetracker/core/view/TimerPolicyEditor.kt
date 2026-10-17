package com.example.util.simpletimetracker.core.view

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy

object TimerPolicyEditor {
    fun label(value: TimerPolicy?): String = when (value?.mode) {
        null -> "跟随活动设置"
        TimerPolicy.Mode.ORDINARY -> "自由记录"
        TimerPolicy.Mode.FOCUS_EXIT -> "专注退出轻提醒 · 15 分钟一段"
        TimerPolicy.Mode.INTERVAL -> "周期收尾提醒 · ${value.intervalMinutes} 分钟"
    }

    fun show(context: Context, current: TimerPolicy?, inherit: Boolean, changed: (TimerPolicy?) -> Unit) {
        val options = mutableListOf("自由记录", "专注退出轻提醒", "周期收尾提醒")
        if (inherit) options.add(0, "跟随活动设置")
        AlertDialog.Builder(context).setTitle("计时节奏 · 下次开始生效")
            .setItems(options.toTypedArray()) { _, index ->
                when (index - if (inherit) 1 else 0) {
                    -1 -> changed(null)
                    0 -> changed(TimerPolicy())
                    1 -> changed(TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT))
                    2 -> {
                        val input = EditText(context).apply {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            hint = "1—1440 分钟"
                            setText((current?.intervalMinutes?.takeIf { it > 0 } ?: 30).toString())
                        }
                        val dialog = AlertDialog.Builder(context).setTitle("每隔多少分钟提醒考虑收尾")
                            .setMessage("提醒不会停止计时，也不会阻止继续。")
                            .setView(input).setNegativeButton("取消", null).setPositiveButton("确定", null).create()
                        dialog.setOnShowListener {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                val minutes = input.text.toString().toIntOrNull()
                                if (minutes == null || minutes !in 1..1440) input.error = "请输入 1—1440 的整数"
                                else { changed(TimerPolicy(TimerPolicy.Mode.INTERVAL, minutes)); dialog.dismiss() }
                            }
                        }
                        dialog.show()
                    }
                }
            }.setNegativeButton("取消", null).show()
    }
}

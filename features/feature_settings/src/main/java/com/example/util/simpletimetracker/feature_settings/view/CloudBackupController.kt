package com.example.util.simpletimetracker.feature_settings.view

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.util.simpletimetracker.feature_settings.viewModel.CloudBackupViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudBackupController(private val fragment: Fragment, private val model: () -> CloudBackupViewModel) {
    private var progress: AlertDialog? = null
    private var menu: AlertDialog? = null
    fun bind(button: MaterialButton) {
        button.setOnClickListener { showMenu() }
        model().state.observe(fragment.viewLifecycleOwner) { state -> button.text = if (state.pending) "手动云备份 · 有待重试上传" else "手动云备份与电脑查看" }
        model().busy.observe(fragment.viewLifecycleOwner) { busy ->
            button.isEnabled = !busy
            if (busy) {
                menu?.dismiss()
                if (progress?.isShowing != true) {
                    progress = MaterialAlertDialogBuilder(fragment.requireContext())
                        .setMessage("正在制作、验证并上传完整备份…\n手机记录照常保存在本地，失败不会替换服务器已有备份。")
                        .setCancelable(false).show()
                }
            } else {
                progress?.dismiss()
                progress = null
            }
        }
        model().message.observe(fragment.viewLifecycleOwner) { Toast.makeText(fragment.requireContext(), it, Toast.LENGTH_LONG).show() }
        fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                menu?.dismiss()
                progress?.dismiss()
                menu = null
                progress = null
            }
        })
        model().refresh()
    }
    private fun showMenu() {
        val state = model().state.value ?: return
        val body = LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        val date = state.lastSuccess?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "尚未成功备份"
        body.addView(TextView(fragment.requireContext()).apply { text = "服务器：${state.serverUrl.ifBlank { "未配置" }}\n最近成功备份的记录截止：$date\n电脑只读，使用独立只读凭据。" })
        fun action(text: String, block: () -> Unit) = MaterialButton(fragment.requireContext()).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { block() }
            body.addView(this)
        }
        action(if (state.pending) "重试上次未完成的上传" else "立即备份当前记录（含照片）") { model().upload() }
        action("使用 Codex 只读分析") {
            MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("让 Codex 帮你回顾")
                .setMessage("先手动备份，再在电脑 Codex 中查询。建议只在 Codex 查看，由你决定是否调整计划。\n\n最近成功备份的记录截止：$date\nContext：2026-10-17-v3.2\n内置：精力管理／时间贫困／稀缺，原笔记与已确认产品原则分开。\n\nAI 使用独立只读凭据，只能查询文字、评分、恢复感和计划；不能修改记录、读取照片或原始恢复包。原笔记不是执行指令，也不自动代表已核实的科学结论。\n\n没有填写的评分和恢复感会保持为空。每次分析固定一份备份，并说明数据截至时间。")
                .setPositiveButton("知道了", null).show()
        }
        action("配置服务器与写入凭据") {
            menu?.dismiss()
            configure(state.serverUrl)
        }
        if (state.pending) {
            body.addView(TextView(fragment.requireContext()).apply { text = "重试继续上次冻结的快照，不自动混入之后的记录。要备份最新记录，可放弃旧上传后重新备份。" })
            action("放弃未完成上传") {
                MaterialAlertDialogBuilder(fragment.requireContext()).setMessage("只放弃这次上传任务，本地记录、照片和服务器完整备份会保留。")
                    .setNegativeButton("取消", null).setPositiveButton("放弃这次上传") { _, _ -> model().discard() }.show()
            }
        }
        menu = MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("手动云备份与电脑查看").setView(body).setNegativeButton("关闭", null).show()
    }
    private fun configure(current: String) {
        val body = LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        fun input(label: String, password: Boolean): EditText {
            body.addView(TextView(fragment.requireContext()).apply { text = label })
            return EditText(fragment.requireContext()).apply {
                inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                body.addView(this)
            }
        }
        val url = input("服务器地址（HTTPS）", false).apply {
            id = com.example.util.simpletimetracker.feature_settings.R.id.cloudServerUrl
            setText(current.ifBlank { "https://124.221.50.129" })
        }
        val token = input("手机写入凭据（不会出现在完整快照中）", true).apply { id = com.example.util.simpletimetracker.feature_settings.R.id.cloudWriteCredential }
        val dialog = MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("连接设置").setView(body).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (url.text.isBlank() || token.text.isBlank()) {
                    token.error = "请填写地址和写入凭据，或取消。"
                } else {
                    val value = token.text.toString().trim()
                    token.text.clear()
                    model().configure(url.text.toString(), value)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}

package com.example.util.simpletimetracker.feature_settings.view

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.util.simpletimetracker.feature_settings.R
import com.example.util.simpletimetracker.feature_settings.viewModel.FullBackupViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class FullBackupController(private val fragment: Fragment, private val model: () -> FullBackupViewModel) {
    private var exportKind = "zip"
    private var progress: AlertDialog? = null
    private val zip = fragment.registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { model().export(it.toString(), exportKind) } }
    private val csv = fragment.registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { model().export(it.toString(), "csv") } }
    private val ics = fragment.registerForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri -> uri?.let { model().export(it.toString(), "ics") } }
    private val restore = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model().prepare(it.toString()) } }

    fun restoreState(state: Bundle?) {
        exportKind = state?.getString("full_backup_export_kind") ?: "zip"
    }
    fun saveState(state: Bundle) {
        state.putString("full_backup_export_kind", exportKind)
    }

    fun bind(button: MaterialButton) {
        button.setOnClickListener { showMenu() }
        model().busy.observe(fragment.viewLifecycleOwner) { busy ->
            button.isEnabled = busy != true
            if (busy == true) {
                if (progress?.isShowing != true) progress = MaterialAlertDialogBuilder(fragment.requireContext()).setMessage(R.string.life_clock_backup_progress).setCancelable(false).show()
            } else {
                progress?.dismiss()
                progress = null
            }
        }
        model().message.observe(fragment.viewLifecycleOwner) { Toast.makeText(fragment.requireContext(), it, Toast.LENGTH_LONG).show() }
        model().preview.observe(fragment.viewLifecycleOwner) { preview ->
            var decided = false
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(preview.createdAt))
            val dialog = MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(R.string.life_clock_restore_full)
                .setMessage(fragment.getString(R.string.life_clock_restore_preview, date, preview.recordCount, preview.runningCount, preview.photoCount, preview.reviewCount, preview.repairCount))
                .setPositiveButton(R.string.life_clock_restore_apply) { _, _ ->
                    decided = true
                    model().restore(preview.token)
                }
                .setNegativeButton(R.string.cancel, null).create()
            dialog.setOnDismissListener { if (!decided) model().discard(preview.token) }
            dialog.show()
        }
        model().restored.observe(fragment.viewLifecycleOwner) { fragment.requireActivity().recreate() }
    }

    private fun showMenu() {
        val items = intArrayOf(R.string.life_clock_export_full, R.string.life_clock_restore_full, R.string.life_clock_export_full_csv, R.string.life_clock_export_full_ics, R.string.life_clock_recovery_copy)
        MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(R.string.life_clock_full_backup)
            .setItems(items.map(fragment::getString).toTypedArray()) { _, index ->
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
                try {
                    when (index) {
                        0 -> {
                            exportKind = "zip"
                            zip.launch("life-clock_$stamp.zip")
                        }
                        1 -> restore.launch(arrayOf("application/zip", "application/octet-stream"))
                        2 -> csv.launch("life-clock_$stamp.csv")
                        3 -> ics.launch("life-clock_$stamp.ics")
                        4 -> fragment.viewLifecycleOwner.lifecycleScope.launch {
                            if (model().hasRecoveryCopy()) {
                                exportKind = "recovery"
                                zip.launch("life-clock_before-restore_$stamp.zip")
                            } else {
                                Toast.makeText(fragment.requireContext(), R.string.life_clock_no_recovery_copy, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(fragment.requireContext(), R.string.life_clock_backup_io, Toast.LENGTH_LONG).show()
                }
            }.show()
    }
}

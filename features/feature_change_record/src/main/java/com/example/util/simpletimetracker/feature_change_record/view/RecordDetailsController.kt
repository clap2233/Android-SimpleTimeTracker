package com.example.util.simpletimetracker.feature_change_record.view

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.util.simpletimetracker.domain.record.model.RecordPhoto
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTagPurpose
import com.example.util.simpletimetracker.feature_change_record.R
import com.example.util.simpletimetracker.feature_change_record.viewModel.ChangeRecordBaseViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import com.example.util.simpletimetracker.feature_change_record.databinding.LifeClockExperienceEntryBinding

class RecordDetailsController(
    private val fragment: Fragment,
    private val model: () -> ChangeRecordBaseViewModel,
) {
    private var cameraPath: String? = null
    private var entryRefresh: Job? = null
    private val selectPhotos = fragment.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model().importDetailPhotos(uris.map { it.toString() })
    }
    private val takePhoto = fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraPath?.let(::File)
        cameraPath = null
        if (success && file != null) {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                model().importDetailPhoto(android.net.Uri.fromFile(file).toString()).join()
                withContext(Dispatchers.IO) { file.delete() }
            }
        } else {
            file?.delete()
        }
    }

    fun saveState(state: Bundle) {
        state.putString("life_clock_camera_path", cameraPath)
    }
    fun restoreState(state: Bundle?) {
        cameraPath = state?.getString("life_clock_camera_path")
    }

    fun bind(entry: LifeClockExperienceEntryBinding) {
        val vm = model()
        val draft = vm.detailDraft
        val configuration = fragment.resources.configuration
        if (configuration.screenWidthDp < 360 || configuration.fontScale > 1.3f) {
            val inset = (8 * fragment.resources.displayMetrics.density).toInt()
            listOf(entry.lifeClockHappinessButton, entry.lifeClockMeaningButton).forEach {
                it.setPadding(inset, it.paddingTop, inset, it.paddingBottom)
            }
        }
        var catalog = emptyList<com.example.util.simpletimetracker.domain.recordTag.model.RecordTag>()
        fun render() {
            fun score(id: Long?) = vm.detailValues().firstOrNull { it.tagId == id }?.numericValue?.toInt()?.toString() ?: fragment.getString(R.string.life_clock_unrated)
            entry.lifeClockHappinessButton.text = fragment.getString(R.string.life_clock_happiness_entry_value, score(draft.identity?.happinessTagId))
            entry.lifeClockMeaningButton.text = fragment.getString(R.string.life_clock_meaning_entry_value, score(draft.identity?.meaningTagId))
            val count = draft.photos.value.orEmpty().size
            entry.lifeClockPhotosButton.text = if (count == 0) fragment.getString(R.string.life_clock_photo_entry) else fragment.getString(R.string.life_clock_photo_count, count)
            val selected = vm.detailValues().map { it.tagId }.toSet()
            fun group(purpose: RecordTagPurpose) = catalog.filter { it.id in selected && it.purpose == purpose }.joinToString("、") { it.name }.ifEmpty { "未选择" }
            entry.lifeClockPeopleButton.text = "人物 · ${group(RecordTagPurpose.PERSON)}"
            entry.lifeClockEnvironmentButton.text = "环境 · ${group(RecordTagPurpose.ENVIRONMENT)}"
            if (entry.lifeClockSummaryInput.text.toString() != draft.summary) entry.lifeClockSummaryInput.setText(draft.summary)
        }
        fun refreshCatalog() {
            entryRefresh?.cancel()
            entryRefresh = fragment.viewLifecycleOwner.lifecycleScope.launch {
                catalog = vm.detailTags()
                render()
            }
        }
        draft.ready.observe(fragment.viewLifecycleOwner) { ready ->
            listOf(entry.lifeClockHappinessButton, entry.lifeClockMeaningButton, entry.lifeClockPhotosButton, entry.lifeClockPeopleButton, entry.lifeClockEnvironmentButton, entry.lifeClockSummaryInput).forEach { it.isEnabled = ready == true }
            render()
            if (ready == true) refreshCatalog()
        }
        vm.detailSelection.observe(fragment.viewLifecycleOwner) {
            render()
            refreshCatalog()
        }
        draft.photos.observe(fragment.viewLifecycleOwner) { render() }
        draft.summaryChanges.observe(fragment.viewLifecycleOwner) { render() }
        draft.error.observe(fragment.viewLifecycleOwner) { message -> toast(message) }
        entry.lifeClockHappinessButton.setOnClickListener { chooseScore(false) }
        entry.lifeClockMeaningButton.setOnClickListener { chooseScore(true) }
        entry.lifeClockPhotosButton.setOnClickListener { photoMenu() }
        entry.lifeClockPeopleButton.setOnClickListener { chooseGroup(RecordTagPurpose.PERSON) }
        entry.lifeClockEnvironmentButton.setOnClickListener { chooseGroup(RecordTagPurpose.ENVIRONMENT) }
        entry.lifeClockSummaryInput.doAfterTextChanged { draft.summary = it?.toString().orEmpty() }
    }

    private fun chooseScore(meaning: Boolean) {
        val vm = model()
        val identity = vm.detailDraft.identity ?: return
        val id = if (meaning) identity.meaningTagId else identity.happinessTagId
        val items = arrayOf(fragment.getString(R.string.life_clock_empty), "1", "2", "3", "4", "5")
        val current = vm.detailValues().firstOrNull { it.tagId == id }?.numericValue?.toInt() ?: 0
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(if (meaning) R.string.life_clock_meaning else R.string.life_clock_happiness)
            .setSingleChoiceItems(items, current) { dialog, index ->
                vm.setDetailScore(id, index.takeIf { it > 0 })
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun chooseGroup(purpose: RecordTagPurpose) = fragment.viewLifecycleOwner.lifecycleScope.launch {
        val vm = model()
        val tags = vm.detailTags().filter { it.purpose == purpose && (!it.archived || vm.detailValues().any { value -> value.tagId == it.id }) }
        val selected = tags.filter { tag -> vm.detailValues().any { it.tagId == tag.id } }.map { it.id }.toMutableSet()
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(if (purpose == RecordTagPurpose.PERSON) R.string.life_clock_people else R.string.life_clock_environment)
            .setMultiChoiceItems(tags.map { it.name }.toTypedArray(), tags.map { it.id in selected }.toBooleanArray()) { _, index, checked ->
                if (checked) selected.add(tags[index].id) else selected.remove(tags[index].id)
            }.setPositiveButton(R.string.ok) { _, _ -> fragment.viewLifecycleOwner.lifecycleScope.launch { vm.setDetailGroup(purpose, selected) } }
            .setNeutralButton(R.string.life_clock_add) { _, _ -> addTag(purpose) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun addTag(purpose: RecordTagPurpose) {
        val input = EditText(fragment.requireContext())
        val dialog = MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(R.string.life_clock_add)
            .setView(input).setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.isNullOrBlank()) {
                    input.error = fragment.getString(R.string.life_clock_name_required)
                } else {
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        model().createDetailTag(input.text.toString(), purpose)
                        dialog.dismiss()
                        chooseGroup(purpose)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun photoMenu() {
        if (model().detailDraft.busy.value == true) {
            toast(R.string.life_clock_photo_busy)
            return
        }
        val choices = arrayOf(fragment.getString(R.string.life_clock_select_photo), fragment.getString(R.string.life_clock_take_photo), fragment.getString(R.string.life_clock_view_photos))
        MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(R.string.life_clock_photos)
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> try {
                        selectPhotos.launch(arrayOf("image/*"))
                    } catch (_: ActivityNotFoundException) {
                        toast(R.string.life_clock_photo_import_failed)
                    }
                    1 -> launchCamera()
                    else -> showPhotos()
                }
            }.show()
    }

    private fun launchCamera() {
        try {
            val context = fragment.requireContext()
            val file = File.createTempFile("life-clock-camera-", ".jpg", context.cacheDir)
            cameraPath = file.absolutePath
            takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
        } catch (_: ActivityNotFoundException) {
            cameraPath?.let(::File)?.delete()
            cameraPath = null
            toast(R.string.life_clock_photo_import_failed)
        } catch (_: IOException) {
            toast(R.string.life_clock_photo_import_failed)
        } catch (_: SecurityException) {
            toast(R.string.life_clock_photo_import_failed)
        }
    }

    private fun showPhotos() {
        val photos = model().detailDraft.photos.value.orEmpty()
        if (photos.isEmpty()) {
            toast(R.string.life_clock_no_photos)
            return
        }
        MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(R.string.life_clock_photos)
            .setItems(photos.indices.map { fragment.getString(R.string.life_clock_photo_number, it + 1) }.toTypedArray()) { _, index -> showPhoto(photos[index]) }.show()
    }

    private fun showPhoto(photo: RecordPhoto) = fragment.viewLifecycleOwner.lifecycleScope.launch {
        val image = ImageView(fragment.requireContext()).apply {
            adjustViewBounds = true
            contentDescription = fragment.getString(R.string.life_clock_photos)
        }
        val bitmap = withContext(Dispatchers.IO) {
            val path = model().detailDraft.photoRepo.path(photo)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = 1
            while (options.outWidth / options.inSampleSize > 1600 || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            val decoded = BitmapFactory.decodeFile(path, options)
            val orientation = runCatching { android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = android.graphics.Matrix()
            when (orientation) {
                2 -> matrix.setScale(-1f, 1f)
                3 -> matrix.setRotate(180f)
                4 -> matrix.setScale(1f, -1f)
                5 -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                6 -> matrix.setRotate(90f)
                7 -> {
                    matrix.setRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                8 -> matrix.setRotate(270f)
            }
            if (decoded != null && !matrix.isIdentity) {
                android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }
        }
        if (bitmap == null) {
            toast(R.string.life_clock_photo_missing)
            return@launch
        }
        image.setImageBitmap(bitmap)
        MaterialAlertDialogBuilder(fragment.requireContext()).setView(image).setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.life_clock_remove_photo) { _, _ -> model().detailDraft.remove(photo) }.show()
    }

    private fun toast(message: Int) = Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
}

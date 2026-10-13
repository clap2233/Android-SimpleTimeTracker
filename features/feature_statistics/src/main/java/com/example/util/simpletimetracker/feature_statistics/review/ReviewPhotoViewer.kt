package com.example.util.simpletimetracker.feature_statistics.review

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.util.simpletimetracker.domain.record.repo.RecordPhotoRepo
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewPhotoViewer @Inject constructor(private val photos: RecordPhotoRepo) {
    fun show(fragment: Fragment, recordId: Long) = fragment.viewLifecycleOwner.lifecycleScope.launch {
        val owner = fragment.viewLifecycleOwner
        val context = fragment.requireContext()
        val items = photos.get(recordId, false)
        if (items.isEmpty()) {
            Toast.makeText(context, "这条经历没有照片", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val image = ImageView(context).apply {
            adjustViewBounds = true
            contentDescription = "经历照片"
        }
        var index = 0
        val dialog = AlertDialog.Builder(context).setView(image).setNegativeButton("关闭", null)
            .setPositiveButton("下一张", null).create()
        var displaying = false
        fun display() = owner.lifecycleScope.launch {
            if (displaying) return@launch
            displaying = true
            try {
                val path = photos.path(items[index])
                val bitmap = withContext(Dispatchers.IO) { decode(path) }
                if (bitmap == null) Toast.makeText(context, "照片副本不可读取", Toast.LENGTH_SHORT).show()
                image.setImageBitmap(bitmap)
                dialog.setTitle("照片 ${index + 1} / ${items.size}")
            } finally {
                displaying = false
            }
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dialog.dismiss()
            }
        }
        owner.lifecycle.addObserver(observer)
        dialog.setOnDismissListener { owner.lifecycle.removeObserver(observer) }
        dialog.setOnShowListener {
            display()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!displaying) {
                    index = (index + 1) % items.size
                    display()
                }
            }
        }
        dialog.show()
    }

    private fun decode(path: String): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = 1
        while (options.outWidth / options.inSampleSize > 1600 || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val orientation = runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix()
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
        return if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        }
    }
}

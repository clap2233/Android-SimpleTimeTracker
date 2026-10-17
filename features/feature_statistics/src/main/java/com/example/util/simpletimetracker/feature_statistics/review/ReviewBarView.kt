package com.example.util.simpletimetracker.feature_statistics.review

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class ReviewBarView(
    context: Context,
    private val fraction: Float,
    private val fill: Int,
    private val track: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = height / 2f
        paint.color = track
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        if (fraction > 0) {
            paint.color = fill
            canvas.drawRoundRect(0f, 0f, width * fraction.coerceIn(0f, 1f), height.toFloat(), radius, radius, paint)
        }
    }
}

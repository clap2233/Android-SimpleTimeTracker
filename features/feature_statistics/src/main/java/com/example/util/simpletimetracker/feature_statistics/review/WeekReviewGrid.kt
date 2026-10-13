package com.example.util.simpletimetracker.feature_statistics.review

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.util.simpletimetracker.domain.review.interactor.ReviewTimeBlock
import com.example.util.simpletimetracker.domain.review.interactor.ReviewTimeState
import com.example.util.simpletimetracker.domain.review.interactor.WeekReviewStats
import java.util.Calendar
import java.util.TimeZone

class WeekReviewGrid(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hits = mutableListOf<Pair<RectF, ReviewTimeBlock>>()
    var stats: WeekReviewStats? = null
    var zoneId = TimeZone.getDefault().id
    var colors: Map<Long, Int> = emptyMap()
    var textColor: Int = 0xff555555.toInt()
    var onBlock: (ReviewTimeBlock) -> Unit = {}
    var onDay: (Int) -> Unit = {}
    var onGap: (Int, Int) -> Unit = { _, _ -> }
    private var selected: ReviewTimeBlock? = null
    private val density get() = resources.displayMetrics.density

    init {
        isClickable = true
        contentDescription = "完整七天、00:00 至 24:00 周视图；也可通过本周经历按钮逐条查看。"
        minimumHeight = (630 * density).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = stats ?: return
        hits.clear()
        val left = 34 * density
        val top = 32 * density
        val column = (width - left) / 7
        val hour = (height - top - 18 * density) / 24
        val cutoff = data.days.first().start + data.elapsed
        val futureCalendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId))
        paint.color = textColor
        paint.alpha = 15
        paint.style = Paint.Style.FILL
        data.days.forEachIndexed { index, day ->
            if (cutoff < day.end) {
                futureCalendar.timeInMillis = cutoff
                val minute = if (cutoff <= day.start) 0f else futureCalendar.get(Calendar.HOUR_OF_DAY) * 60f + futureCalendar.get(Calendar.MINUTE)
                canvas.drawRect(left + index * column, top + minute / 60 * hour, left + (index + 1) * column, top + 24 * hour, paint)
            }
        }
        paint.textSize = 10 * density
        paint.color = textColor
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        for (i in 0..24) {
            val y = top + i * hour
            canvas.drawText("%02d".format(i), 0f, y + 3 * density, paint)
            paint.alpha = 35
            canvas.drawLine(left, y, width.toFloat(), y, paint)
            paint.alpha = 255
        }
        data.days.forEachIndexed { i, day ->
            canvas.drawText(day.date.substring(5), left + i * column + 2 * density, 14 * density, paint)
            if (day.end - day.start != 86_400_000L) canvas.drawText("DST", left + i * column, 27 * density, paint)
            paint.alpha = 35
            canvas.drawLine(left + i * column, top, left + i * column, top + 24 * hour, paint)
            paint.alpha = 255
        }
        val zone = TimeZone.getTimeZone(zoneId)
        val calendar = Calendar.getInstance(zone)
        fun minute(timestamp: Long): Float {
            calendar.timeInMillis = timestamp
            return calendar.get(Calendar.HOUR_OF_DAY) * 60f + calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) / 60f + calendar.get(Calendar.MILLISECOND) / 60_000f
        }
        data.blocks.forEach { block ->
            val day = data.days[block.day]
            // Split at offset transitions so repeated wall-clock hours never reverse a rectangle.
            val ranges = mutableListOf<Pair<Long, Long>>()
            var part = block.start
            var cursor = part
            while (cursor < block.end) {
                val next = minOf(cursor + 60_000, block.end)
                if (zone.getOffset(cursor) != zone.getOffset(next - 1)) {
                    var low = cursor
                    var high = next
                    while (high - low > 1) {
                        val mid = low + (high - low) / 2
                        if (zone.getOffset(mid) == zone.getOffset(cursor)) low = mid else high = mid
                    }
                    ranges.add(part to high)
                    part = high
                } else if (next < block.end && zone.getOffset(cursor) != zone.getOffset(next)) {
                    ranges.add(part to next)
                    part = next
                }
                cursor = next
            }
            ranges.add(part to block.end)
            ranges.filter { it.first < it.second }.forEach { (from, to) ->
                val a = minute(from)
                val b = if (to == day.end) 1440f else minute(to - 1) + 1f / 60_000
                val repeated = day.end - day.start > 86_400_000L
                val laneWidth = if (repeated) column / 2 else column
                val lane = if (repeated && zone.getOffset(from) != zone.getOffset(day.start)) laneWidth else 0f
                val x = left + block.day * column + lane + density
                val y = top + a / 60 * hour
                val rect = RectF(x, y, x + laneWidth - 2 * density, maxOf(y + density, top + b / 60 * hour))
                paint.color = colors[block.typeId] ?: 0xffb0b0b0.toInt()
                paint.alpha = if (block.state == ReviewTimeState.PENDING) 130 else 255
                paint.style = Paint.Style.FILL
                canvas.drawRect(rect, paint)
                if (block.state != ReviewTimeState.CONFIRMED) {
                    paint.color = textColor
                    paint.alpha = 255
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = density
                    paint.pathEffect = if (block.state == ReviewTimeState.PENDING) android.graphics.DashPathEffect(floatArrayOf(3 * density, 2 * density), 0f) else null
                    canvas.drawRect(rect, paint)
                    paint.pathEffect = null
                }
                hits.add(rect to block)
            }
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.action == MotionEvent.ACTION_UP) {
            selected = hits.lastOrNull { it.first.contains(event.x, event.y) }?.second
            if (selected != null) {
                performClick()
            } else {
                val left = 34 * density
                val top = 32 * density
                val day = ((event.x - left) / ((width - left) / 7)).toInt().coerceIn(0, 6)
                if (event.y < top) {
                    onDay(day)
                } else {
                    onGap(day, ((event.y - top) / (height - top - 18 * density) * 1440).toInt().coerceIn(0, 1439))
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        selected?.let(onBlock)
        return true
    }
}

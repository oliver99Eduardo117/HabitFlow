package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

enum class StripDay { DONE, MISSED, REST, TODAY_PENDING }
data class StripDayState(val label: String, val state: StripDay, val isToday: Boolean)

object WidgetBitmapUtils {

    fun createSegmentedRingBitmap(
        segmentColors: List<Int>,
        sizePx: Int,
        strokeWidthPx: Float,
        trackColorInt: Int = 0xFF334155.toInt(),
        visibleGapDegrees: Float = 6f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokeWidthPx / 2f + 1f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }
        val n = segmentColors.size
        if (n <= 1) {
            paint.color = segmentColors.firstOrNull() ?: trackColorInt
            canvas.drawOval(rect, paint)
            return bitmap
        }
        val sweepPer = 360f / n
        val radius = rect.width() / 2f
        val capDegrees = Math.toDegrees((strokeWidthPx / 2f / radius).toDouble()).toFloat()
        var gap = 2f * capDegrees + visibleGapDegrees
        if (gap > sweepPer * 0.6f) {
            paint.strokeCap = Paint.Cap.BUTT
            gap = if (n > 30) 0f else 3f
        }
        segmentColors.forEachIndexed { i, color ->
            paint.color = color
            canvas.drawArc(rect, -90f + i * sweepPer + gap / 2f, sweepPer - gap, false, paint)
        }
        return bitmap
    }

    fun createAvatarRingBitmap(
        fraction: Float,
        sizePx: Int,
        strokeWidthPx: Float,
        progressColorInt: Int,
        trackColorInt: Int = 0xFF334155.toInt()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokeWidthPx / 2f + 0.5f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = trackColorInt
        }
        canvas.drawOval(rect, paint)
        paint.color = progressColorInt
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
        return bitmap
    }

    fun createWeekStripBitmap(
        days: List<StripDayState>,
        widthPx: Int,
        density: Float,
        accentColorInt: Int = 0xFFF59E0B.toInt(),
        missedColorInt: Int = 0xFF334155.toInt(),
        restColorInt: Int = 0xFF475569.toInt(),
        labelColorInt: Int = 0xFF94A3B8.toInt(),
        todayLabelColorInt: Int = 0xFFF1F5F9.toInt()
    ): Bitmap {
        val heightPx = (30f * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        if (days.isEmpty()) return bitmap
        val canvas = Canvas(bitmap)
        val slot = widthPx / days.size.toFloat()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelBaseline = 10f * density
        val dotCenterY = heightPx - 7f * density
        days.forEachIndexed { i, day ->
            val cx = slot * (i + 0.5f)
            labelPaint.color = if (day.isToday) todayLabelColorInt else labelColorInt
            labelPaint.typeface = if (day.isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(day.label, cx, labelBaseline, labelPaint)
            when (day.state) {
                StripDay.DONE -> {
                    dotPaint.style = Paint.Style.FILL; dotPaint.color = accentColorInt
                    canvas.drawCircle(cx, dotCenterY, 7f * density, dotPaint)
                }
                StripDay.MISSED -> {
                    dotPaint.style = Paint.Style.FILL; dotPaint.color = missedColorInt
                    canvas.drawCircle(cx, dotCenterY, 7f * density, dotPaint)
                }
                StripDay.REST -> {
                    dotPaint.style = Paint.Style.FILL; dotPaint.color = restColorInt
                    canvas.drawCircle(cx, dotCenterY, 3f * density, dotPaint)
                }
                StripDay.TODAY_PENDING -> {
                    dotPaint.style = Paint.Style.STROKE; dotPaint.strokeWidth = 2f * density
                    dotPaint.color = accentColorInt
                    canvas.drawCircle(cx, dotCenterY, 6f * density, dotPaint)
                }
            }
        }
        return bitmap
    }

    fun createHeatmapGridBitmap(
        columns: Int,
        monthPositions: List<Pair<String, Int>>,
        cellSizePx: Float,
        gapPx: Float,
        monthLabelTextPx: Float,
        highlightCell: Pair<Int, Int>? = null,
        highlightColorInt: Int = 0xFFF1F5F9.toInt(),
        monthLabelColorInt: Int = 0xFF94A3B8.toInt(),
        cellColorProvider: (col: Int, row: Int) -> Int
    ): Bitmap {
        val cols = columns.coerceAtLeast(1)
        val headerPx = monthLabelTextPx * 1.4f
        val widthPx = (cols * cellSizePx + (cols - 1) * gapPx).toInt().coerceAtLeast(1)
        val heightPx = (headerPx + 7 * cellSizePx + 6 * gapPx).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = monthLabelColorInt
            textSize = monthLabelTextPx
            textAlign = Paint.Align.LEFT
        }
        monthPositions.forEach { (label, col) ->
            if (col in 0 until cols) {
                val x = col * (cellSizePx + gapPx)
                if (x + textPaint.measureText(label) <= widthPx) {
                    canvas.drawText(label, x, monthLabelTextPx, textPaint)
                }
            }
        }

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val corner = cellSizePx * 0.25f
        for (col in 0 until cols) {
            for (row in 0 until 7) {
                val color = cellColorProvider(col, row)
                if (android.graphics.Color.alpha(color) == 0) continue
                val left = col * (cellSizePx + gapPx)
                val top = headerPx + row * (cellSizePx + gapPx)
                cellPaint.color = color
                canvas.drawRoundRect(RectF(left, top, left + cellSizePx, top + cellSizePx), corner, corner, cellPaint)
            }
        }

        highlightCell?.let { (col, row) ->
            if (col in 0 until cols && row in 0 until 7) {
                val stroke = (cellSizePx * 0.15f).coerceAtLeast(2f)
                val left = col * (cellSizePx + gapPx) + stroke / 2f
                val top = headerPx + row * (cellSizePx + gapPx) + stroke / 2f
                val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = stroke
                    color = highlightColorInt
                }
                canvas.drawRoundRect(
                    RectF(left, top, left + cellSizePx - stroke, top + cellSizePx - stroke),
                    corner, corner, outline
                )
            }
        }
        return bitmap
    }
}


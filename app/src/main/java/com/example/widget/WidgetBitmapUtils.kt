package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color

object WidgetBitmapUtils {

    /**
     * Creates a smooth, crisp circular progress ring bitmap for Glance AppWidgets.
     * Replaces the indeterminate CircularProgressIndicator spinner with a determinate ring.
     */
    fun createProgressRingBitmap(
        percentage: Int,
        sizePx: Int = 180,
        strokeWidthPx: Float = 18f,
        trackColorInt: Int = 0xFF334155.toInt(),
        progressColorInt: Int = 0xFF6366F1.toInt(),
        completedColorInt: Int = 0xFF10B981.toInt()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val halfStroke = strokeWidthPx / 2f
        val rect = RectF(
            halfStroke + 4f,
            halfStroke + 4f,
            sizePx - halfStroke - 4f,
            sizePx - halfStroke - 4f
        )

        // Background track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = trackColorInt
        }
        canvas.drawOval(rect, trackPaint)

        // Progress arc
        val clampedPct = percentage.coerceIn(0, 100)
        if (clampedPct > 0) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                strokeCap = Paint.Cap.ROUND
                color = if (clampedPct >= 100) completedColorInt else progressColorInt
            }
            val sweepAngle = (clampedPct / 100f) * 360f
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }

        return bitmap
    }

    /**
     * Creates a crisp Heatmap Grid Bitmap for Glance AppWidgets.
     * Renders exact columns x rows with proportional spacing, corner radius and explicit 4-bucket color interpolation.
     */
    fun createHeatmapGridBitmap(
        dailyRatios: List<Float>, // chronological list of ratios (0f..1f)
        columns: Int,
        rows: Int,
        widthPx: Int = 400,
        heightPx: Int = 160,
        gapPx: Float = 6f,
        cornerRadiusPx: Float = 5f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val totalGapX = (columns - 1) * gapPx
        val totalGapY = (rows - 1) * gapPx
        val availableW = widthPx - totalGapX
        val availableH = heightPx - totalGapY
        val cellW = (availableW / columns).coerceAtLeast(4f)
        val cellH = (availableH / rows).coerceAtLeast(4f)
        val cellSize = minOf(cellW, cellH)

        // Center horizontally and vertically within the canvas
        val totalGridW = columns * cellSize + totalGapX
        val totalGridH = rows * cellSize + totalGapY
        val startX = (widthPx - totalGridW) / 2f
        val startY = (heightPx - totalGridH) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        var dataIndex = 0
        for (col in 0 until columns) {
            val left = startX + col * (cellSize + gapPx)
            for (row in 0 until rows) {
                val top = startY + row * (cellSize + gapPx)
                val ratio = if (dataIndex < dailyRatios.size) dailyRatios[dataIndex] else 0f
                dataIndex++

                val colorInt = WidgetColors.interpolateHeatmapColorInt(ratio)
                paint.color = colorInt
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            }
        }

        return bitmap
    }
}

package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

object WidgetBitmapUtils {

    /**
     * Creates a smooth circular progress ring bitmap for DailyProgressWidget.
     * Track: Color(0xFF334155) (DarkSurfaceVariant), Progress: #6366F1 (Indigo), StrokeCap.Round.
     */
    fun createProgressRingBitmap(
        percentage: Int,
        sizePx: Int = 210,
        strokeWidthPx: Float = 18f,
        trackColorInt: Int = 0xFF334155.toInt(),
        progressColorInt: Int = 0xFF6366F1.toInt(),
        completedColorInt: Int = 0xFF6366F1.toInt()
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
     * Single shared Heatmap Bitmap drawing engine for ConsistencyWidget and DashboardWidget.
     * Derives cell size, small proportional gaps (~13%), corner radius (~20%), month labels on top,
     * and day initials on the left from the available targetWidthPx and targetHeightPx.
     *
     * @param columns Total number of week columns (e.g. 10 or 14).
     * @param monthPositions List of Pair(monthLabel, weekIndex).
     * @param targetWidthPx Available width in pixels for drawing.
     * @param targetHeightPx Available height in pixels for drawing.
     * @param cellColorProvider Lambda deciding the resolved color Int for each cell (col, row).
     */
    fun createHeatmapBitmap(
        columns: Int,
        monthPositions: List<Pair<String, Int>>,
        targetWidthPx: Int = 500,
        targetHeightPx: Int = 200,
        cellColorProvider: (col: Int, row: Int) -> Int
    ): Bitmap {
        val cols = columns.coerceAtLeast(1)
        val rows = 7

        val dayLabelWidthPx = (targetWidthPx * 0.07f).coerceIn(18f, 26f)
        val monthHeaderHeightPx = (targetHeightPx * 0.18f).coerceIn(16f, 24f)

        val availW = (targetWidthPx - dayLabelWidthPx).coerceAtLeast(20f)
        val availH = (targetHeightPx - monthHeaderHeightPx).coerceAtLeast(20f)

        val gapRatio = 0.13f
        val cellSizeX = availW / (cols + (cols - 1) * gapRatio)
        val cellSizeY = availH / (rows + (rows - 1) * gapRatio)

        val cellSize = minOf(cellSizeX, cellSizeY).coerceAtLeast(4f)
        val gapPx = (cellSize * gapRatio).coerceAtLeast(1.5f)
        val cornerRadiusPx = (cellSize * 0.20f).coerceAtLeast(2f)

        val gridW = cols * cellSize + (cols - 1) * gapPx
        val gridH = rows * cellSize + (rows - 1) * gapPx

        val widthPx = (dayLabelWidthPx + gridW).toInt().coerceAtLeast(1)
        val heightPx = (monthHeaderHeightPx + gridH).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val startX = dayLabelWidthPx
        val startY = monthHeaderHeightPx

        // Draw month header text
        val monthTextSize = (monthHeaderHeightPx * 0.65f).coerceIn(10f, 16f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt() // Slate 400
            textSize = monthTextSize
            textAlign = Paint.Align.LEFT
        }

        monthPositions.forEach { (monthLabel, weekIdx) ->
            if (weekIdx in 0 until cols) {
                val posX = startX + weekIdx * (cellSize + gapPx)
                canvas.drawText(monthLabel, posX, monthHeaderHeightPx - (monthTextSize * 0.25f), textPaint)
            }
        }

        // Draw day initials (L, M, X, J, V, S, D) vertically centered to each row
        val dayTextSize = (cellSize * 0.65f).coerceIn(9f, 15f)
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = dayTextSize
            textAlign = Paint.Align.CENTER
        }
        val dayMetrics = dayPaint.fontMetrics
        val textCenterOffset = (dayMetrics.descent + dayMetrics.ascent) / 2f
        val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

        for (row in 0 until rows) {
            val cellCenterY = startY + row * (cellSize + gapPx) + (cellSize / 2f)
            val dayLetter = dayLetters.getOrNull(row) ?: ""
            canvas.drawText(dayLetter, dayLabelWidthPx / 2f, cellCenterY - textCenterOffset, dayPaint)
        }

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        for (col in 0 until cols) {
            val left = startX + col * (cellSize + gapPx)
            for (row in 0 until rows) {
                val top = startY + row * (cellSize + gapPx)
                cellPaint.color = cellColorProvider(col, row)
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, cellPaint)
            }
        }

        return bitmap
    }
}


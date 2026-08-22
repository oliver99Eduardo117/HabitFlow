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
     * Creates a crisp Heatmap Grid Bitmap for ConsistencyWidget.
     * 5 columns (weeks) x 7 rows (days per week, top-to-bottom chronological), 4 exact intensity levels.
     */
    fun createHeatmapGridBitmap(
        dailyRatios: List<Float>, // 35 chronological ratios (oldest to today)
        columns: Int = 5,
        rows: Int = 7,
        widthPx: Int = 420,
        heightPx: Int = 210,
        gapPx: Float = 6f,
        cornerRadiusPx: Float = 4f
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

        for (col in 0 until columns) {
            val left = startX + col * (cellSize + gapPx)
            for (row in 0 until rows) {
                val top = startY + row * (cellSize + gapPx)
                val dataIndex = col * rows + row
                val ratio = if (dataIndex < dailyRatios.size) dailyRatios[dataIndex] else 0f

                // Exact 4 levels: 0%, 1-33%, 34-66%, 67-100%
                paint.color = WidgetColors.getHeatmapColorInt(ratio)
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            }
        }

        return bitmap
    }

    /**
     * Creates an aggregated multi-habit 20-week x 7-day Heatmap Bitmap with month headers on top.
     * ratioMatrix: 20 columns (weeks) x 7 rows (days per week, Mon to Sun), with completion ratio 0.0f..1.0f.
     * monthPositions: List of Pair(monthLabel, weekIndex)
     */
    fun createAggregatedHeatmapBitmap(
        ratioMatrix: List<List<Float>>,
        monthPositions: List<Pair<String, Int>>,
        widthPx: Int = 900,
        heightPx: Int = 400
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val columns = ratioMatrix.size.coerceAtLeast(1)
        val rows = 7

        val monthHeaderHeightPx = 36f
        val gapPx = 6f
        val cornerRadiusPx = 5f

        val totalGapX = (columns - 1) * gapPx
        val totalGapY = (rows - 1) * gapPx
        val availableW = widthPx.toFloat() - totalGapX
        val availableH = (heightPx.toFloat() - monthHeaderHeightPx) - totalGapY

        val cellW = availableW / columns
        val cellH = availableH / rows
        val cellSize = minOf(cellW, cellH).coerceAtLeast(4f)

        val gridW = columns * cellSize + totalGapX
        val gridH = rows * cellSize + totalGapY

        val startX = (widthPx - gridW) / 2f
        val startY = monthHeaderHeightPx + (heightPx - monthHeaderHeightPx - gridH) / 2f

        // Draw month header text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt() // Slate 400
            textSize = 24f
            textAlign = Paint.Align.LEFT
        }

        monthPositions.forEach { (monthLabel, weekIdx) ->
            if (weekIdx in 0 until columns) {
                val posX = startX + weekIdx * (cellSize + gapPx)
                canvas.drawText(monthLabel, posX, monthHeaderHeightPx - 8f, textPaint)
            }
        }

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        for (col in 0 until columns) {
            val left = startX + col * (cellSize + gapPx)
            val weekRatios = ratioMatrix.getOrNull(col) ?: emptyList()
            for (row in 0 until rows) {
                val top = startY + row * (cellSize + gapPx)
                val ratio = weekRatios.getOrNull(row) ?: 0f
                cellPaint.color = WidgetColors.getHeatmapColorInt(ratio)
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, cellPaint)
            }
        }

        return bitmap
    }

    /**
     * Creates a single-habit Heatmap Bitmap with month headers on top and day initials on the left.
     * dateMatrix: columns (weeks) x 7 rows (days per week, Mon to Sun).
     * completedDates: Set of "yyyy-MM-dd" strings completed for this habit.
     * habitColorInt: parsed Color int for completed cells.
     * monthPositions: List of Pair(monthLabel, weekIndex)
     */
    fun createHabitHeatmapBitmap(
        dateMatrix: List<List<String>>,
        completedDates: Set<String>,
        habitColorInt: Int,
        monthPositions: List<Pair<String, Int>>
    ): Bitmap {
        val columns = dateMatrix.size.coerceAtLeast(1)
        val rows = 7

        val cellSize = 22f
        val gapPx = 5f
        val cornerRadiusPx = 4f
        val dayLabelWidthPx = 26f
        val monthHeaderHeightPx = 24f

        val gridW = columns * cellSize + (columns - 1) * gapPx
        val gridH = rows * cellSize + (rows - 1) * gapPx

        val widthPx = (dayLabelWidthPx + gridW).toInt()
        val heightPx = (monthHeaderHeightPx + gridH).toInt()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val startX = dayLabelWidthPx
        val startY = monthHeaderHeightPx

        // Draw month header text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt() // Slate 400
            textSize = 16f
            textAlign = Paint.Align.LEFT
        }

        monthPositions.forEach { (monthLabel, weekIdx) ->
            if (weekIdx in 0 until columns) {
                val posX = startX + weekIdx * (cellSize + gapPx)
                canvas.drawText(monthLabel, posX, monthHeaderHeightPx - 6f, textPaint)
            }
        }

        // Draw day initials (L, M, X, J, V, S, D) vertically centered to each row
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 14f
            textAlign = Paint.Align.CENTER
        }
        val dayMetrics = dayPaint.fontMetrics
        val textCenterOffset = (dayMetrics.descent + dayMetrics.ascent) / 2f
        val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

        for (row in 0 until rows) {
            val cellCenterY = startY + row * (cellSize + gapPx) + (cellSize / 2f)
            val dayLetter = dayLetters.getOrNull(row) ?: ""
            canvas.drawText(dayLetter, dayLabelWidthPx / 2f - 2f, cellCenterY - textCenterOffset, dayPaint)
        }

        // Draw cells: habitColorInt when completed, 0x38334155 (22% opacity #334155) when not
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val uncompletedColorInt = 0x38334155

        for (col in 0 until columns) {
            val left = startX + col * (cellSize + gapPx)
            val week = dateMatrix.getOrNull(col) ?: emptyList()
            for (row in 0 until rows) {
                val top = startY + row * (cellSize + gapPx)
                val dateStr = week.getOrNull(row)
                val isCompleted = dateStr != null && completedDates.contains(dateStr)

                cellPaint.color = if (isCompleted) habitColorInt else uncompletedColorInt
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, cellPaint)
            }
        }

        return bitmap
    }
}


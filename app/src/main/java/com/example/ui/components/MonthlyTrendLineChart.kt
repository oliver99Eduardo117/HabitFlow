package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.util.DateUtils
import com.example.util.MonthlyTrendDataPoint
import com.example.util.MonthlyTrendStats
import java.util.Calendar

/**
 * High-fidelity, interactive Monthly Compliance Trend Line Chart
 * built with Jetpack Compose Canvas, smooth Bézier cubic spline curves,
 * dynamic gradient fill, and touch-interactive scrubbing tooltips.
 */
@Composable
fun MonthlyTrendLineChart(
    habits: List<Habit>,
    allLogs: List<HabitLog>,
    modifier: Modifier = Modifier
) {
    // Current selected calendar month
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedHabitFilterId by remember { mutableStateOf<Long?>(null) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val todayCalendar = remember { Calendar.getInstance() }
    val isCurrentMonth = remember(selectedCalendar) {
        selectedCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                selectedCalendar.get(Calendar.MONTH) == todayCalendar.get(Calendar.MONTH)
    }

    val monthlyStats = remember(habits, allLogs, selectedCalendar, selectedHabitFilterId) {
        DateUtils.calculateMonthlyTrend(
            habits = habits,
            allLogs = allLogs,
            calendar = selectedCalendar,
            filterHabitId = selectedHabitFilterId
        )
    }

    // Animation progress when switching month or filter
    var animationKey by remember { mutableStateOf(0) }
    LaunchedEffect(selectedCalendar.timeInMillis, selectedHabitFilterId) {
        animationKey++
        selectedPointIndex = null
    }

    val chartAnimationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "line_chart_anim_$animationKey"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_trend_line_chart"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Month Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tendencia Mensual de Hábitos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = monthlyStats.monthName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Month Navigator controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newCal = (selectedCalendar.clone() as Calendar).apply {
                                add(Calendar.MONTH, -1)
                            }
                            selectedCalendar = newCal
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("month_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Mes anterior",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!isCurrentMonth) {
                        FilledTonalButton(
                            onClick = {
                                selectedCalendar = Calendar.getInstance()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Hoy", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!isCurrentMonth) {
                                val newCal = (selectedCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                                selectedCalendar = newCal
                            }
                        },
                        enabled = !isCurrentMonth,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("month_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Mes siguiente",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Habit Filter Chips (Horizontal Scrollable)
            if (habits.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedHabitFilterId == null,
                        onClick = { selectedHabitFilterId = null },
                        label = { Text("Todos (${habits.size})") },
                        leadingIcon = if (selectedHabitFilterId == null) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.testTag("chart_filter_chip_all")
                    )

                    habits.forEach { habit ->
                        FilterChip(
                            selected = selectedHabitFilterId == habit.id,
                            onClick = {
                                selectedHabitFilterId = if (selectedHabitFilterId == habit.id) null else habit.id
                            },
                            label = { Text(habit.title, maxLines = 1) },
                            leadingIcon = if (selectedHabitFilterId == habit.id) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.testTag("chart_filter_chip_${habit.id}")
                        )
                    }
                }
            }

            // Summary Key KPI Metric Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Monthly Average
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Promedio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${monthlyStats.averageRate.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                monthlyStats.averageRate >= 80 -> Color(0xFF10B981)
                                monthlyStats.averageRate >= 50 -> Color(0xFF6366F1)
                                else -> Color(0xFFF59E0B)
                            }
                        )
                    }
                }

                // Metric 2: Peak Day
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Día Pico",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Día ${monthlyStats.peakDay} (${monthlyStats.peakRate.toInt()}%)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6366F1)
                        )
                    }
                }

                // Metric 3: Total Completions
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Realizados",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${monthlyStats.totalCompletions}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Interactive Tooltip Box
            selectedPointIndex?.let { idx ->
                val point = monthlyStats.dataPoints.getOrNull(idx)
                if (point != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "Día ${point.dayNumber} (${point.dateString}):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "${point.completionRate.toInt()}% (${point.completedCount}/${point.scheduledCount} hábitos)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // The Canvas Line Chart
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
            val textMeasurer = rememberTextMeasurer()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(monthlyStats.dataPoints) {
                        detectTapGestures { offset ->
                            val leftPadding = 36.dp.toPx()
                            val rightPadding = 12.dp.toPx()
                            val chartWidth = size.width - leftPadding - rightPadding
                            val totalPoints = monthlyStats.dataPoints.size
                            if (totalPoints > 1 && offset.x >= leftPadding) {
                                val step = chartWidth / (totalPoints - 1)
                                val index = ((offset.x - leftPadding + (step / 2)) / step).toInt().coerceIn(0, totalPoints - 1)
                                selectedPointIndex = index
                            }
                        }
                    }
                    .pointerInput(monthlyStats.dataPoints) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val leftPadding = 36.dp.toPx()
                                val rightPadding = 12.dp.toPx()
                                val chartWidth = size.width - leftPadding - rightPadding
                                val totalPoints = monthlyStats.dataPoints.size
                                if (totalPoints > 1 && change.position.x >= leftPadding) {
                                    val step = chartWidth / (totalPoints - 1)
                                    val index = ((change.position.x - leftPadding + (step / 2)) / step).toInt().coerceIn(0, totalPoints - 1)
                                    selectedPointIndex = index
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLineChart(
                        stats = monthlyStats,
                        progress = chartAnimationProgress,
                        selectedPointIndex = selectedPointIndex,
                        primaryColor = primaryColor,
                        outlineVariantColor = outlineVariantColor,
                        onSurfaceVariantColor = onSurfaceVariantColor,
                        textMeasurer = textMeasurer
                    )
                }
            }

            // Legend / Instructions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "Curva de cumplimiento",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(Color(0xFFF59E0B))
                    )
                    Text(
                        text = "Promedio mensual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLineChart(
    stats: MonthlyTrendStats,
    progress: Float,
    selectedPointIndex: Int?,
    primaryColor: Color,
    outlineVariantColor: Color,
    onSurfaceVariantColor: Color,
    textMeasurer: TextMeasurer
) {
    val leftPadding = 36.dp.toPx()
    val rightPadding = 12.dp.toPx()
    val topPadding = 16.dp.toPx()
    val bottomPadding = 28.dp.toPx()

    val chartWidth = size.width - leftPadding - rightPadding
    val chartHeight = size.height - topPadding - bottomPadding

    if (chartWidth <= 0 || chartHeight <= 0 || stats.dataPoints.isEmpty()) return

    // 1. Draw horizontal guide lines (0%, 25%, 50%, 75%, 100%) and Y-axis labels
    val levels = listOf(0, 25, 50, 75, 100)
    levels.forEach { level ->
        val y = topPadding + chartHeight * (1f - (level / 100f))
        drawLine(
            color = outlineVariantColor.copy(alpha = 0.35f),
            start = Offset(leftPadding, y),
            end = Offset(size.width - rightPadding, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )

        // Y-axis label text
        val labelResult = textMeasurer.measure(
            text = "$level%",
            style = TextStyle(
                fontSize = 9.sp,
                color = onSurfaceVariantColor.copy(alpha = 0.8f)
            )
        )
        drawText(
            textLayoutResult = labelResult,
            topLeft = Offset(
                x = leftPadding - labelResult.size.width - 6.dp.toPx(),
                y = y - (labelResult.size.height / 2f)
            )
        )
    }

    // 2. Average Benchmark Line across the month
    if (stats.averageRate > 0f) {
        val avgY = topPadding + chartHeight * (1f - ((stats.averageRate / 100f).coerceIn(0f, 1f) * progress))
        drawLine(
            color = Color(0xFFF59E0B).copy(alpha = 0.7f),
            start = Offset(leftPadding, avgY),
            end = Offset(size.width - rightPadding, avgY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
        )
    }

    // 3. Compute Data Coordinates
    val points = stats.dataPoints
    val totalPoints = points.size
    val stepX = if (totalPoints > 1) chartWidth / (totalPoints - 1) else chartWidth

    val coordinates = points.mapIndexed { index, point ->
        val x = leftPadding + index * stepX
        val y = topPadding + chartHeight * (1f - ((point.completionRate / 100f) * progress))
        Offset(x, y)
    }

    // 4. Construct Smooth Cubic Bézier Spline Path
    if (coordinates.size > 1) {
        val linePath = Path()
        val fillPath = Path()

        linePath.moveTo(coordinates[0].x, coordinates[0].y)
        fillPath.moveTo(coordinates[0].x, topPadding + chartHeight)
        fillPath.lineTo(coordinates[0].x, coordinates[0].y)

        for (i in 0 until coordinates.size - 1) {
            val p0 = coordinates[i]
            val p1 = coordinates[i + 1]

            val controlX1 = p0.x + (p1.x - p0.x) / 2f
            val controlY1 = p0.y
            val controlX2 = p0.x + (p1.x - p0.x) / 2f
            val controlY2 = p1.y

            linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
        }

        fillPath.lineTo(coordinates.last().x, topPadding + chartHeight)
        fillPath.close()

        // Draw gradient area under the curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.30f),
                    primaryColor.copy(alpha = 0.02f)
                ),
                startY = topPadding,
                endY = topPadding + chartHeight
            )
        )

        // Draw the smooth curve line
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    // 5. Draw Key Data Points & X-Axis Day Labels
    val labelInterval = when {
        totalPoints <= 10 -> 1
        totalPoints <= 20 -> 2
        else -> 5
    }

    coordinates.forEachIndexed { index, coord ->
        val point = points[index]

        // Draw X-axis day labels at spaced intervals (1, 5, 10, 15, 20, 25, 30/last)
        if (point.dayNumber == 1 || point.dayNumber % labelInterval == 0 || index == totalPoints - 1) {
            val dayLabel = textMeasurer.measure(
                text = "${point.dayNumber}",
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = if (point.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (point.isToday) primaryColor else onSurfaceVariantColor
                )
            )
            drawText(
                textLayoutResult = dayLabel,
                topLeft = Offset(
                    x = coord.x - (dayLabel.size.width / 2f),
                    y = topPadding + chartHeight + 6.dp.toPx()
                )
            )
        }

        // Draw point dots for elapsed days (or today)
        if (!point.isFuture && progress > 0.8f) {
            // Halo glow for high compliance or today
            if (point.isToday || point.completionRate >= 100f) {
                drawCircle(
                    color = if (point.isToday) Color(0xFF06B6D4).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.35f),
                    radius = 5.dp.toPx(),
                    center = coord
                )
            }

            drawCircle(
                color = primaryColor,
                radius = 3.dp.toPx(),
                center = coord
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = coord
            )
        }
    }

    // 6. Draw Selected Point Indicator (Scrubbing / Tap)
    if (selectedPointIndex != null && selectedPointIndex in coordinates.indices) {
        val selectedCoord = coordinates[selectedPointIndex]

        // Vertical highlight guide line
        drawLine(
            color = primaryColor.copy(alpha = 0.6f),
            start = Offset(selectedCoord.x, topPadding),
            end = Offset(selectedCoord.x, topPadding + chartHeight),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )

        // Pulsing highlight circle
        drawCircle(
            color = primaryColor.copy(alpha = 0.25f),
            radius = 9.dp.toPx(),
            center = selectedCoord
        )
        drawCircle(
            color = primaryColor,
            radius = 5.dp.toPx(),
            center = selectedCoord
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = selectedCoord
        )
    }
}

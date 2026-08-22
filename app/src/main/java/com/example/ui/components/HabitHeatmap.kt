package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * HabitHeatmap: Componente de cuadrícula interactiva estilo GitHub que visualiza
 * el progreso y la constancia de hábitos completados en los últimos meses,
 * utilizando una escala de intensidad de color basada en el número de tareas realizadas.
 */
@Composable
fun HabitHeatmap(
    habits: List<Habit>,
    allLogs: List<HabitLog>,
    modifier: Modifier = Modifier,
    weeks: Int = 20,
    selectedHabitId: Long? = null,
    onDateSelected: (String) -> Unit = {},
    showFilters: Boolean = true,
    showStatsHeader: Boolean = true
) {
    var internalSelectedHabitId by remember { mutableStateOf(selectedHabitId) }
    var selectedDayStr by remember { mutableStateOf<String?>(null) }

    val activeHabitFilter = selectedHabitId ?: internalSelectedHabitId

    val filteredLogs = remember(allLogs, activeHabitFilter) {
        if (activeHabitFilter != null) {
            allLogs.filter { it.habitId == activeHabitFilter }
        } else {
            allLogs
        }
    }

    val logsByDate = remember(filteredLogs) {
        filteredLogs.groupBy { it.date }
    }

    val dateMatrix = remember(weeks) {
        DateUtils.getHeatmapDateMatrix(weeks = weeks)
    }

    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)

    val (currentStreak, bestStreak) = remember(logsByDate) {
        DateUtils.calculateStreak(logsByDate.keys)
    }
    val totalActiveDays = logsByDate.keys.size
    val totalCheckIns = filteredLogs.size

    val animatedCurrentStreak by animateIntAsState(
        targetValue = currentStreak,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "heatmap_current_streak"
    )
    val animatedBestStreak by animateIntAsState(
        targetValue = bestStreak,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "heatmap_best_streak"
    )
    val animatedActiveDays by animateIntAsState(
        targetValue = totalActiveDays,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "heatmap_active_days"
    )
    val animatedCheckIns by animateIntAsState(
        targetValue = totalCheckIns,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "heatmap_check_ins"
    )

    // Month headers calculator for columns
    val monthPositions = remember(dateMatrix) {
        calculateMonthPositions(dateMatrix)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Mapa de Calor",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mapa de Calor de Hábitos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Historial de consistencia estilo GitHub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showStatsHeader) {
                Spacer(modifier = Modifier.height(16.dp))

                // Metric Summary Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeatmapStatPill(
                        title = "Racha Actual",
                        value = "$animatedCurrentStreak d",
                        icon = Icons.Default.LocalFireDepartment,
                        color = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Mejor Racha",
                        value = "$animatedBestStreak d",
                        icon = Icons.Default.EmojiEvents,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Días Activos",
                        value = "$animatedActiveDays",
                        icon = Icons.Default.Eco,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Completados",
                        value = "$animatedCheckIns",
                        icon = Icons.Default.Bolt,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (showFilters && habits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Filtrar por hábito:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeHabitFilter == null,
                        onClick = { internalSelectedHabitId = null },
                        label = { Text("Todos (${habits.size})") }
                    )
                    habits.forEach { habit ->
                        FilterChip(
                            selected = activeHabitFilter == habit.id,
                            onClick = { internalSelectedHabitId = habit.id },
                            label = { Text(habit.title) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GitHub-Style Heatmap Grid Container (Fixed Left Day Labels + Scrollable Matrix)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // STICKY / FIXED LEFT COLUMN: Day Labels (L, M, X, J, V, S, D)
                    Column(
                        modifier = Modifier.width(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Month Header spacer
                        Spacer(modifier = Modifier.height(20.dp))

                        val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
                        dayLabels.forEach { label ->
                            Box(
                                modifier = Modifier.size(width = 22.dp, height = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }

                    // Divider line between fixed day column and scrollable heatmap grid
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(1.dp)
                            .height((20 + 7 * 14 + 6 * 3).dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )

                    val totalGridWidth = (dateMatrix.size * 17).dp

                    // SCROLLABLE RIGHT SECTION: Month Headers + Heatmap Matrix
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState)
                    ) {
                        // Month Headers with precise horizontal offset
                        Box(
                            modifier = Modifier
                                .width(totalGridWidth)
                                .height(20.dp)
                        ) {
                            monthPositions.forEach { item ->
                                val xOffset = (item.weekIndex * 17).dp
                                Text(
                                    text = item.monthName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.offset(x = xOffset)
                                )
                            }
                        }

                        // Weeks Columns
                        Row(
                            modifier = Modifier.width(totalGridWidth),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            dateMatrix.forEach { week ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    week.forEach { dateStr ->
                                        val completedCount = logsByDate[dateStr]?.size ?: 0
                                        val isSelected = (selectedDayStr == dateStr)

                                        HabitHeatmapCell(
                                            count = completedCount,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedDayStr = dateStr
                                                onDateSelected(dateStr)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Desliza para ver más meses",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    listOf(0, 1, 2, 3, 4).forEach { countLevel ->
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(getHabitHeatmapColor(countLevel))
                                .border(
                                    width = 0.5.dp,
                                    color = Color.Black.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(2.5.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "4+",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Interactive Day Details Card
            AnimatedVisibility(
                visible = selectedDayStr != null,
                enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedDayStr?.let { dateStr ->
                    val dayLogs = logsByDate[dateStr] ?: emptyList()
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(getHabitHeatmapColor(dayLogs.size))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = DateUtils.formatDateForDisplay(dateStr),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                IconButton(
                                    onClick = { selectedDayStr = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar detalle",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (dayLogs.isEmpty()) "Ningún hábito completado en esta fecha."
                                else "${dayLogs.size} ${if (dayLogs.size == 1) "hábito completado" else "hábitos completados"}:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (dayLogs.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (dayLogs.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (dayLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                dayLogs.forEach { log ->
                                    val habit = habits.find { it.id == log.habitId }
                                    if (habit != null) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = habit.title + if (habit.unit.isNotEmpty() && log.value > 0) " (${log.value} ${habit.unit})" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Tile/Cell of the GitHub style heatmap.
 */
@Composable
fun HabitHeatmapCell(
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cellColor = getHabitHeatmapColor(count)

    Box(
        modifier = Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(3.5.dp))
            .background(cellColor)
            .border(
                width = if (isSelected) 1.8.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.12f),
                shape = RoundedCornerShape(3.5.dp)
            )
            .clickable { onClick() }
    )
}

/**
 * Color scale from lowest to highest intensity based on completed task count.
 * Level 0: Muted empty gray/slate
 * Level 1: Subtle green / emerald light
 * Level 2: Medium green / emerald medium
 * Level 3: Strong green / emerald vibrant
 * Level 4+: Ultra bright vibrant green
 */
fun getHabitHeatmapColor(count: Int): Color {
    return when {
        count <= 0 -> Color(0xFF334155).copy(alpha = 0.25f) // Level 0: Empty cell
        count == 1 -> Color(0xFF065F46)                      // Level 1: 1 completed task
        count == 2 -> Color(0xFF059669)                      // Level 2: 2 completed tasks
        count == 3 -> Color(0xFF10B981)                      // Level 3: 3 completed tasks
        else -> Color(0xFF34D399)                            // Level 4: 4+ completed tasks (Max intensity)
    }
}

@Composable
fun HeatmapStatPill(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 13.sp
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

data class MonthHeaderItem(
    val monthName: String,
    val weekIndex: Int
)

private fun calculateMonthPositions(dateMatrix: List<List<String>>): List<MonthHeaderItem> {
    if (dateMatrix.isEmpty()) return emptyList()
    val positions = mutableListOf<MonthHeaderItem>()
    val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMM", Locale("es", "ES"))

    var lastMonth = ""
    var lastAddedWeek = -10

    dateMatrix.forEachIndexed { weekIndex, week ->
        val firstOfMonth = week.find { it.endsWith("-01") }
        val targetDay = firstOfMonth ?: week.getOrNull(3) ?: week.firstOrNull()
        val monthStr = if (targetDay != null) {
            try {
                val d = iso.parse(targetDay)
                if (d != null) monthFmt.format(d).replaceFirstChar { it.uppercase() } else ""
            } catch (_: Exception) { "" }
        } else ""

        if (monthStr.isNotEmpty()) {
            if (weekIndex == 0) {
                positions.add(MonthHeaderItem(monthStr, 0))
                lastMonth = monthStr
                lastAddedWeek = 0
            } else if (monthStr != lastMonth && (weekIndex - lastAddedWeek) >= 3) {
                positions.add(MonthHeaderItem(monthStr, weekIndex))
                lastMonth = monthStr
                lastAddedWeek = weekIndex
            }
        }
    }
    return positions
}

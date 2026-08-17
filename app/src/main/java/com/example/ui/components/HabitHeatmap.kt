package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    // Month headers calculator for columns
    val monthHeaders = remember(dateMatrix) {
        calculateMonthHeaders(dateMatrix)
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
                        value = "$currentStreak d",
                        icon = "🔥",
                        color = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Mejor Racha",
                        value = "$bestStreak d",
                        icon = "🏆",
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Días Activos",
                        value = "$totalActiveDays",
                        icon = "🌱",
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Completados",
                        value = "$totalCheckIns",
                        icon = "⚡",
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

            // GitHub-Style Heatmap Grid Container with Horizontal Scroll
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp)
            ) {
                // Month Headers Row
                Row(
                    modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
                ) {
                    monthHeaders.forEach { header ->
                        Box(
                            modifier = Modifier.width((header.weekSpan * 18).dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = header.monthName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Grid Body: Weekday Labels + Matrix of Columns
                Row(verticalAlignment = Alignment.Top) {
                    // Weekday column: L, M, X, J, V, S, D
                    Column(
                        modifier = Modifier.padding(end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
                        dayLabels.forEach { label ->
                            Box(
                                modifier = Modifier.size(15.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Weeks Columns
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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

            Spacer(modifier = Modifier.height(12.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Desliza para ver más meses 👈",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

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
                enter = fadeIn(),
                exit = fadeOut()
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
                                            Text(
                                                text = "✓",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
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
    icon: String,
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
            Text(text = icon, fontSize = 16.sp)
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
    val weekSpan: Int
)

private fun calculateMonthHeaders(dateMatrix: List<List<String>>): List<MonthHeaderItem> {
    if (dateMatrix.isEmpty()) return emptyList()
    val headers = mutableListOf<MonthHeaderItem>()
    val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMM", Locale("es", "ES"))

    var currentMonth = ""
    var currentSpan = 0

    dateMatrix.forEach { week ->
        val midDay = week.getOrNull(3) ?: week.firstOrNull()
        val monthStr = if (midDay != null) {
            try {
                val d = iso.parse(midDay)
                if (d != null) monthFmt.format(d).replaceFirstChar { it.uppercase() } else ""
            } catch (_: Exception) { "" }
        } else ""

        if (monthStr != currentMonth) {
            if (currentMonth.isNotEmpty() && currentSpan > 0) {
                headers.add(MonthHeaderItem(currentMonth, currentSpan))
            }
            currentMonth = monthStr
            currentSpan = 1
        } else {
            currentSpan++
        }
    }

    if (currentMonth.isNotEmpty() && currentSpan > 0) {
        headers.add(MonthHeaderItem(currentMonth, currentSpan))
    }

    return headers
}

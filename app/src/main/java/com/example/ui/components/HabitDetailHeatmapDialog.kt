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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HabitLog
import com.example.model.HabitWithStats
import com.example.util.DateUtils
import com.example.util.IconHelper
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HabitDetailHeatmapDialog(
    habitWithStats: HabitWithStats,
    allLogs: List<HabitLog>,
    onDismiss: () -> Unit,
    onToggleDateCompletion: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val habitLogs = remember(allLogs, habit.id) {
        allLogs.filter { it.habitId == habit.id }
    }

    val logsByDate = remember(habitLogs) {
        habitLogs.associateBy { it.date }
    }

    val weeks = 20
    val dateMatrix = remember(weeks) {
        DateUtils.getHeatmapDateMatrix(weeks = weeks)
    }

    val monthHeaders = remember(dateMatrix) {
        calculateMonthHeadersForHabit(dateMatrix)
    }

    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    var selectedDayStr by remember { mutableStateOf<String?>(null) }

    val (currentStreak, bestStreak) = remember(logsByDate) {
        DateUtils.calculateStreak(logsByDate.keys)
    }
    val totalActiveDays = logsByDate.keys.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("habit_detail_heatmap_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Dialog Header: Icon, Habit Title, Category, Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(habitColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIconByName(habit.iconName),
                                contentDescription = habit.title,
                                tint = habitColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = habit.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = habit.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = habitColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = " • Mapa de Consistencia",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_heatmap_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeatmapStatPill(
                        title = "Racha Actual",
                        value = "$currentStreak días",
                        icon = "🔥",
                        color = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Mejor Racha",
                        value = "$bestStreak días",
                        icon = "🏆",
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Total Días",
                        value = "$totalActiveDays",
                        icon = "🌱",
                        color = habitColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Heatmap Container
                Text(
                    text = "HISTORIAL (ÚLTIMAS 20 SEMANAS)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Matrix
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp)
                        .horizontalScroll(scrollState)
                ) {
                    // Month Headers Row
                    Row(
                        modifier = Modifier.padding(start = 22.dp, bottom = 6.dp)
                    ) {
                        monthHeaders.forEach { header ->
                            Box(
                                modifier = Modifier.width((header.weekSpan * 17).dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = header.monthName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Grid Body: Days + Columns
                    Row(verticalAlignment = Alignment.Top) {
                        // Weekday labels
                        Column(
                            modifier = Modifier.padding(end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            listOf("L", "M", "X", "J", "V", "S", "D").forEach { label ->
                                Box(
                                    modifier = Modifier.size(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Week Columns
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            dateMatrix.forEach { week ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    week.forEach { dateStr ->
                                        val isCompleted = logsByDate.containsKey(dateStr)
                                        val isSelected = (selectedDayStr == dateStr)

                                        SingleHabitHeatmapCell(
                                            isCompleted = isCompleted,
                                            habitColor = habitColor,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedDayStr = dateStr
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Desliza para ver historial 👈",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color(0xFF334155).copy(alpha = 0.25f))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("No", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(habitColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hecho", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Interactive Day Details Box
                AnimatedVisibility(
                    visible = selectedDayStr != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    selectedDayStr?.let { dateStr ->
                        val log = logsByDate[dateStr]
                        val isDone = log != null

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDone) habitColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isDone) habitColor else MaterialTheme.colorScheme.outlineVariant
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = DateUtils.formatDateForDisplay(dateStr),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isDone) {
                                            if (habit.unit.isNotEmpty() && log.value > 0f) {
                                                "✓ Completado (${log.value} ${habit.unit})"
                                            } else {
                                                "✓ Completado con éxito"
                                            }
                                        } else "No registrado este día",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDone) habitColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (onToggleDateCompletion != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            onToggleDateCompletion(dateStr)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(if (isDone) "Desmarcar" else "Marcar hecho", fontSize = 11.sp)
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

@Composable
private fun SingleHabitHeatmapCell(
    isCompleted: Boolean,
    habitColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cellColor = if (isCompleted) habitColor else Color(0xFF334155).copy(alpha = 0.22f)

    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(cellColor)
            .border(
                width = if (isSelected) 1.6.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.12f),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable { onClick() }
    )
}

private data class MonthHeader(
    val monthName: String,
    val weekSpan: Int
)

private fun calculateMonthHeadersForHabit(dateMatrix: List<List<String>>): List<MonthHeader> {
    if (dateMatrix.isEmpty()) return emptyList()
    val headers = mutableListOf<MonthHeader>()
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
                headers.add(MonthHeader(currentMonth, currentSpan))
            }
            currentMonth = monthStr
            currentSpan = 1
        } else {
            currentSpan++
        }
    }

    if (currentMonth.isNotEmpty() && currentSpan > 0) {
        headers.add(MonthHeader(currentMonth, currentSpan))
    }

    return headers
}

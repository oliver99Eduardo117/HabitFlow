package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitLog
import com.example.model.HabitWithStats
import com.example.util.DateUtils
import com.example.util.IconHelper
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HabitsHeatmapLayout(
    habits: List<HabitWithStats>,
    allLogs: List<HabitLog>,
    onToggleCompletion: (Long) -> Unit,
    onOpenProgressDialog: (HabitWithStats) -> Unit,
    onToggleDateCompletion: (Long, String) -> Unit,
    onEditHabit: (HabitWithStats) -> Unit,
    modifier: Modifier = Modifier
) {
    if (habits.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No hay hábitos para mostrar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crea un hábito o ajusta los filtros de búsqueda",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("habits_heatmap_list_layout"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(habits, key = { it.habit.id }) { habitWithStats ->
            SingleHabitHeatmapCard(
                habitWithStats = habitWithStats,
                allLogs = allLogs,
                onToggleCompletion = { onToggleCompletion(habitWithStats.habit.id) },
                onOpenProgressDialog = { onOpenProgressDialog(habitWithStats) },
                onToggleDateCompletion = { dateStr ->
                    onToggleDateCompletion(habitWithStats.habit.id, dateStr)
                },
                onEditHabit = { onEditHabit(habitWithStats) }
            )
        }
    }
}

@Composable
private fun SingleHabitHeatmapCard(
    habitWithStats: HabitWithStats,
    allLogs: List<HabitLog>,
    onToggleCompletion: () -> Unit,
    onOpenProgressDialog: () -> Unit,
    onToggleDateCompletion: (String) -> Unit,
    onEditHabit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val habitColor = remember(habit.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(habit.colorHex))
        } catch (_: Exception) {
            Color(0xFF6366F1)
        }
    }

    val habitLogs = remember(allLogs, habit.id) {
        allLogs.filter { it.habitId == habit.id }
    }

    val logsByDate = remember(habitLogs) {
        habitLogs.associateBy { it.date }
    }

    val weeks = 18
    val dateMatrix = remember(weeks) {
        DateUtils.getHeatmapDateMatrix(weeks = weeks)
    }

    val monthHeaders = remember(dateMatrix) {
        calculateMonthHeaders(dateMatrix)
    }

    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    var selectedDateStr by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_heatmap_card_${habit.id}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon, Title, Category, Streak Pill, and Today Action Button
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(habitColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconHelper.getIconByName(habit.iconName),
                            contentDescription = habit.title,
                            tint = habitColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = habit.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = habitColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (habitWithStats.currentStreak > 0) {
                                Text(
                                    text = " • 🔥 ${habitWithStats.currentStreak}d racha",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF97316),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Action Check / Quantity button for Today
                val isCompletedToday = habitWithStats.isCompletedToday
                if (habit.unit.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onOpenProgressDialog,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isCompletedToday) habitColor else habitColor.copy(alpha = 0.15f),
                            contentColor = if (isCompletedToday) Color.White else habitColor
                        )
                    ) {
                        Text(
                            text = "${habitWithStats.todayLog?.value?.toInt() ?: 0}/${habit.targetValue.toInt()} ${habit.unit}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    IconButton(
                        onClick = onToggleCompletion,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isCompletedToday) habitColor else habitColor.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completar hoy",
                            tint = if (isCompletedToday) Color.White else habitColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatPill(
                    label = "Racha",
                    value = "${habitWithStats.currentStreak} días",
                    color = Color(0xFFF97316),
                    modifier = Modifier.weight(1f)
                )
                MiniStatPill(
                    label = "Mejor Racha",
                    value = "${habitWithStats.bestStreak} días",
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                MiniStatPill(
                    label = "Total Días",
                    value = "${logsByDate.size}",
                    color = habitColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Heatmap Matrix for this Habit
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .horizontalScroll(scrollState)
            ) {
                // Month Headers
                Row(
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
                ) {
                    monthHeaders.forEach { header ->
                        Box(
                            modifier = Modifier.width((header.weekSpan * 16).dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = header.monthName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Grid Body: Weekday letters + Week columns
                Row(verticalAlignment = Alignment.Top) {
                    // Weekday initials (L, M, X, J, V, S, D)
                    Column(
                        modifier = Modifier.padding(end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.5.dp)
                    ) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEach { label ->
                            Box(
                                modifier = Modifier.size(13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Heatmap Columns
                    Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                        dateMatrix.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                                week.forEach { dateStr ->
                                    val isCompleted = logsByDate.containsKey(dateStr)
                                    val isSelected = (selectedDateStr == dateStr)
                                    val isToday = (dateStr == DateUtils.getTodayDateString())

                                    val cellColor = if (isCompleted) habitColor else Color(0xFF334155).copy(alpha = 0.2f)

                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(cellColor)
                                            .border(
                                                width = if (isSelected) 1.5.dp else if (isToday) 1.dp else 0.5.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) habitColor else Color.Black.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                            .clickable {
                                                selectedDateStr = if (selectedDateStr == dateStr) null else dateStr
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Day inspector & toggle
            AnimatedVisibility(
                visible = selectedDateStr != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedDateStr?.let { dateStr ->
                    val log = logsByDate[dateStr]
                    val isDone = log != null

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDone) habitColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isDone) habitColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DateUtils.formatDateForDisplay(dateStr),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDone) {
                                        if (habit.unit.isNotEmpty() && log.value > 0f) {
                                            "✓ Completado (${log.value.toInt()} ${habit.unit})"
                                        } else {
                                            "✓ Completado con éxito"
                                        }
                                    } else "Sin registro este día",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDone) habitColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    onToggleDateCompletion(dateStr)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isDone) Color(0xFFEF4444).copy(alpha = 0.15f) else habitColor.copy(alpha = 0.2f),
                                    contentColor = if (isDone) Color(0xFFEF4444) else habitColor
                                )
                            ) {
                                Text(
                                    text = if (isDone) "Desmarcar" else "Marcar hecho",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class HabitLayoutMonthHeader(
    val monthName: String,
    val weekSpan: Int
)

private fun calculateMonthHeaders(dateMatrix: List<List<String>>): List<HabitLayoutMonthHeader> {
    if (dateMatrix.isEmpty()) return emptyList()
    val headers = mutableListOf<HabitLayoutMonthHeader>()
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
                headers.add(HabitLayoutMonthHeader(currentMonth, currentSpan))
            }
            currentMonth = monthStr
            currentSpan = 1
        } else {
            currentSpan++
        }
    }

    if (currentMonth.isNotEmpty() && currentSpan > 0) {
        headers.add(HabitLayoutMonthHeader(currentMonth, currentSpan))
    }

    return headers
}

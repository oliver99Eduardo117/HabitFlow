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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val monthPositions = remember(dateMatrix) {
        calculateMonthPositionsForHabit(dateMatrix)
    }

    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    var selectedDayStr by remember { mutableStateOf<String?>(null) }

    val (currentStreak, bestStreak) = remember(logsByDate) {
        DateUtils.calculateStreak(logsByDate.keys)
    }
    val totalActiveDays = logsByDate.keys.size

    val animatedCurrentStreak by animateIntAsState(
        targetValue = currentStreak,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "detail_heatmap_current_streak"
    )
    val animatedBestStreak by animateIntAsState(
        targetValue = bestStreak,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "detail_heatmap_best_streak"
    )
    val animatedActiveDays by animateIntAsState(
        targetValue = totalActiveDays,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "detail_heatmap_active_days"
    )

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
                        value = "$animatedCurrentStreak días",
                        icon = Icons.Default.LocalFireDepartment,
                        color = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Mejor Racha",
                        value = "$animatedBestStreak días",
                        icon = Icons.Default.EmojiEvents,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    HeatmapStatPill(
                        title = "Total Días",
                        value = "$animatedActiveDays",
                        icon = Icons.Default.Eco,
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

                val totalGridWidth = (dateMatrix.size * 18).dp

                // Heatmap Container (Fixed Left Day Column + Horizontally Scrollable Grid)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // STICKY / FIXED LEFT COLUMN: Day Labels (L, M, X, J, V, S, D)
                        Column(
                            modifier = Modifier.width(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Month Header spacer
                            Spacer(modifier = Modifier.height(22.dp))

                            val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
                            dayLabels.forEach { label ->
                                Box(
                                    modifier = Modifier.size(width = 24.dp, height = 15.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                            }
                        }

                        // Divider between day column and scrollable heatmap matrix
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .width(1.dp)
                                .height((22 + 7 * 15 + 6 * 3).dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        )

                        // SCROLLABLE RIGHT SECTION: Month Headers + Heatmap Columns
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollState)
                        ) {
                            // Month Headers with precise horizontal offset
                            Box(
                                modifier = Modifier
                                    .width(totalGridWidth)
                                    .height(22.dp)
                            ) {
                                monthPositions.forEach { item ->
                                    val xOffset = (item.weekIndex * 18).dp
                                    Text(
                                        text = item.monthName,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.offset(x = xOffset)
                                    )
                                }
                            }

                            // Week Columns
                            Row(
                                modifier = Modifier.width(totalGridWidth),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
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
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Legend
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
                            text = "Desliza para ver historial",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

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
                    enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                    exit = fadeOut() + shrinkVertically()
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (isDone) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = habitColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (habit.unit.isNotEmpty() && log.value > 0f) {
                                                    "Completado (${log.value} ${habit.unit})"
                                                } else {
                                                    "Completado con éxito"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = habitColor
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "No registrado este día",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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
            .size(15.dp)
            .clip(RoundedCornerShape(3.5.dp))
            .background(cellColor)
            .border(
                width = if (isSelected) 1.6.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.12f),
                shape = RoundedCornerShape(3.5.dp)
            )
            .clickable { onClick() }
    )
}

private data class DetailMonthHeaderPosition(
    val monthName: String,
    val weekIndex: Int
)

private fun calculateMonthPositionsForHabit(dateMatrix: List<List<String>>): List<DetailMonthHeaderPosition> {
    if (dateMatrix.isEmpty()) return emptyList()
    val positions = mutableListOf<DetailMonthHeaderPosition>()
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
                positions.add(DetailMonthHeaderPosition(monthStr, 0))
                lastMonth = monthStr
                lastAddedWeek = 0
            } else if (monthStr != lastMonth && (weekIndex - lastAddedWeek) >= 3) {
                positions.add(DetailMonthHeaderPosition(monthStr, weekIndex))
                lastMonth = monthStr
                lastAddedWeek = weekIndex
            }
        }
    }
    return positions
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.HabitWithStats
import com.example.model.SubTask
import com.example.util.DateUtils
import com.example.util.IconHelper
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailDialog(
    habitWithStats: HabitWithStats,
    allLogs: List<HabitLog>,
    onDismiss: () -> Unit,
    onToggleTodayCompletion: () -> Unit,
    onStartPomodoro: () -> Unit,
    onEditHabit: () -> Unit,
    onArchiveHabit: () -> Unit,
    onUnarchiveHabit: (() -> Unit)? = null,
    onDeleteHabit: () -> Unit,
    onToggleSubTask: (SubTask, Boolean) -> Unit = { _, _ -> },
    onToggleDateCompletion: ((String) -> Unit)? = null
) {
    val habit = habitWithStats.habit
    val isCompletedToday = habitWithStats.isCompletedToday
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var selectedDayStr by remember { mutableStateOf<String?>(null) }

    val habitLogs = remember(allLogs, habit.id) {
        allLogs.filter { it.habitId == habit.id }
    }

    val logsByDate = remember(habitLogs) {
        habitLogs.associateBy { it.date }
    }

    val (currentStreak, bestStreak) = remember(logsByDate) {
        DateUtils.calculateStreak(logsByDate.keys)
    }
    val totalActiveDays = logsByDate.keys.size

    val weeks = 16
    val dateMatrix = remember(weeks) {
        DateUtils.getHeatmapDateMatrix(weeks = weeks)
    }
    val monthPositions = remember(dateMatrix) {
        calculateDetailMonthPositions(dateMatrix)
    }
    val heatmapScrollState = rememberScrollState(initial = Int.MAX_VALUE)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp)
                .testTag("habit_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header Row
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(habitColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIconByName(habit.iconName),
                                contentDescription = habit.title,
                                tint = habitColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = habit.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = habitColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(habitColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                if (habit.isArchived) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "Archivado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = habit.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_habit_detail_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Description
                    if (habit.description.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = habit.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Key Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailStatCard(
                            title = "Racha Actual",
                            value = "$currentStreak días",
                            icon = Icons.Default.LocalFireDepartment,
                            iconTint = Color(0xFFF97316),
                            modifier = Modifier.weight(1f)
                        )
                        DetailStatCard(
                            title = "Mejor Racha",
                            value = "$bestStreak días",
                            icon = Icons.Default.EmojiEvents,
                            iconTint = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )
                        DetailStatCard(
                            title = "Total Días",
                            value = "$totalActiveDays",
                            icon = Icons.Default.CheckCircle,
                            iconTint = habitColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Habit Configuration Attributes (Frequency, Reminder, Quantitative Goal)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "DETALLES DE CONFIGURACIÓN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Frequency Days
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Frecuencia:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val days = listOf("L", "M", "X", "J", "V", "S", "D")
                                    days.forEachIndexed { index, dayLetter ->
                                        val dayNum = index + 1
                                        val isActive = habit.frequencyDays.contains(dayNum)
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isActive) habitColor else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayLetter,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Reminder details
                            if (!habit.reminderTime.isNullOrBlank()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = habitColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Recordatorio diario:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (habit.reminderMinutesAdvance > 0) "${habit.reminderTime} (-${habit.reminderMinutesAdvance} min)" else habit.reminderTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Quantitative metric
                            if (habit.unit.isNotEmpty()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.TrackChanges,
                                            contentDescription = null,
                                            tint = habitColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Meta cuantitativa:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${habit.targetValue.toInt()} ${habit.unit} al día",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Pomodoro Timer
                            if (habit.hasTimer) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = habitColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Enfoque / Pomodoro:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${habit.timerDurationMinutes} minutos",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Sub-tasks checklist if any
                    if (habitWithStats.subTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                val completedCount = habitWithStats.subTasks.count { it.isCompleted }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SUB-RUTINAS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$completedCount / ${habitWithStats.subTasks.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = habitColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                habitWithStats.subTasks.forEach { subTask ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleSubTask(subTask, !subTask.isCompleted) }
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = subTask.isCompleted,
                                            onCheckedChange = { onToggleSubTask(subTask, it) },
                                            colors = CheckboxDefaults.colors(checkedColor = habitColor),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = subTask.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (subTask.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Heatmap Consistency Matrix
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MAPA DE CONSISTENCIA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Últimas 16 semanas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val totalGridWidth = (dateMatrix.size * 18).dp

                            // Consistency Grid Container (Fixed Day Column + Scrollable Month & Week Columns)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
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
                                        // Month Header spacer to align with grid rows
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
                                            .horizontalScroll(heatmapScrollState)
                                    ) {
                                        // Month Headers Row
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

                                        // Heatmap Matrix
                                        Row(
                                            modifier = Modifier.width(totalGridWidth),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            dateMatrix.forEach { week ->
                                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    week.forEach { dateStr ->
                                                        val isCompleted = logsByDate.containsKey(dateStr)
                                                        val isSelected = (selectedDayStr == dateStr)
                                                        val cellColor = if (isCompleted) habitColor else Color(0xFF334155).copy(alpha = 0.22f)

                                                        Box(
                                                            modifier = Modifier
                                                                .size(15.dp)
                                                                .clip(RoundedCornerShape(3.5.dp))
                                                                .background(cellColor)
                                                                .border(
                                                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                                                    shape = RoundedCornerShape(3.5.dp)
                                                                )
                                                                .clickable { selectedDayStr = dateStr }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Selected day detail
                            AnimatedVisibility(
                                visible = selectedDayStr != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                selectedDayStr?.let { dateStr ->
                                    val log = logsByDate[dateStr]
                                    val isDone = log != null

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDone) habitColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = DateUtils.formatDateForDisplay(dateStr),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isDone) "Completado" else "No registrado",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDone) habitColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (onToggleDateCompletion != null) {
                                                TextButton(
                                                    onClick = { onToggleDateCompletion(dateStr) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action Bar: Edit, Archive/Unarchive, Delete & Main Action
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary Icon Buttons: Edit, Archive, Delete
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                onDismiss()
                                onEditHabit()
                            },
                            modifier = Modifier.testTag("detail_edit_habit_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar hábito",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (habit.isArchived && onUnarchiveHabit != null) {
                            IconButton(
                                onClick = {
                                    onUnarchiveHabit()
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("detail_unarchive_habit_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Unarchive,
                                    contentDescription = "Desarchivar hábito",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    onArchiveHabit()
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("detail_archive_habit_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = "Archivar hábito",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.testTag("detail_delete_habit_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar hábito",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Primary Action: Focus Timer & Today Completion Button (Palomita)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onStartPomodoro()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enfoque", fontSize = 12.sp)
                        }

                        FilledIconButton(
                            onClick = onToggleTodayCompletion,
                            shape = RoundedCornerShape(12.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isCompletedToday) habitColor else habitColor.copy(alpha = 0.18f),
                                contentColor = if (isCompletedToday) Color.White else habitColor
                            ),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("detail_toggle_today_btn")
                        ) {
                            Icon(
                                imageVector = if (isCompletedToday) Icons.Default.Check else Icons.Default.CheckCircleOutline,
                                contentDescription = if (isCompletedToday) "Completado hoy" else "Marcar completado hoy",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("¿Eliminar este hábito?")
            },
            text = {
                Text("¿Estás seguro de que deseas eliminar permanentemente \"${habit.title}\"? Se borrarán también sus registros históricos y recordatorios.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteHabit()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DetailStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = iconTint.copy(alpha = 0.1f)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(iconTint.copy(alpha = 0.3f))
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = iconTint
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DetailMonthPosition(
    val monthName: String,
    val weekIndex: Int
)

private fun calculateDetailMonthPositions(dateMatrix: List<List<String>>): List<DetailMonthPosition> {
    if (dateMatrix.isEmpty()) return emptyList()
    val positions = mutableListOf<DetailMonthPosition>()
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
                positions.add(DetailMonthPosition(monthStr, 0))
                lastMonth = monthStr
                lastAddedWeek = 0
            } else if (monthStr != lastMonth && (weekIndex - lastAddedWeek) >= 3) {
                positions.add(DetailMonthPosition(monthStr, weekIndex))
                lastMonth = monthStr
                lastAddedWeek = weekIndex
            }
        }
    }
    return positions
}

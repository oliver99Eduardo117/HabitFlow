package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitWithStats
import com.example.model.SubTask
import com.example.util.IconHelper

@Composable
fun HabitTileCard(
    habitWithStats: HabitWithStats,
    isGridView: Boolean = true,
    onToggleCompletion: () -> Unit,
    onOpenProgressDialog: () -> Unit,
    onStartTimer: () -> Unit,
    onToggleSubTask: (SubTask, Boolean) -> Unit,
    onEditHabit: () -> Unit,
    onArchiveHabit: () -> Unit,
    onTestReminder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val isCompleted = habitWithStats.isCompletedToday
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    var expandedMenu by remember { mutableStateOf(false) }
    var showSubTasks by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (isCompleted) 0.98f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    val currentVal = habitWithStats.todayLog?.value ?: 0f
    val targetVal = habit.targetValue
    val progressRatio = if (targetVal > 0f) (currentVal / targetVal).coerceIn(0f, 1f) else if (isCompleted) 1f else 0f
    val isOverachieved = currentVal > targetVal

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .testTag("habit_card_${habit.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                habitColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                if (isCompleted) listOf(habitColor, habitColor.copy(alpha = 0.5f))
                else listOf(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon, Category Badge, Streaks, More Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCompleted) habitColor else habitColor.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconHelper.getIconByName(habit.iconName),
                        contentDescription = habit.title,
                        tint = if (isCompleted) Color.White else habitColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = habit.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = habitColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(habitColor.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        if (!habit.reminderTime.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Recordatorio activo",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (habit.reminderMinutesAdvance > 0) "${habit.reminderTime} (-${habit.reminderMinutesAdvance}m)" else habit.reminderTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!habitWithStats.isDependencyMet) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🔒 Bloqueado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (isGridView) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isCompleted && habit.unit.isEmpty()) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCompleted && habit.unit.isEmpty()) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Streak Flame Badge
                if (habitWithStats.currentStreak > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF97316).copy(alpha = 0.15f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "🔥", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${habitWithStats.currentStreak}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }
                }

                // Dropdown Menu Button
                Box {
                    IconButton(
                        onClick = { expandedMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cronómetro / Pomodoro") },
                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = habitColor) },
                            onClick = {
                                expandedMenu = false
                                onStartTimer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar hábito") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                expandedMenu = false
                                onEditHabit()
                            }
                        )
                        if (!habit.reminderTime.isNullOrBlank() && onTestReminder != null) {
                            DropdownMenuItem(
                                text = { Text("Probar recordatorio") },
                                leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    expandedMenu = false
                                    onTestReminder()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Archivar") },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                            onClick = {
                                expandedMenu = false
                                onArchiveHabit()
                            }
                        )
                    }
                }
            }

            // Description
            if (habit.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = habit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quantitative Progress Tracker (if habit has units like min, pages, liters)
            if (habit.unit.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentVal.toInt()} / ${targetVal.toInt()} ${habit.unit}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOverachieved) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isOverachieved) {
                        Text(
                            text = "🚀 ¡Meta superada!",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isCompleted) Color(0xFF10B981) else habitColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Sub-tasks expansion toggle if habit has sub-tasks
            if (habitWithStats.subTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSubTasks = !showSubTasks }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val completedSubCount = habitWithStats.subTasks.count { it.isCompleted }
                    Text(
                        text = "Sub-rutinas ($completedSubCount/${habitWithStats.subTasks.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = habitColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (showSubTasks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expandir sub-tareas",
                        tint = habitColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showSubTasks,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        habitWithStats.subTasks.forEach { subTask ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSubTask(subTask, !subTask.isCompleted) }
                                    .padding(vertical = 2.dp),
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
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (subTask.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: Check-in / Numeric Log / Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantitative input button or main check button
                if (habit.unit.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenProgressDialog,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("log_progress_btn_${habit.id}"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Registrar valor",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentVal > 0) "Editar valor" else "Registrar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Dedicated Pomodoro / Timer Button
                FilledTonalButton(
                    onClick = onStartTimer,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("timer_btn_${habit.id}"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Pomodoro y Cronómetro",
                        tint = habitColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${habit.timerDurationMinutes}m",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Main Toggle Check Button
                val buttonColor by animateColorAsState(
                    targetValue = if (isCompleted) Color(0xFF10B981) else habitColor,
                    label = "btnColor"
                )

                Button(
                    onClick = onToggleCompletion,
                    enabled = habitWithStats.isDependencyMet,
                    modifier = Modifier
                        .weight(if (habit.unit.isEmpty()) 1f else 0.8f)
                        .height(40.dp)
                        .testTag("toggle_habit_btn_${habit.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.CheckCircleOutline,
                        contentDescription = if (isCompleted) "Completado" else "Marcar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCompleted) "Hecho" else "Completar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

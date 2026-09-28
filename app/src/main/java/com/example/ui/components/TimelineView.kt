package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HabitWithStats
import com.example.model.SubTask

@Composable
fun TimelineView(
    habits: List<HabitWithStats>,
    onToggleCompletion: (Long) -> Unit,
    onOpenProgressDialog: (HabitWithStats) -> Unit,
    onStartTimer: (HabitWithStats) -> Unit,
    onEditHabit: (HabitWithStats) -> Unit,
    onArchiveHabit: (Long) -> Unit,
    onToggleSubTask: (SubTask, Boolean) -> Unit = { _, _ -> },
    onViewDetail: (HabitWithStats) -> Unit = {},
    onDeleteHabit: ((Long) -> Unit)? = null,
    onTestReminder: ((HabitWithStats) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (habits.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No hay hábitos para mostrar en la línea de tiempo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Orden cronologico: primero "Todo el dia" (sin recordatorio), luego por hora.
    // sortedWith es estable: los empates conservan el orden manual (orderIndex).
    val orderedHabits = remember(habits) {
        habits.sortedWith(compareBy { reminderMinutesOfDay(it.habit.reminderTime) ?: -1 })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(orderedHabits, key = { _, item -> item.habit.id }) { index, habitStat ->
            val habit = habitStat.habit
            val habitColor = try {
                Color(android.graphics.Color.parseColor(habit.colorHex))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primary
            }

            val animatedNodeColor by animateColorAsState(
                targetValue = if (habitStat.isCompletedToday) Color(0xFF10B981) else habitColor,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                label = "timeline_node_color"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                // Time & Node Column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = habit.reminderTime ?: "Todo el día",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(animatedNodeColor)
                    )

                    if (index < orderedHabits.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    HabitTileCard(
                        habitWithStats = habitStat,
                        isGridView = false,
                        onToggleCompletion = { onToggleCompletion(habitStat.habit.id) },
                        onOpenProgressDialog = { onOpenProgressDialog(habitStat) },
                        onStartTimer = { onStartTimer(habitStat) },
                        onToggleSubTask = onToggleSubTask,
                        onEditHabit = { onEditHabit(habitStat) },
                        onArchiveHabit = { onArchiveHabit(habitStat.habit.id) },
                        onViewDetail = { onViewDetail(habitStat) },
                        onDeleteHabit = onDeleteHabit?.let { delete -> { delete(habitStat.habit.id) } },
                        onTestReminder = onTestReminder?.let { test -> { test(habitStat) } }
                    )
                }
            }
        }
    }
}

/** Minutos desde medianoche de una hora "HH:mm" (acepta "8:30"). Null si no hay hora valida. */
private fun reminderMinutesOfDay(time: String?): Int? {
    val parts = time?.trim()?.split(":") ?: return null
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}


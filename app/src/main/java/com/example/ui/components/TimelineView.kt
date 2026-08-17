package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HabitWithStats

@Composable
fun TimelineView(
    habits: List<HabitWithStats>,
    onToggleCompletion: (Long) -> Unit,
    onOpenProgressDialog: (HabitWithStats) -> Unit,
    onStartTimer: (HabitWithStats) -> Unit,
    onEditHabit: (HabitWithStats) -> Unit,
    onArchiveHabit: (Long) -> Unit,
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(habits, key = { _, item -> item.habit.id }) { index, habitStat ->
            val habit = habitStat.habit
            val habitColor = try {
                Color(android.graphics.Color.parseColor(habit.colorHex))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primary
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Time & Node Column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
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
                            .background(if (habitStat.isCompletedToday) Color(0xFF10B981) else habitColor)
                    )

                    if (index < habits.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(140.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Card
                Box(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
                        HabitTileCard(
                            habitWithStats = habitStat,
                            isGridView = false,
                            onToggleCompletion = { onToggleCompletion(habitStat.habit.id) },
                            onOpenProgressDialog = { onOpenProgressDialog(habitStat) },
                            onStartTimer = { onStartTimer(habitStat) },
                            onToggleSubTask = { _, _ -> },
                            onEditHabit = { onEditHabit(habitStat) },
                            onArchiveHabit = { onArchiveHabit(habitStat.habit.id) }
                        )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HabitWithStats

@Composable
fun KanbanView(
    habits: List<HabitWithStats>,
    onToggleCompletion: (Long) -> Unit,
    onOpenProgressDialog: (HabitWithStats) -> Unit,
    onStartTimer: (HabitWithStats) -> Unit,
    onEditHabit: (HabitWithStats) -> Unit,
    onArchiveHabit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val todoHabits = habits.filter { !it.isCompletedToday && (it.todayLog?.value ?: 0f) == 0f }
    val inProgressHabits = habits.filter { !it.isCompletedToday && (it.todayLog?.value ?: 0f) > 0f }
    val completedHabits = habits.filter { it.isCompletedToday }

    Row(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KanbanColumn(
            title = "Por Hacer",
            count = todoHabits.size,
            color = Color(0xFF6366F1),
            habits = todoHabits,
            onToggleCompletion = onToggleCompletion,
            onOpenProgressDialog = onOpenProgressDialog,
            onStartTimer = onStartTimer,
            onEditHabit = onEditHabit,
            onArchiveHabit = onArchiveHabit,
            modifier = Modifier.width(300.dp)
        )

        KanbanColumn(
            title = "En Progreso",
            count = inProgressHabits.size,
            color = Color(0xFFF59E0B),
            habits = inProgressHabits,
            onToggleCompletion = onToggleCompletion,
            onOpenProgressDialog = onOpenProgressDialog,
            onStartTimer = onStartTimer,
            onEditHabit = onEditHabit,
            onArchiveHabit = onArchiveHabit,
            modifier = Modifier.width(300.dp)
        )

        KanbanColumn(
            title = "Completados Hoy",
            count = completedHabits.size,
            color = Color(0xFF10B981),
            habits = completedHabits,
            onToggleCompletion = onToggleCompletion,
            onOpenProgressDialog = onOpenProgressDialog,
            onStartTimer = onStartTimer,
            onEditHabit = onEditHabit,
            onArchiveHabit = onArchiveHabit,
            modifier = Modifier.width(300.dp)
        )
    }
}

@Composable
private fun KanbanColumn(
    title: String,
    count: Int,
    color: Color,
    habits: List<HabitWithStats>,
    onToggleCompletion: (Long) -> Unit,
    onOpenProgressDialog: (HabitWithStats) -> Unit,
    onStartTimer: (HabitWithStats) -> Unit,
    onEditHabit: (HabitWithStats) -> Unit,
    onArchiveHabit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, shape = RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (habits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin hábitos en esta columna",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    items(habits, key = { it.habit.id }) { habitStat ->
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
}

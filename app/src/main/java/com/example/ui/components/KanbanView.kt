package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.HabitWithStats
import kotlinx.coroutines.launch

private data class KanbanColumnSpec(
    val title: String,
    val emptyText: String,
    val badgeColor: Color,
    val habits: List<HabitWithStats>
)

/** Un habito "empezado": tiene avance registrado o al menos un paso marcado. */
private fun isStarted(item: HabitWithStats): Boolean =
    (item.todayLog?.value ?: 0f) > 0f || item.subTasks.any { it.isCompleted }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KanbanView(
    habits: List<HabitWithStats>,
    onPrimaryAction: (HabitWithStats) -> Unit,
    onOpen: (HabitWithStats) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = listOf(
        KanbanColumnSpec(
            title = "Por hacer",
            emptyText = "Nada pendiente por aquí",
            badgeColor = MaterialTheme.colorScheme.primary,
            habits = habits.filter { !it.isCompletedToday && !isStarted(it) }
        ),
        KanbanColumnSpec(
            title = "En curso",
            emptyText = "Aquí aparecen los hábitos que ya empezaste",
            badgeColor = Color(0xFFF59E0B),
            habits = habits.filter { !it.isCompletedToday && isStarted(it) }
        ),
        KanbanColumnSpec(
            title = "Hechos",
            emptyText = "Lo que completes hoy aparece aquí",
            badgeColor = HabitDoneAccent,
            habits = habits.filter { it.isCompletedToday }
        )
    )
    val pagerState = rememberPagerState(pageCount = { columns.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Pestanas con conteo: saltan a la columna sin tener que deslizar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            columns.forEachIndexed { index, column ->
                val selected = pagerState.currentPage == index
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = column.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${column.habits.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = column.badgeColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(column.badgeColor.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Columnas deslizables; se asoma la siguiente para indicar que hay mas
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val column = columns[page]
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                if (column.habits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = column.emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(column.habits, key = { it.habit.id }) { item ->
                            HabitCompactRow(
                                item = item,
                                showTime = true,
                                onPrimaryAction = { onPrimaryAction(item) },
                                onOpen = { onOpen(item) },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitWithStats
import com.example.model.HabitLog
import com.example.util.CalendarDay
import com.example.util.DateUtils
import java.util.Calendar

@Composable
fun CalendarMonthView(
    selectedDate: String,
    habitsWithStats: List<HabitWithStats>,
    allLogs: List<HabitLog>,
    onSelectDate: (String) -> Unit,
    onToggleHabitCompletion: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCalendar = remember {
        Calendar.getInstance()
    }
    var currentYear by remember { mutableStateOf(currentCalendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(currentCalendar.get(Calendar.MONTH)) }

    val monthCalendar = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val daysInMonth = remember(currentYear, currentMonth) {
        DateUtils.getDaysInMonth(currentYear, currentMonth)
    }

    val logsByDate = remember(allLogs) {
        allLogs.groupBy { it.date }
    }

    val totalHabitsCount = maxOf(1, habitsWithStats.size)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Month Navigation Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear--
                            } else {
                                currentMonth--
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Mes anterior"
                        )
                    }

                    Text(
                        text = DateUtils.formatMonthYear(monthCalendar),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear++
                            } else {
                                currentMonth++
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Mes siguiente"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Day of Week Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM").forEach { dayLabel ->
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar Grid Days
                val chunkedDays = daysInMonth.chunked(7)
                chunkedDays.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        week.forEach { day ->
                            if (day.isCurrentMonth) {
                                val completedCount = logsByDate[day.dateString]?.size ?: 0
                                val ratio = (completedCount.toFloat() / totalHabitsCount).coerceIn(0f, 1f)
                                val isSelected = (day.dateString == selectedDate)

                                CalendarDayCell(
                                    day = day,
                                    completionRatio = ratio,
                                    completedCount = completedCount,
                                    isSelected = isSelected,
                                    onClick = { onSelectDate(day.dateString) }
                                )
                            } else {
                                Box(modifier = Modifier.size(38.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Day Habits Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateUtils.formatDateForDisplay(selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val todayLogs = logsByDate[selectedDate] ?: emptyList()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${todayLogs.size}/${habitsWithStats.size} completados",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                habitsWithStats.forEach { hStat ->
                    val habit = hStat.habit
                    val isDone = logsByDate[selectedDate]?.any { it.habitId == habit.id } == true
                    val habitColor = try {
                        Color(android.graphics.Color.parseColor(habit.colorHex))
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleHabitCompletion(habit.id) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isDone) habitColor else habitColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = habit.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = habitColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    day: CalendarDay,
    completionRatio: Float,
    completedCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    completionRatio >= 1f -> Color(0xFF10B981).copy(alpha = 0.25f)
                    completionRatio > 0f -> Color(0xFF10B981).copy(alpha = 0.12f)
                    day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isSelected) 2.dp else if (day.isToday) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else if (day.isToday) MaterialTheme.colorScheme.outline else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${day.dayNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday || isSelected || completionRatio > 0f) FontWeight.Bold else FontWeight.Normal,
                color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            if (completedCount > 0) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }
        }
    }
}

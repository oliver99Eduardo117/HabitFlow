package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.util.DateUtils

@Composable
fun HeatmapView(
    habits: List<Habit>,
    allLogs: List<HabitLog>,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    HabitHeatmap(
        habits = habits,
        allLogs = allLogs,
        modifier = modifier.padding(16.dp),
        weeks = 20,
        onDateSelected = onSelectDate,
        showFilters = true,
        showStatsHeader = true
    )
}

@Composable
fun HeatmapTile(
    ratio: Float,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = getHeatmapColor(ratio)

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable { onClick() }
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

fun getHeatmapColor(ratio: Float): Color {
    return when {
        ratio <= 0f -> Color(0xFF334155).copy(alpha = 0.35f)
        ratio < 0.3f -> Color(0xFF065F46) // Emerald dark
        ratio < 0.6f -> Color(0xFF059669) // Emerald medium
        ratio < 0.9f -> Color(0xFF10B981) // Emerald vibrant
        else -> Color(0xFF34D399) // Emerald high bright
    }
}

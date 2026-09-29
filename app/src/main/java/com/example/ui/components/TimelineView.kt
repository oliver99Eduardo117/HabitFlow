package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitWithStats
import kotlinx.coroutines.delay
import java.time.LocalTime

private enum class DayPeriod(val title: String) {
    ALL_DAY("Todo el día"),
    MORNING("Mañana"),
    AFTERNOON("Tarde"),
    NIGHT("Noche")
}

/** Mañana antes de las 12:00, Tarde de 12:00 a 18:59, Noche desde las 19:00. */
private fun periodOf(minutes: Int?): DayPeriod = when {
    minutes == null -> DayPeriod.ALL_DAY
    minutes < 12 * 60 -> DayPeriod.MORNING
    minutes < 19 * 60 -> DayPeriod.AFTERNOON
    else -> DayPeriod.NIGHT
}

private sealed interface TimelineEntry {
    val key: String

    data class Header(val period: DayPeriod) : TimelineEntry {
        override val key: String get() = "header_${period.name}"
    }

    data class Now(val label: String) : TimelineEntry {
        override val key: String get() = "now"
    }

    data class Item(
        val habit: HabitWithStats,
        val minutes: Int?,
        val isLastInPeriod: Boolean
    ) : TimelineEntry {
        override val key: String get() = "habit_${habit.habit.id}"
    }
}

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun currentMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/**
 * Arma la lista: encabezado por periodo, filas en orden de hora y la marca "Ahora"
 * antes del primer habito con hora posterior a la actual (solo si se ve el dia de hoy).
 */
private fun buildTimelineEntries(habits: List<HabitWithStats>, nowMinutes: Int?): List<TimelineEntry> {
    // sortedBy es estable: los empates conservan el orden manual (orderIndex)
    val sorted = habits
        .map { it to reminderMinutesOfDay(it.habit.reminderTime) }
        .sortedBy { it.second ?: -1 }
    val entries = mutableListOf<TimelineEntry>()
    var nowPlaced = nowMinutes == null || sorted.none { it.second != null }

    DayPeriod.values().forEach { period ->
        val group = sorted.filter { periodOf(it.second) == period }
        if (group.isEmpty()) return@forEach

        val firstMinutes = group.first().second
        if (!nowPlaced && nowMinutes != null && firstMinutes != null && firstMinutes > nowMinutes) {
            entries += TimelineEntry.Now(formatMinutes(nowMinutes))
            nowPlaced = true
        }
        entries += TimelineEntry.Header(period)
        group.forEachIndexed { index, pair ->
            val minutes = pair.second
            if (!nowPlaced && nowMinutes != null && minutes != null && minutes > nowMinutes) {
                entries += TimelineEntry.Now(formatMinutes(nowMinutes))
                nowPlaced = true
            }
            entries += TimelineEntry.Item(pair.first, minutes, index == group.lastIndex)
        }
    }
    if (!nowPlaced && nowMinutes != null) {
        entries += TimelineEntry.Now(formatMinutes(nowMinutes))
    }
    return entries
}

@Composable
fun TimelineView(
    habits: List<HabitWithStats>,
    isToday: Boolean,
    onPrimaryAction: (HabitWithStats) -> Unit,
    onOpen: (HabitWithStats) -> Unit,
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

    // Hora actual, se actualiza al cambiar el minuto
    val nowMinutes by produceState(initialValue = currentMinutes()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = currentMinutes()
        }
    }
    val entries = remember(habits, nowMinutes, isToday) {
        buildTimelineEntries(habits, if (isToday) nowMinutes else null)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is TimelineEntry.Header -> Text(
                    text = entry.period.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
                )
                is TimelineEntry.Now -> NowMarker(label = entry.label)
                is TimelineEntry.Item -> TimelineHabitRow(
                    entry = entry,
                    onPrimaryAction = onPrimaryAction,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun TimelineHabitRow(
    entry: TimelineEntry.Item,
    onPrimaryAction: (HabitWithStats) -> Unit,
    onOpen: (HabitWithStats) -> Unit
) {
    val item = entry.habit
    val state = habitActionState(item)
    val habitColor = habitColorOf(item.habit.colorHex, MaterialTheme.colorScheme.primary)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val showLine = !entry.isLastInPeriod

    // La linea vertical se dibuja detras de la fila (sin medidas intrinsecas)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (showLine) {
                    val x = 23.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 50.dp.toPx()),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .width(46.dp)
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (entry.minutes != null) item.habit.reminderTime.orEmpty() else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.height(16.dp)
            )
            val nodeModifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
            when (state.kind) {
                HabitActionKind.DONE -> Box(modifier = nodeModifier.background(HabitDoneFill))
                HabitActionKind.BLOCKED -> Box(
                    modifier = nodeModifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                else -> Box(modifier = nodeModifier.border(2.dp, habitColor, CircleShape))
            }
        }

        HabitCompactRow(
            item = item,
            showTime = false,
            onPrimaryAction = { onPrimaryAction(item) },
            onOpen = { onOpen(item) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NowMarker(label: String) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(46.dp)
        )
        Text(
            text = "Ahora",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent)
        )
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

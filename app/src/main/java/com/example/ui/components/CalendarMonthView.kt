package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitWithStats
import com.example.ui.theme.Motion
import com.example.util.CalendarDayKind
import com.example.util.CalendarDaySummary
import com.example.util.CalendarMonthSummary
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Numero del dia sobre el disco verde de "dia perfecto". */
private val DayNumberOnDone = Color(0xFF052E1F)

private val MonthNameFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("es-ES"))

private val WeekdayInitials = listOf("L", "M", "M", "J", "V", "S", "D")

private val DayCellShape = RoundedCornerShape(16.dp)

/**
 * Pestana Calendario (Propuesta A, "anillos de progreso").
 *
 * No calcula nada: [month] llega resuelto desde HabitViewModel.calendarMonth y
 * [dayHabits] es uiState.habits, ya calculado para [selectedDate].
 */
@Composable
fun CalendarMonthView(
    month: CalendarMonthSummary?,
    selectedDate: String,
    dayHabits: List<HabitWithStats>,
    onSelectDate: (String) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onGoToday: () -> Unit,
    onPrimaryAction: (HabitWithStats) -> Unit,
    onOpenHabit: (HabitWithStats) -> Unit,
    modifier: Modifier = Modifier
) {
    if (month == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val selected = remember(selectedDate) { runCatching { LocalDate.parse(selectedDate) }.getOrNull() }
    // Puede ser null un instante al cambiar de mes, mientras llega el resumen nuevo
    val selectedDay = selected?.let { month.dayOf(it) }
    val isFuture = selectedDay?.kind == CalendarDayKind.FUTURE
    val countedIds = selectedDay?.scheduledHabitIds ?: emptySet()
    val todo = dayHabits.filter { it.habit.id in countedIds && !it.isCompletedToday }
    val done = dayHabits.filter { it.habit.id in countedIds && it.isCompletedToday }
    val notScheduled = dayHabits.filter { it.habit.id !in countedIds }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header") {
            CalendarHeader(month = month, onShiftMonth = onShiftMonth, onGoToday = onGoToday)
        }
        item(key = "stats") {
            CalendarStatsRow(month = month)
        }
        item(key = "grid") {
            CalendarGridCard(month = month, selected = selected, onSelectDate = onSelectDate)
        }

        if (selectedDay != null) {
            item(key = "day_summary") {
                DaySummaryCard(day = selectedDay)
            }
            if (dayHabits.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Aún no tienes hábitos. Crea el primero desde la pestaña Hoy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
            }
            if (todo.isNotEmpty()) {
                item(key = "todo_label") {
                    SectionLabel(if (isFuture) "PROGRAMADOS · ${todo.size}" else "POR HACER · ${todo.size}")
                }
                items(todo, key = { "todo_${it.habit.id}" }) { item ->
                    HabitCompactRow(
                        item = item,
                        showTime = false,
                        onPrimaryAction = { onPrimaryAction(item) },
                        onOpen = { onOpenHabit(item) },
                        actionEnabled = !isFuture
                    )
                }
            }
            if (done.isNotEmpty()) {
                item(key = "done_label") {
                    SectionLabel("HECHOS · ${done.size}")
                }
                items(done, key = { "done_${it.habit.id}" }) { item ->
                    HabitCompactRow(
                        item = item,
                        showTime = false,
                        onPrimaryAction = { onPrimaryAction(item) },
                        onOpen = { onOpenHabit(item) }
                    )
                }
            }
            if (notScheduled.isNotEmpty()) {
                item(key = "not_scheduled") {
                    NotScheduledLine(habits = notScheduled)
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    month: CalendarMonthSummary,
    onShiftMonth: (Int) -> Unit,
    onGoToday: () -> Unit
) {
    val monthName = remember(month.month) {
        month.month.atDay(1).format(MonthNameFormat).replaceFirstChar { it.uppercase() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text(
                text = monthName,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = month.month.year.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
        FilledTonalIconButton(onClick = { onShiftMonth(-1) }) {
            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
        }
        FilledTonalButton(
            onClick = onGoToday,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(text = "Hoy", fontWeight = FontWeight.Bold)
        }
        FilledTonalIconButton(onClick = { onShiftMonth(1) }) {
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
        }
    }
}

@Composable
private fun CalendarStatsRow(month: CalendarMonthSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CalendarStatTile(
            icon = Icons.Default.CheckCircle,
            iconTint = HabitDoneAccent,
            value = month.perfectDays.toString(),
            label = "días perfectos",
            modifier = Modifier.weight(1f)
        )
        CalendarStatTile(
            icon = Icons.Default.LocalFireDepartment,
            iconTint = HabitStreakColor,
            value = month.perfectStreak.toString(),
            label = "perfectos seguidos",
            modifier = Modifier.weight(1f)
        )
        CalendarStatTile(
            icon = Icons.Default.TrackChanges,
            iconTint = MaterialTheme.colorScheme.primary,
            value = "${month.completionPercent}%",
            label = "cumplido del mes",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CalendarStatTile(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CalendarGridCard(
    month: CalendarMonthSummary,
    selected: LocalDate?,
    onSelectDate: (String) -> Unit
) {
    // Semanas completas de 7 celdas: la ultima fila se rellena para que no se desalinee
    val weeks = remember(month) {
        val cells: List<CalendarDaySummary?> = List(month.leadingBlanks) { null } + month.days
        val trailing = (7 - cells.size % 7) % 7
        (cells + List(trailing) { null }).chunked(7)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                WeekdayInitials.forEach { initial ->
                    Text(
                        text = initial,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                            )
                        } else {
                            CalendarDayCell(
                                day = day,
                                isSelected = day.date == selected,
                                onClick = { onSelectDate(day.dateString) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
            )
            CalendarLegend()
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDaySummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val track = colors.onSurfaceVariant.copy(alpha = 0.22f)
    val targetSweep = if (day.kind == CalendarDayKind.PARTIAL) day.ratio else 0f
    val sweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = Motion.springValues(),
        label = "calendar_day_ring"
    )
    val numberColor = when {
        day.kind == CalendarDayKind.PERFECT -> DayNumberOnDone
        day.kind == CalendarDayKind.FUTURE -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        day.isToday -> colors.primary
        day.kind == CalendarDayKind.MISSED || day.kind == CalendarDayKind.FREE -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val strong = day.isToday || isSelected || day.kind == CalendarDayKind.PERFECT

    Column(
        modifier = modifier
            .height(58.dp)
            .padding(horizontal = 2.dp)
            .clip(DayCellShape)
            .background(if (isSelected) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (isSelected) colors.primary else Color.Transparent,
                shape = DayCellShape
            )
            .clickable(role = Role.Button, onClickLabel = "Ver este día", onClick = onClick)
            .semantics {
                contentDescription = dayDescription(day)
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                // El anillo se dibuja en la fase de dibujo: animar el avance no recompone la celda
                .drawBehind { drawDayRing(day.kind, sweep, track, 3.5.dp) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Medium,
                color = numberColor
            )
        }
        Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
            if (day.isToday) {
                Text(
                    text = "HOY",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = colors.primary
                )
            }
        }
    }
}

/**
 * PERFECT: disco verde lleno. PARTIAL y MISSED: pista gris y arco verde con [sweep] (0 a 1).
 * FREE y FUTURE: nada, solo el numero.
 */
private fun DrawScope.drawDayRing(kind: CalendarDayKind, sweep: Float, track: Color, strokeWidth: Dp) {
    val stroke = strokeWidth.toPx()
    val topLeft = Offset(stroke / 2f, stroke / 2f)
    val arcSize = Size(size.width - stroke, size.height - stroke)
    when (kind) {
        CalendarDayKind.PERFECT -> drawCircle(color = HabitDoneAccent)
        CalendarDayKind.FREE, CalendarDayKind.FUTURE -> Unit
        CalendarDayKind.PARTIAL, CalendarDayKind.MISSED -> {
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (sweep > 0f) {
                drawArc(
                    color = HabitDoneAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun dayDescription(day: CalendarDaySummary): String {
    val date = "${day.date.dayOfMonth} de " + day.date.format(MonthNameFormat)
    val state = when (day.kind) {
        CalendarDayKind.PERFECT -> "día perfecto"
        CalendarDayKind.FREE -> "día libre"
        CalendarDayKind.FUTURE -> "por venir"
        CalendarDayKind.MISSED -> "sin avance"
        CalendarDayKind.PARTIAL -> "${day.completedCount} de ${day.scheduledCount} hábitos"
    }
    return if (day.isToday) "Hoy, $date, $state" else "$date, $state"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = muted.copy(alpha = 0.3f)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendItem(label = "Completo") {
            Box(modifier = Modifier.size(14.dp).drawBehind { drawDayRing(CalendarDayKind.PERFECT, 0f, track, 3.dp) })
        }
        LegendItem(label = "Parcial") {
            Box(modifier = Modifier.size(14.dp).drawBehind { drawDayRing(CalendarDayKind.PARTIAL, 0.5f, track, 3.dp) })
        }
        LegendItem(label = "Sin avance") {
            Box(modifier = Modifier.size(14.dp).drawBehind { drawDayRing(CalendarDayKind.MISSED, 0f, track, 3.dp) })
        }
        LegendItem(label = "Por venir") {
            Text(text = "30", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        swatch()
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DaySummaryCard(day: CalendarDaySummary) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val doneText = if (isDark) HabitDoneAccent else HabitDoneFill
    val pendingText = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val pending = day.scheduledCount - day.completedCount
    val pastHint = "Puedes corregir días pasados si olvidaste registrar algo."

    val whenLabel = when {
        day.isToday -> "Hoy"
        day.kind == CalendarDayKind.FUTURE -> "Por venir"
        else -> "Día pasado"
    }
    val (headline, headlineColor, detail) = when {
        day.kind == CalendarDayKind.FREE -> Triple("Día libre", colors.onSurfaceVariant, "Ningún hábito tocaba este día.")
        day.kind == CalendarDayKind.PERFECT -> Triple(
            "Día perfecto",
            doneText,
            if (day.scheduledCount == 1) "Completaste el hábito programado." else "Completaste los ${day.scheduledCount} hábitos programados."
        )
        day.kind == CalendarDayKind.FUTURE -> Triple(
            "Todavía no llega",
            colors.onSurfaceVariant,
            (if (day.scheduledCount == 1) "1 hábito programado." else "${day.scheduledCount} hábitos programados.") +
                " Podrás marcarlos ese día."
        )
        day.isToday -> Triple(
            if (pending == 1) "Te falta 1 hábito" else "Te faltan $pending hábitos",
            pendingText,
            "Toca el círculo de un hábito para marcarlo."
        )
        day.kind == CalendarDayKind.MISSED -> Triple("Sin avance este día", colors.onSurfaceVariant, pastHint)
        else -> Triple("Completaste ${day.completedCount} de ${day.scheduledCount}", pendingText, pastHint)
    }
    val sweep by animateFloatAsState(
        targetValue = day.ratio,
        animationSpec = Motion.springValues(),
        label = "calendar_summary_ring"
    )
    val track = colors.onSurfaceVariant.copy(alpha = 0.2f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .drawBehind { drawDayRing(CalendarDayKind.PARTIAL, sweep, track, 6.dp) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (day.scheduledCount == 0) "–" else "${day.completedCount}/${day.scheduledCount}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (day.isToday) colors.primary.copy(alpha = 0.18f) else colors.onSurfaceVariant.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = whenLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (day.isToday) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = DateUtils.formatDateForDisplay(day.dateString),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = headline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = headlineColor
                    )
                }
            }
            Text(
                text = detail,
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun NotScheduledLine(habits: List<HabitWithStats>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = muted.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Icons.Default.EventBusy, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        Text(
            text = "No toca este día: " + habits.joinToString(", ") { it.habit.title },
            fontSize = 13.sp,
            color = muted
        )
    }
}

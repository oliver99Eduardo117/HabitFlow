package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.ui.theme.Motion
import com.example.util.CalendarDayKind
import com.example.util.ConstancySummary
import com.example.util.DateUtils
import com.example.util.HabitDayStatus
import com.example.util.HabitStreak
import com.example.util.IconHelper
import com.example.util.ProgressCalculator
import com.example.util.ProgressDay
import com.example.util.ProgressSummary
import com.example.util.WeekdayBreakdown

private val WeekdayInitial = listOf("L", "M", "M", "J", "V", "S", "D")
private val ConstancyCellGap = 3.dp
private val ConstancyMaxCell = 18.dp
private val ConstancyLabelWidth = 14.dp

/** Palomita sobre el disco verde de "cumplido" (igual que el Calendario). */
private val ConstancyCheckOnDone = Color(0xFF052E1F)

/** Encabezado de la tarjeta del dia. */
private class DayHeader(
    val chip: String,
    val chipColor: Color,
    val head: String,
    val headColor: Color,
    val sub: String
)

/** Texto, icono y color de la linea de estado de un habito en la tarjeta del dia. */
private class StatusLine(val text: String, val icon: ImageVector, val color: Color)

/**
 * Seccion Constancia: ¿qué pasó cada día?
 * Mapa de 16 semanas por % cumplido (o en el color del habito elegido), el dia tocado en la misma
 * pantalla, rachas por habito y cumplimiento por dia de la semana. No calcula nada.
 */
@Composable
fun ConstanciaSection(
    summary: ProgressSummary?,
    selectedHabitId: Long?,
    onSelectHabit: (Long?) -> Unit,
    onOpenDayInToday: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summary == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (summary.habits.isEmpty()) {
        ProgressEmptyState("Crea tu primer hábito en Hoy para ver aquí tu constancia.", modifier)
        return
    }

    val constancy = summary.constancy
    // Si el habito elegido ya no existe (archivado o borrado), se muestra Todos
    val habitIndex = summary.habits.indexOfFirst { it.id == selectedHabitId }
    val habit = summary.habits.getOrNull(habitIndex)
    // Vuelve a hoy cuando cambia el dia
    var selectedDate by rememberSaveable(summary.today) { mutableStateOf(summary.today.toString()) }
    val dayIndex = constancy.days.indexOfFirst { it.date.toString() == selectedDate }
        .takeIf { it >= 0 } ?: constancy.days.indexOfFirst { it.isToday }
    val day = constancy.days.getOrNull(dayIndex)
    val listState = rememberLazyListState()

    // Al cambiar de habito (desde los chips, las rachas o Resumen) se sube al mapa para que se vea el cambio.
    // Volver a la seccion sin cambiar de habito conserva el scroll.
    var shownHabitId by rememberSaveable { mutableStateOf(selectedHabitId) }
    LaunchedEffect(selectedHabitId) {
        if (selectedHabitId != shownHabitId) {
            shownHabitId = selectedHabitId
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("progress_constancia"),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "chips") {
            HabitChips(habits = summary.habits, selected = habit, onSelect = onSelectHabit)
        }
        item(key = "tiles") {
            ConstancyTiles(summary = summary, habit = habit, habitIndex = habitIndex)
        }
        item(key = "grid") {
            ConstancyGridCard(
                constancy = constancy,
                habit = habit,
                habitIndex = habitIndex,
                selectedIndex = dayIndex,
                onSelectDay = { index -> selectedDate = constancy.days[index].date.toString() }
            )
        }
        if (day != null) {
            item(key = "day") {
                DayDetailCard(
                    summary = summary,
                    day = day,
                    highlightHabitId = habit?.id,
                    onOpenDayInToday = onOpenDayInToday
                )
            }
        }
        if (habit == null) {
            item(key = "streaks") {
                StreaksCard(summary = summary, onSelectHabit = { id -> onSelectHabit(id) })
            }
        }
        item(key = "weekdays") {
            WeekdayCard(
                breakdown = if (habit == null) constancy.weekdays else constancy.habits[habitIndex].weekdays,
                who = habit?.title ?: "Todos los hábitos",
                color = if (habit == null) HabitDoneAccent else habitAccent(habit)
            )
        }
    }
}

@Composable
private fun HabitChips(habits: List<Habit>, selected: Habit?, onSelect: (Long?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val allSelected = selected == null
    val allShape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(allShape)
                .background(if (allSelected) colors.primary.copy(alpha = 0.22f) else progressCardColor())
                .border(1.5.dp, if (allSelected) colors.primary else Color.Transparent, allShape)
                .clickable(role = Role.RadioButton, onClick = { onSelect(null) })
                .semantics { this.selected = allSelected }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = "Todos", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        habits.forEach { habit ->
            val isSelected = habit.id == selected?.id
            val color = habitAccent(habit)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color else color.copy(alpha = 0.14f))
                    .clickable(role = Role.RadioButton, onClick = { onSelect(habit.id) })
                    .semantics {
                        contentDescription = habit.title
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconHelper.getIconByName(habit.iconName),
                    contentDescription = null,
                    tint = if (isSelected) contentOn(color) else color,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ConstancyTiles(summary: ProgressSummary, habit: Habit?, habitIndex: Int) {
    val c = summary.constancy
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (habit == null) {
            ProgressStatTile(
                icon = Icons.Default.CheckCircle,
                iconTint = HabitDoneAccent,
                value = "${c.percent}%",
                label = "cumplido en 16 semanas",
                modifier = Modifier.weight(1f)
            )
            ProgressStatTile(
                icon = Icons.Default.Verified,
                iconTint = MaterialTheme.colorScheme.primary,
                value = c.perfectDays.toString(),
                label = "días perfectos",
                modifier = Modifier.weight(1f)
            )
            ProgressStatTile(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = HabitStreakColor,
                value = c.perfectStreak.toString(),
                unit = if (c.perfectStreak == 1) "día" else "días",
                label = "perfectos seguidos",
                modifier = Modifier.weight(1f)
            )
        } else {
            val stats = c.habits[habitIndex]
            ProgressStatTile(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = HabitStreakColor,
                value = stats.currentStreak.toString(),
                unit = if (stats.currentStreak == 1) "día" else "días",
                label = "racha de hoy",
                modifier = Modifier.weight(1f)
            )
            ProgressStatTile(
                icon = Icons.Default.EmojiEvents,
                iconTint = progressBadgeAmber(),
                value = stats.bestStreak.toString(),
                unit = if (stats.bestStreak == 1) "día" else "días",
                label = "tu mejor racha",
                modifier = Modifier.weight(1f)
            )
            ProgressStatTile(
                icon = Icons.Default.CheckCircle,
                iconTint = habitAccent(habit),
                value = "${stats.percent}%",
                label = "cumplido en 16 semanas",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConstancyGridCard(
    constancy: ConstancySummary,
    habit: Habit?,
    habitIndex: Int,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (habit == null) HabitDoneAccent else habitAccent(habit)
    val target = habit?.targetValue ?: 1f
    val empty = colors.onSurfaceVariant.copy(alpha = 0.16f)
    val pending = colors.onSurfaceVariant.copy(alpha = 0.10f)
    val upcoming = colors.onSurfaceVariant.copy(alpha = 0.06f)
    val outline = colors.onSurfaceVariant.copy(alpha = 0.3f)
    val todayRing = colors.primary
    val selectedRing = colors.onSurface
    val weeks = constancy.weeks
    // El toque siempre llama a la version mas reciente, aunque pointerInput no se reinicie
    val currentOnSelectDay by rememberUpdatedState(onSelectDay)

    ProgressCard {
        ProgressCardTitle(
            title = "Últimas $weeks semanas",
            subtitle = if (habit == null) "Todos los hábitos" else habit.title + " · " + scheduleLabel(habit)
        )
        BoxWithConstraints(modifier = Modifier.padding(top = 12.dp)) {
            val available = maxWidth - ConstancyLabelWidth - 6.dp
            val cell = minOf((available - ConstancyCellGap * (weeks - 1)) / weeks, ConstancyMaxCell)
            val step = cell + ConstancyCellGap
            val gridWidth = step * weeks - ConstancyCellGap
            val gridHeight = step * 7 - ConstancyCellGap

            Row {
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp, end = 6.dp)
                        .width(ConstancyLabelWidth),
                    verticalArrangement = Arrangement.spacedBy(ConstancyCellGap)
                ) {
                    WeekdayInitial.forEachIndexed { row, letter ->
                        Box(modifier = Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                            if (row % 2 == 0) {
                                Text(text = letter, fontSize = 10.sp, lineHeight = 10.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                Column {
                    // Meses: la etiqueta va sobre la semana donde empieza el mes
                    Box(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(14.dp)
                    ) {
                        constancy.monthLabels.forEachIndexed { week, label ->
                            if (label.isNotEmpty()) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .offset(x = step * week)
                                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Todas las celdas en un solo Canvas. Columna = semana (lunes arriba), fila = dia.
                    Canvas(
                        modifier = Modifier
                            .size(width = gridWidth, height = gridHeight)
                            .semantics {
                                contentDescription = "Mapa de las últimas $weeks semanas. Toca un día para ver qué pasó."
                            }
                            .pointerInput(constancy, step) {
                                detectTapGestures { offset ->
                                    val stepPx = step.toPx()
                                    val col = (offset.x / stepPx).toInt().coerceIn(0, weeks - 1)
                                    val row = (offset.y / stepPx).toInt().coerceIn(0, 6)
                                    val index = col * 7 + row
                                    if (index in constancy.days.indices) currentOnSelectDay(index)
                                }
                            }
                    ) {
                        val stepPx = step.toPx()
                        val cellPx = cell.toPx()
                        val corner = CornerRadius(4.dp.toPx())
                        val cellSize = Size(cellPx, cellPx)
                        constancy.days.forEachIndexed { index, day ->
                            val topLeft = Offset((index / 7) * stepPx, (index % 7) * stepPx)
                            val fill = if (habitIndex < 0) {
                                allHabitsFill(day, accent, empty, upcoming)
                            } else {
                                habitFill(day, habitIndex, target, accent, empty, pending, upcoming)
                            }
                            if (fill != null) drawRoundRect(fill, topLeft, cellSize, corner)
                            val isFree = if (habitIndex < 0) {
                                day.kind == CalendarDayKind.FREE
                            } else {
                                day.statuses[habitIndex] == HabitDayStatus.NOT_SCHEDULED
                            }
                            if (isFree) {
                                drawRoundRect(outline, topLeft, cellSize, corner, style = Stroke(1.dp.toPx()))
                            }
                            if (day.isToday) {
                                drawRoundRect(todayRing, topLeft, cellSize, corner, style = Stroke(2.dp.toPx()))
                            }
                            if (index == selectedIndex) {
                                val grow = 2.dp.toPx()
                                drawRoundRect(
                                    color = selectedRing,
                                    topLeft = Offset(topLeft.x - grow, topLeft.y - grow),
                                    size = Size(cellPx + grow * 2f, cellPx + grow * 2f),
                                    cornerRadius = CornerRadius(5.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }

        if (habit == null) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(text = "0%", fontSize = 11.sp, color = colors.onSurfaceVariant)
                ProgressSwatch(empty)
                ProgressSwatch(HabitDoneAccent.copy(alpha = 0.22f))
                ProgressSwatch(HabitDoneAccent.copy(alpha = 0.42f))
                ProgressSwatch(HabitDoneAccent.copy(alpha = 0.66f))
                ProgressSwatch(HabitDoneAccent)
                Text(text = "100% cumplido", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (habit != null) {
                ProgressLegendItem("Cumplido") { ProgressSwatch(accent) }
                ProgressLegendItem("Parcial") { ProgressSwatch(accent.copy(alpha = 0.44f)) }
                ProgressLegendItem("No hecho") { ProgressSwatch(empty) }
                ProgressLegendItem("No tocaba") { ProgressSwatch(Color.Transparent, borderColor = outline) }
            } else {
                ProgressLegendItem("Por venir") { ProgressSwatch(upcoming) }
                ProgressLegendItem("Día libre") { ProgressSwatch(Color.Transparent, borderColor = outline) }
            }
            ProgressLegendItem("Hoy") { ProgressSwatch(Color.Transparent, borderColor = todayRing, borderWidth = 2.dp) }
        }
    }
}

/** Todos: verde segun el % cumplido de lo que tocaba. Null = sin relleno. */
private fun allHabitsFill(day: ProgressDay, green: Color, empty: Color, upcoming: Color): Color? = when (day.kind) {
    CalendarDayKind.FUTURE -> upcoming
    CalendarDayKind.FREE -> null
    else -> {
        val ratio = day.ratio
        when {
            ratio >= 1f -> green
            ratio >= 0.66f -> green.copy(alpha = 0.66f)
            ratio >= 0.33f -> green.copy(alpha = 0.42f)
            ratio > 0f -> green.copy(alpha = 0.22f)
            else -> empty
        }
    }
}

/** Un habito: su color si cumplio, mas claro segun lo que avanzo si fue parcial, gris si no. Null = sin relleno. */
private fun habitFill(
    day: ProgressDay,
    habitIndex: Int,
    target: Float,
    color: Color,
    empty: Color,
    pending: Color,
    upcoming: Color
): Color? = when (day.statuses[habitIndex]) {
    HabitDayStatus.DONE -> color
    HabitDayStatus.PARTIAL, HabitDayStatus.IN_PROGRESS -> {
        val fraction = if (target > 0f) day.values[habitIndex] / target else 0f
        when {
            fraction >= 0.66f -> color.copy(alpha = 0.66f)
            fraction >= 0.33f -> color.copy(alpha = 0.44f)
            else -> color.copy(alpha = 0.26f)
        }
    }
    HabitDayStatus.MISSED -> empty
    HabitDayStatus.PENDING -> pending
    HabitDayStatus.UPCOMING -> upcoming
    HabitDayStatus.NOT_SCHEDULED, HabitDayStatus.NOT_CREATED -> null
}

private fun scheduleLabel(habit: Habit): String {
    val days = habit.frequencyDays.filter { it in 1..7 }.toSet()
    val names = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    return when {
        days.isEmpty() || days.size == 7 -> "Todos los días"
        days == setOf(1, 2, 3, 4, 5) -> "Lunes a viernes"
        days == setOf(6, 7) -> "Fines de semana"
        else -> {
            val list = days.sorted().map { names[it - 1] }
            val text = if (list.size == 1) list[0] else list.dropLast(1).joinToString(", ") + " y " + list.last()
            text.replaceFirstChar { it.uppercase() }
        }
    }
}

@Composable
private fun DayDetailCard(
    summary: ProgressSummary,
    day: ProgressDay,
    highlightHabitId: Long?,
    onOpenDayInToday: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val green = progressGreenText()
    val amber = progressAmberText()
    val muted = colors.onSurfaceVariant
    val track = muted.copy(alpha = 0.2f)
    val isFuture = day.kind == CalendarDayKind.FUTURE
    val beforeStart = day.statuses.all { it == HabitDayStatus.NOT_CREATED }
    val sweep by animateFloatAsState(
        targetValue = if (isFuture || day.kind == CalendarDayKind.FREE) 0f else day.ratio,
        animationSpec = Motion.springValues(),
        label = "constancy_day_ring"
    )

    val header = when {
        beforeStart -> DayHeader(
            "Sin hábitos", muted, "Todavía no tenías hábitos", muted,
            "Tu historial empieza el día que creaste tu primer hábito."
        )
        day.kind == CalendarDayKind.PERFECT -> DayHeader(
            "Día perfecto", green,
            if (day.scheduledCount == 1) "Cumpliste el único hábito" else "Cumpliste los ${day.scheduledCount} hábitos", green,
            "Todo lo que tocaba ese día quedó hecho."
        )
        day.isToday -> DayHeader(
            "Hoy", colors.primary, "Vas ${day.completedCount} de ${day.scheduledCount}", colors.primary,
            "Lo pendiente no cuenta como fallo hasta que termine el día."
        )
        isFuture -> DayHeader(
            "Por venir", muted,
            when (day.scheduledCount) {
                0 -> "No toca ningún hábito"
                1 -> "1 hábito programado"
                else -> "${day.scheduledCount} hábitos programados"
            },
            muted,
            if (day.scheduledCount == 0) "Ese día es libre." else "Podrás marcarlos ese día."
        )
        day.kind == CalendarDayKind.FREE -> DayHeader(
            "Día libre", muted, "No tocaba ningún hábito", muted,
            "Los días libres no rompen ninguna racha."
        )
        day.kind == CalendarDayKind.MISSED -> DayHeader(
            "Sin cumplir", muted, "No se cumplió ningún hábito", muted,
            "Si lo hiciste y olvidaste registrarlo, puedes corregirlo en Hoy."
        )
        else -> DayHeader(
            "${day.completedCount} de ${day.scheduledCount}", amber,
            "Cumpliste ${day.completedCount} de ${day.scheduledCount}", amber,
            "Los avances parciales se ven, pero solo cuenta llegar a la meta."
        )
    }

    ProgressCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .drawBehind {
                        drawProgressRing(sweep, if (day.isToday) colors.primary else HabitDoneAccent, track, 6.dp)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (day.scheduledCount == 0) "–" else "${day.completedCount}/${day.scheduledCount}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ProgressChip(header.chip, header.chipColor)
                Text(
                    text = DateUtils.formatDateForDisplay(day.date.toString()),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = header.head, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = header.headColor)
            }
        }
        Text(
            text = header.sub,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = muted,
            modifier = Modifier.padding(top = 10.dp)
        )

        val notScheduled = summary.habits.filterIndexed { index, _ -> day.statuses[index] == HabitDayStatus.NOT_SCHEDULED }
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            summary.habits.forEachIndexed { index, habit ->
                val status = day.statuses[index]
                if (status.countsForDay) {
                    DayHabitRow(
                        habit = habit,
                        status = status,
                        value = day.values[index],
                        highlighted = habit.id == highlightHabitId
                    )
                }
            }
        }
        if (notScheduled.isNotEmpty()) {
            NotScheduledNote(text = "No tocaba: " + notScheduled.joinToString(", ") { it.title })
        }

        if (!beforeStart) {
            OutlinedButton(
                onClick = { onOpenDayInToday(day.date.toString()) },
                enabled = !isFuture,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 44.dp)
            ) {
                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isFuture -> "Todavía no se puede registrar"
                        day.isToday -> "Registrar en Hoy"
                        else -> "Corregir este día en Hoy"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DayHabitRow(habit: Habit, status: HabitDayStatus, value: Float, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val color = habitAccent(habit)
    val amount = if (habit.unit.isNotEmpty()) {
        "${formatHabitAmount(value)} / ${formatHabitAmount(habit.targetValue)} ${habit.unit}"
    } else {
        ""
    }
    val line = when (status) {
        HabitDayStatus.DONE -> StatusLine(amount.ifEmpty { "Hecho" }, Icons.Default.CheckCircle, progressGreenText())
        HabitDayStatus.PARTIAL -> StatusLine(
            if (amount.isEmpty()) "Parcial" else "Parcial: $amount",
            Icons.Default.TrackChanges,
            colors.onSurface
        )
        HabitDayStatus.IN_PROGRESS -> StatusLine(
            if (amount.isEmpty()) "En progreso" else "En progreso: $amount",
            Icons.Default.TrackChanges,
            colors.onSurface
        )
        HabitDayStatus.PENDING -> StatusLine(
            if (amount.isEmpty()) "Pendiente" else "Pendiente: $amount",
            Icons.Default.Schedule,
            colors.onSurfaceVariant
        )
        HabitDayStatus.UPCOMING -> StatusLine("Programado", Icons.Default.Event, colors.onSurfaceVariant)
        else -> StatusLine(
            if (amount.isEmpty()) "No hecho" else "No hecho: $amount",
            Icons.Default.Remove,
            colors.onSurfaceVariant
        )
    }
    val shape = RoundedCornerShape(16.dp)
    val fraction = if (habit.targetValue > 0f) (value / habit.targetValue).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) color.copy(alpha = 0.12f) else progressInsetColor())
            .border(1.5.dp, if (highlighted) color.copy(alpha = 0.6f) else Color.Transparent, shape)
            .padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HabitIconTile(habit = habit)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = habit.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = line.icon, contentDescription = null, tint = line.color, modifier = Modifier.size(14.dp))
                Text(text = line.text, fontSize = 12.sp, color = line.color)
            }
        }
        StatusGlyph(status = status, color = color, fraction = fraction)
    }
}

/** Circulo de la derecha: lleno si se cumplio, arco si hubo avance, vacio si falta. */
@Composable
private fun StatusGlyph(status: HabitDayStatus, color: Color, fraction: Float) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onSurfaceVariant.copy(alpha = 0.45f)
    val track = colors.onSurfaceVariant.copy(alpha = 0.25f)
    Box(
        modifier = Modifier
            .size(28.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val radius = size.minDimension / 2f - stroke / 2f
                when (status) {
                    HabitDayStatus.DONE -> drawCircle(color = HabitDoneAccent)
                    HabitDayStatus.PARTIAL, HabitDayStatus.IN_PROGRESS -> drawProgressRing(fraction, color, track, 3.dp)
                    HabitDayStatus.UPCOMING -> drawCircle(
                        color = muted,
                        radius = radius,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                        )
                    )
                    else -> drawCircle(color = muted, radius = radius, style = Stroke(width = stroke))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (status == HabitDayStatus.DONE) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ConstancyCheckOnDone,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NotScheduledNote(text: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = muted.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
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
        Text(text = text, fontSize = 13.sp, color = muted)
    }
}

@Composable
private fun StreaksCard(summary: ProgressSummary, onSelectHabit: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val green = progressGreenText()
    val maxBest = (summary.streaks.maxOfOrNull { it.best } ?: 0).coerceAtLeast(1)
    val rows = summary.streaks.sortedWith(
        compareByDescending<HabitStreak> { it.current }.thenByDescending { it.best }
    )

    ProgressCard {
        ProgressCardTitle(
            title = "Rachas por hábito",
            subtitle = "Días programados seguidos que cumpliste. Los días libres no la rompen."
        )
        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { streak ->
                val habit = summary.habit(streak.habitId) ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Ver el mapa de ${habit.title}",
                            onClick = { onSelectHabit(habit.id) }
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HabitIconTile(habit = habit, size = 36.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = habit.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = daysText(streak.current), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        StreakBar(
                            current = streak.current / maxBest.toFloat(),
                            best = streak.best / maxBest.toFloat(),
                            color = habitAccent(habit)
                        )
                        Text(
                            text = if (streak.isRecord) "Es tu récord" else "Tu mejor racha: ${daysText(streak.best)}",
                            fontSize = 12.sp,
                            color = if (streak.isRecord) green else colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgressLegendItem("Racha de hoy") {
                ProgressSwatch(colors.onSurfaceVariant, shape = RoundedCornerShape(4.dp))
            }
            ProgressLegendItem("Tu mejor racha") {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(12.dp)
                        .background(colors.onSurface, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/** Barra de racha: relleno hasta la racha de hoy y una marca en la mejor racha. */
@Composable
private fun StreakBar(current: Float, best: Float, color: Color) {
    val colors = MaterialTheme.colorScheme
    val track = colors.onSurfaceVariant.copy(alpha = 0.16f)
    val marker = colors.onSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val corner = CornerRadius(barHeight / 2f)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), corner)
        val fill = size.width * current.coerceIn(0f, 1f)
        if (fill > 0f) drawRoundRect(color, Offset(0f, top), Size(fill, barHeight), corner)
        if (best > 0f) {
            val markerWidth = 3.dp.toPx()
            val x = (size.width * best.coerceIn(0f, 1f) - markerWidth).coerceAtLeast(0f)
            drawRoundRect(marker, Offset(x, 0f), Size(markerWidth, size.height), CornerRadius(markerWidth / 2f))
        }
    }
}

@Composable
private fun WeekdayCard(breakdown: WeekdayBreakdown, who: String, color: Color) {
    val colors = MaterialTheme.colorScheme
    ProgressCard {
        ProgressCardTitle(
            title = "Por día de la semana",
            subtitle = "$who · últimas ${ProgressCalculator.WEEKDAY_WEEKS} semanas"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            breakdown.days.forEach { stat ->
                val percent = stat.percent
                val isBest = stat.dayOfWeek in breakdown.best
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {
                            contentDescription = ProgressCalculator.weekdayPlural(stat.dayOfWeek) +
                                if (percent == null) ": no toca" else ": $percent%"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (percent == null) "–" else "$percent%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = if (isBest) colors.onSurface else colors.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (percent == null) 4.dp else (72.dp * (percent / 100f)).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        percent == null -> colors.onSurfaceVariant.copy(alpha = 0.12f)
                                        isBest -> color
                                        else -> color.copy(alpha = 0.38f)
                                    }
                                )
                        )
                    }
                    Text(
                        text = WeekdayInitial[stat.dayOfWeek - 1],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBest) colors.onSurface else colors.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = weekdaySentence(breakdown),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

private fun weekdaySentence(breakdown: WeekdayBreakdown): String {
    fun names(days: List<Int>): String {
        val list = days.map { "los " + ProgressCalculator.weekdayPlural(it) }
        return if (list.size == 1) list[0] else list.dropLast(1).joinToString(", ") + " y " + list.last()
    }
    if (breakdown.best.isEmpty() || breakdown.worst.isEmpty()) {
        val measured = breakdown.days.filter { it.scheduled >= ProgressCalculator.WEEKDAY_MIN_SCHEDULED }
        return if (measured.isEmpty()) {
            "Aún no hay suficientes días para comparar."
        } else {
            "Cumples parejo todos los días que te toca: ${measured.first().percent ?: 0}% cada uno."
        }
    }
    val bestPercent = breakdown.days.first { it.dayOfWeek == breakdown.best.first() }.percent
    val worstPercent = breakdown.days.first { it.dayOfWeek == breakdown.worst.first() }.percent
    val best = names(breakdown.best).replaceFirstChar { it.uppercase() }
    val worst = names(breakdown.worst).replaceFirstChar { it.uppercase() }
    val bestVerb = if (breakdown.best.size == 1) "son tu mejor día" else "son tus mejores días"
    return "$best $bestVerb ($bestPercent%). $worst cuestan más ($worstPercent%)."
}

@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.HabitWithStats
import com.example.util.DateUtils
import com.example.util.IconHelper
import com.example.viewmodel.ActiveTimerState
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val HEATMAP_WEEKS = 18
private val CellSize = 16.dp
private val CellGap = 3.dp
private val DayLabelWidth = 16.dp

/** Estado de un dia en la cuadricula. */
private enum class DayCellState { DONE, PARTIAL, MISSED, NOT_SCHEDULED, FUTURE }

private fun dayStateLabel(state: DayCellState): String = when (state) {
    DayCellState.DONE -> "Hecho"
    DayCellState.PARTIAL -> "A medias"
    DayCellState.MISSED -> "Sin hacer"
    DayCellState.NOT_SCHEDULED -> "No toca"
    DayCellState.FUTURE -> "Pendiente"
}

/** Un dia cuenta como hecho solo si el valor llega a la meta; con avance menor es "a medias". */
private fun dayCellState(
    habit: Habit,
    dateStr: String,
    log: HabitLog?,
    today: LocalDate,
    createdDay: LocalDate
): DayCellState {
    val date = LocalDate.parse(dateStr)
    val value = log?.value ?: 0f
    val target = if (habit.targetValue > 0f) habit.targetValue else 1f
    return when {
        value >= target -> DayCellState.DONE
        date.isAfter(today) -> DayCellState.FUTURE
        value > 0f -> DayCellState.PARTIAL
        date.isBefore(createdDay) -> DayCellState.NOT_SCHEDULED
        date.dayOfWeek.value !in habit.frequencyDays -> DayCellState.NOT_SCHEDULED
        else -> DayCellState.MISSED
    }
}

/** Estado del cronometro para un habito: null si no hay sesion activa de ese habito. */
private data class HabitTimerStatus(val text: String, val isPaused: Boolean)

private fun timerStatusFor(habitId: Long, timer: ActiveTimerState): HabitTimerStatus? {
    if (timer.habitId != habitId || !(timer.isRunning || timer.isPaused)) return null
    val seconds = (if (timer.isPomodoro) timer.remainingSeconds else timer.elapsedSeconds).coerceAtLeast(0)
    val clock = "%02d:%02d".format(seconds / 60, seconds % 60)
    return HabitTimerStatus(
        text = if (timer.isPaused) "En pausa $clock" else "En curso $clock",
        isPaused = timer.isPaused
    )
}

private fun frequencyLabel(days: List<Int>): String {
    val set = days.toSet()
    return when {
        set.size >= 7 -> "Todos los días"
        set == setOf(1, 2, 3, 4, 5) -> "Lunes a viernes"
        set == setOf(6, 7) -> "Fines de semana"
        set.isEmpty() -> "Sin días asignados"
        else -> {
            val names = listOf("L", "M", "X", "J", "V", "S", "D")
            set.sorted().joinToString(" ") { names.getOrElse(it - 1) { "" } }
        }
    }
}

@Composable
fun HabitsHeatmapLayout(
    habits: List<HabitWithStats>,
    allLogs: List<HabitLog>,
    onToggleCompletion: (Long) -> Unit,
    onOpenProgressDialog: (HabitWithStats) -> Unit,
    onToggleDateCompletion: (Long, String) -> Unit,
    onEditHabit: (HabitWithStats) -> Unit,
    modifier: Modifier = Modifier,
    onStartTimer: (HabitWithStats) -> Unit = {},
    activeTimer: ActiveTimerState = ActiveTimerState()
) {
    if (habits.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No hay hábitos para mostrar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crea un hábito o ajusta los filtros de búsqueda",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    // Una sola agrupacion de registros para todas las tarjetas
    val logsByHabit = remember(allLogs) {
        allLogs.groupBy { it.habitId }.mapValues { entry -> entry.value.associateBy { it.date } }
    }
    // La matriz se recalcula si cambia el dia (termina en la semana actual)
    val todayStr = DateUtils.getTodayDateString()
    val dateMatrix = remember(todayStr) { DateUtils.getHeatmapDateMatrix(weeks = HEATMAP_WEEKS) }
    val monthPositions = remember(dateMatrix) { calculateMonthPositions(dateMatrix) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("habits_heatmap_list_layout"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "heatmap_legend") {
            HeatmapLegend()
        }
        items(habits, key = { it.habit.id }) { habitWithStats ->
            SingleHabitHeatmapCard(
                habitWithStats = habitWithStats,
                logsByDate = logsByHabit[habitWithStats.habit.id].orEmpty(),
                todayStr = todayStr,
                dateMatrix = dateMatrix,
                monthPositions = monthPositions,
                onPrimaryAction = {
                    if (habitWithStats.habit.unit.isNotEmpty()) {
                        onOpenProgressDialog(habitWithStats)
                    } else {
                        onToggleCompletion(habitWithStats.habit.id)
                    }
                },
                onToggleDate = { dateStr -> onToggleDateCompletion(habitWithStats.habit.id, dateStr) },
                onEditHabit = { onEditHabit(habitWithStats) },
                // Solo la tarjeta del habito con cronometro activo recibe el texto; las demas no se recomponen
                timerStatus = timerStatusFor(habitWithStats.habit.id, activeTimer),
                onStartTimer = { onStartTimer(habitWithStats) }
            )
        }
    }
}

@Composable
private fun HeatmapLegend() {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val dot = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurface

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendItem("No toca") {
            val r = size.minDimension
            drawRoundRect(color = empty, cornerRadius = CornerRadius(r * 0.25f), style = Stroke(1.dp.toPx()))
            drawCircle(color = dot, radius = r * 0.17f)
        }
        LegendItem("Sin hacer") {
            drawRoundRect(color = empty, cornerRadius = CornerRadius(size.minDimension * 0.25f))
        }
        LegendItem("A medias") {
            drawRoundRect(color = accent.copy(alpha = 0.42f), cornerRadius = CornerRadius(size.minDimension * 0.25f))
        }
        LegendItem("Hecho") {
            drawRoundRect(color = accent, cornerRadius = CornerRadius(size.minDimension * 0.25f))
        }
        LegendItem("Hoy") {
            drawRoundRect(color = empty, cornerRadius = CornerRadius(size.minDimension * 0.25f))
            drawRoundRect(
                color = ring,
                cornerRadius = CornerRadius(size.minDimension * 0.25f),
                style = Stroke(1.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp), onDraw = draw)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SingleHabitHeatmapCard(
    habitWithStats: HabitWithStats,
    logsByDate: Map<String, HabitLog>,
    todayStr: String,
    dateMatrix: List<List<String>>,
    monthPositions: List<HeatmapMonthPosition>,
    onPrimaryAction: () -> Unit,
    onToggleDate: (String) -> Unit,
    onEditHabit: () -> Unit,
    timerStatus: HabitTimerStatus?,
    onStartTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val habitColor = habitColorOf(habit.colorHex, MaterialTheme.colorScheme.primary)
    val actionState = habitActionState(habitWithStats)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val today = remember(todayStr) { LocalDate.parse(todayStr) }
    val createdDay = remember(habit.createdAt) {
        Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    // Estado de cada celda, por semana (columna) y dia (fila, lunes a domingo)
    val cellStates = remember(habit, logsByDate, dateMatrix, today, createdDay) {
        dateMatrix.map { week ->
            week.map { dateStr -> dayCellState(habit, dateStr, logsByDate[dateStr], today, createdDay) }
        }
    }
    // La semana actual es la ultima columna de la matriz
    val thisWeek = cellStates.lastOrNull().orEmpty()
    val weekDone = thisWeek.count { it == DayCellState.DONE }
    val weekPlanned = dateMatrix.lastOrNull().orEmpty().count { dateStr ->
        LocalDate.parse(dateStr).dayOfWeek.value in habit.frequencyDays
    }

    var selectedDate by remember(habit.id) { mutableStateOf<String?>(null) }

    val subtitle = buildList {
        add(frequencyLabel(habit.frequencyDays))
        if (habit.unit.isNotEmpty()) {
            val value = habitWithStats.todayLog?.value ?: 0f
            add("${formatHabitAmount(value)} / ${formatHabitAmount(habit.targetValue)} ${habit.unit}")
        } else if (habitWithStats.subTasks.isNotEmpty()) {
            add("${habitWithStats.subTasks.size} pasos")
        }
    }.joinToString(" · ")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_heatmap_card_${habit.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabecera: icono, nombre, frecuencia y accion del dia
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(habitColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconHelper.getIconByName(habit.iconName),
                        contentDescription = null,
                        tint = habitColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = habit.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEditHabit, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar ${habit.title}",
                        tint = muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                HabitActionRing(
                    state = actionState,
                    color = habitColor,
                    label = habitActionLabel(habitWithStats),
                    onClick = onPrimaryAction
                )
            }

            // Datos clave
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatChip("Racha: ${habitWithStats.currentStreak} ${if (habitWithStats.currentStreak == 1) "día" else "días"}")
                StatChip("Mejor: ${habitWithStats.bestStreak} ${if (habitWithStats.bestStreak == 1) "día" else "días"}")
                StatChip("Esta semana: $weekDone de $weekPlanned")
                // Temporizador: solo si el habito lo tiene y su requisito ya se cumplio
                if (habit.hasTimer && habitWithStats.isDependencyMet) {
                    TimerChip(
                        status = timerStatus,
                        habitTitle = habit.title,
                        onClick = onStartTimer,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            // Cuadricula: meses arriba, letras de dia a la izquierda
            HeatmapGrid(
                dateMatrix = dateMatrix,
                cellStates = cellStates,
                monthPositions = monthPositions,
                todayStr = todayStr,
                selectedDate = selectedDate,
                habitColor = habitColor,
                onSelect = { dateStr ->
                    selectedDate = if (selectedDate == dateStr) null else dateStr
                }
            )

            // Barra del dia seleccionado: el cambio siempre es con un boton explicito
            val selected = selectedDate
            if (selected != null) {
                val week = dateMatrix.indexOfFirst { selected in it }
                val state = if (week >= 0) cellStates[week][dateMatrix[week].indexOf(selected)] else DayCellState.MISSED
                val isDone = state == DayCellState.DONE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (state) {
                                    DayCellState.DONE -> habitColor
                                    DayCellState.PARTIAL -> habitColor.copy(alpha = 0.42f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                    Text(
                        text = "${DateUtils.formatDateForDisplay(selected)} · ${dayStateLabel(state)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onToggleDate(selected)
                            selectedDate = null
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (isDone) "Desmarcar" else "Marcar hecho",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * Boton del temporizador. Se ve de 32dp pero su area de toque es de 48dp.
 * En reposo dice "Temporizador"; con la sesion activa muestra el tiempo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerChip(
    status: HabitTimerStatus?,
    habitTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = status != null
    val container = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val content = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val label = status?.text ?: "Temporizador"

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = "$label, ${habitTitle}" }
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when {
                    status == null -> Icons.Default.Timer
                    status.isPaused -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeatmapGrid(
    dateMatrix: List<List<String>>,
    cellStates: List<List<DayCellState>>,
    monthPositions: List<HeatmapMonthPosition>,
    todayStr: String,
    selectedDate: String?,
    habitColor: Color,
    onSelect: (String) -> Unit
) {
    val weeks = dateMatrix.size
    val step = CellSize + CellGap
    val gridWidth = step * weeks - CellGap
    val gridHeight = step * 7 - CellGap

    val empty = MaterialTheme.colorScheme.surfaceVariant
    val dot = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val todayRing = MaterialTheme.colorScheme.onSurface
    val selectedRing = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Fila de meses: su alto sigue al tamano de letra del sistema para que el texto no se corte
    val monthLineHeight = 16.sp
    val monthRowHeight = with(LocalDensity.current) { monthLineHeight.toDp() }
    val monthStyle = TextStyle(fontSize = 11.sp, lineHeight = monthLineHeight, color = muted)

    // Siempre inicia en la semana actual (al final). Se desplaza cuando se conoce el ancho real.
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue in 1 until Int.MAX_VALUE) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Row {
        // Letras de dia: L, X, V, D (lunes, miercoles, viernes, domingo)
        Column(
            modifier = Modifier
                .padding(top = monthRowHeight + 4.dp, end = 4.dp)
                .width(DayLabelWidth),
            verticalArrangement = Arrangement.spacedBy(CellGap)
        ) {
            listOf("L", "", "X", "", "V", "", "D").forEach { letter ->
                Box(modifier = Modifier.height(CellSize), contentAlignment = Alignment.CenterEnd) {
                    Text(text = letter, fontSize = 10.sp, color = muted)
                }
            }
        }

        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Meses
            Box(
                modifier = Modifier
                    .width(gridWidth)
                    .height(monthRowHeight)
            ) {
                monthPositions.forEach { position ->
                    Text(
                        text = position.monthName,
                        style = monthStyle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .offset(x = step * position.weekIndex)
                            .wrapContentWidth(unbounded = true)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Todas las celdas en un solo Canvas; un toque solo selecciona el dia
            Canvas(
                modifier = Modifier
                    .size(width = gridWidth, height = gridHeight)
                    .semantics { contentDescription = "Historial de las últimas $weeks semanas" }
                    .pointerInput(dateMatrix, cellStates) {
                        detectTapGestures { offset ->
                            val stepPx = step.toPx()
                            val cellPx = CellSize.toPx()
                            val col = (offset.x / stepPx).toInt()
                            val row = (offset.y / stepPx).toInt()
                            val insideCell = offset.x - col * stepPx <= cellPx && offset.y - row * stepPx <= cellPx
                            if (insideCell && col in dateMatrix.indices && row in 0..6) {
                                if (cellStates[col][row] != DayCellState.FUTURE) {
                                    onSelect(dateMatrix[col][row])
                                }
                            }
                        }
                    }
            ) {
                val stepPx = step.toPx()
                val cellPx = CellSize.toPx()
                val corner = CornerRadius(4.dp.toPx())
                val cellSize = Size(cellPx, cellPx)

                dateMatrix.forEachIndexed { col, week ->
                    week.forEachIndexed { row, dateStr ->
                        val topLeft = Offset(col * stepPx, row * stepPx)
                        when (cellStates[col][row]) {
                            DayCellState.DONE -> drawRoundRect(habitColor, topLeft, cellSize, corner)
                            DayCellState.PARTIAL -> drawRoundRect(habitColor.copy(alpha = 0.42f), topLeft, cellSize, corner)
                            DayCellState.MISSED -> drawRoundRect(empty, topLeft, cellSize, corner)
                            DayCellState.NOT_SCHEDULED -> {
                                drawRoundRect(empty, topLeft, cellSize, corner, style = Stroke(1.dp.toPx()))
                                drawCircle(
                                    color = dot,
                                    radius = cellPx * 0.14f,
                                    center = Offset(topLeft.x + cellPx / 2f, topLeft.y + cellPx / 2f)
                                )
                            }
                            DayCellState.FUTURE -> Unit
                        }
                        if (dateStr == todayStr) {
                            drawRoundRect(todayRing, topLeft, cellSize, corner, style = Stroke(1.5.dp.toPx()))
                        }
                        if (dateStr == selectedDate) {
                            val grow = 1.5.dp.toPx()
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
}

private data class HeatmapMonthPosition(
    val monthName: String,
    val weekIndex: Int
)

private fun calculateMonthPositions(dateMatrix: List<List<String>>): List<HeatmapMonthPosition> {
    if (dateMatrix.isEmpty()) return emptyList()
    val positions = mutableListOf<HeatmapMonthPosition>()
    val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMM", Locale("es", "ES"))

    var lastMonth = ""
    var lastAddedWeek = -10

    dateMatrix.forEachIndexed { weekIndex, week ->
        val firstOfMonth = week.find { it.endsWith("-01") }
        val targetDay = firstOfMonth ?: week.getOrNull(3) ?: week.firstOrNull()
        val monthStr = if (targetDay != null) {
            try {
                val d = iso.parse(targetDay)
                // Algunos Android devuelven "sept." o "ago."; se deja en tres letras y sin punto
                if (d != null) monthFmt.format(d).replace(".", "").take(3).replaceFirstChar { it.uppercase() } else ""
            } catch (_: Exception) { "" }
        } else ""

        if (monthStr.isNotEmpty()) {
            if (weekIndex == 0) {
                positions.add(HeatmapMonthPosition(monthStr, 0))
                lastMonth = monthStr
                lastAddedWeek = 0
            } else if (monthStr != lastMonth && (weekIndex - lastAddedWeek) >= 3) {
                positions.add(HeatmapMonthPosition(monthStr, weekIndex))
                lastMonth = monthStr
                lastAddedWeek = weekIndex
            }
        }
    }
    return positions
}

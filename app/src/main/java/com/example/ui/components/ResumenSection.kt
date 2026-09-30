package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.ui.theme.Motion
import com.example.util.CalendarDayKind
import com.example.util.HabitPeriodStat
import com.example.util.InsightKind
import com.example.util.NearMilestone
import com.example.util.PeriodSummary
import com.example.util.ProgressCalculator
import com.example.util.ProgressDay
import com.example.util.ProgressInsight
import com.example.util.ProgressPeriod
import com.example.util.ProgressSummary
import kotlin.math.abs

/** Numero del dia sobre el disco verde de "dia perfecto" (igual que el Calendario). */
private val ResumenNumberOnDone = Color(0xFF052E1F)

private val WeekdayShort = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/**
 * Seccion Resumen (antes Análisis): ¿cómo voy?
 * No calcula nada: todo llega en [summary] desde HabitViewModel.progress.
 */
@Composable
fun ResumenSection(
    summary: ProgressSummary?,
    aiInsights: List<String>,
    isLoadingAi: Boolean,
    aiConfigured: Boolean,
    onAskAi: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenAchievements: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (summary == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (summary.habits.isEmpty()) {
        ProgressEmptyState("Crea tu primer hábito en Hoy para ver aquí cómo vas.", modifier)
        return
    }

    var period by rememberSaveable { mutableStateOf(ProgressPeriod.WEEK) }
    val data = summary.period(period)
    val near = summary.nearMilestones.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("progress_resumen"),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "periods") {
            PeriodSelector(selected = period, onSelect = { period = it })
        }
        item(key = "hero") {
            ResumenHero(summary = summary, data = data)
        }
        item(key = "tiles") {
            ResumenTiles(summary = summary, data = data)
        }
        item(key = "habits") {
            ResumenHabits(summary = summary, data = data, onOpenHabit = onOpenHabit)
        }
        if (near != null) {
            item(key = "near") {
                NearMilestoneTeaser(summary = summary, near = near, onClick = onOpenAchievements)
            }
        }
        item(key = "insights") {
            InsightsCard(
                summary = summary,
                aiInsights = aiInsights,
                isLoadingAi = isLoadingAi,
                aiConfigured = aiConfigured,
                onAskAi = onAskAi,
                onOpenAiSettings = onOpenAiSettings
            )
        }
    }
}

@Composable
private fun PeriodSelector(selected: ProgressPeriod, onSelect: (ProgressPeriod) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ProgressPeriod.WEEK to "Semana",
            ProgressPeriod.MONTH to "Mes",
            ProgressPeriod.YEAR to "Año"
        ).forEach { (value, label) ->
            val isSelected = value == selected
            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(shape)
                    .background(if (isSelected) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.5.dp, if (isSelected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.3f), shape)
                    .clickable(role = Role.RadioButton, onClick = { onSelect(value) })
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResumenHero(summary: ProgressSummary, data: PeriodSummary) {
    val colors = MaterialTheme.colorScheme
    val green = progressGreenText()
    val amber = progressAmberText()
    val fraction by animateFloatAsState(
        targetValue = data.percent / 100f,
        animationSpec = Motion.springValues(),
        label = "resumen_ring"
    )
    val track = colors.onSurfaceVariant.copy(alpha = 0.18f)
    val hasData = data.scheduled > 0

    val (verdict, verdictColor) = when {
        !hasData -> "Aún sin días programados" to colors.onSurfaceVariant
        data.percent >= 85 -> verdictText(data.period, 0) to green
        data.percent >= 70 -> verdictText(data.period, 1) to green
        data.percent >= 50 -> verdictText(data.period, 2) to green
        else -> verdictText(data.period, 3) to amber
    }

    ProgressCard(contentPadding = PaddingValues(20.dp)) {
        Text(
            text = rangeLabel(data),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = colors.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .drawBehind { drawProgressRing(fraction, HabitDoneAccent, track, 12.dp) }
                    .clearAndSetSemantics { contentDescription = "${data.percent}% cumplido" },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (hasData) "${data.percent}%" else "–",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(text = "cumplido", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = verdict,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = verdictColor
                )
                if (hasData) {
                    Text(
                        text = buildAnnotatedString {
                            append("Cumpliste ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.onSurface)) {
                                append("${formatThousands(data.completed)} de ${formatThousands(data.scheduled)}")
                            }
                            append(" hábitos programados")
                        },
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Cuando termine un día con hábitos programados, aquí verás cuánto cumpliste.",
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                DeltaChip(summary = summary, data = data)
            }
        }

        when (data.period) {
            ProgressPeriod.WEEK -> WeekRings(days = data.days)
            ProgressPeriod.MONTH -> MonthBars(days = data.days)
            ProgressPeriod.YEAR -> YearBars(data = data)
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
            color = colors.onSurfaceVariant.copy(alpha = 0.12f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgressLegendItem("Cumplido") { ProgressSwatch(HabitDoneAccent, shape = RoundedCornerShape(4.dp)) }
            ProgressLegendItem("Sin cumplir") { ProgressSwatch(track, shape = RoundedCornerShape(4.dp)) }
            if (data.period == ProgressPeriod.MONTH) {
                ProgressLegendItem("Hoy, en curso") { ProgressSwatch(colors.primary, shape = RoundedCornerShape(4.dp)) }
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Lo pendiente de hoy no baja tu porcentaje: se cuenta cuando termina el día.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun verdictText(period: ProgressPeriod, level: Int): String = when (period) {
    ProgressPeriod.WEEK -> listOf("Excelente semana", "Muy buena semana", "Buena semana", "Semana difícil")[level]
    ProgressPeriod.MONTH -> listOf("Excelente mes", "Muy buen mes", "Buen mes", "Mes difícil")[level]
    ProgressPeriod.YEAR -> listOf("Ritmo excelente", "Muy buen ritmo", "Buen ritmo", "Ritmo irregular")[level]
}

private fun rangeLabel(data: PeriodSummary): String {
    val start = data.start
    val end = data.end
    val startMonth = ProgressCalculator.monthShort(start.monthValue)
    val endMonth = ProgressCalculator.monthShort(end.monthValue)
    val text = when {
        data.period == ProgressPeriod.YEAR && start.year != end.year -> "$startMonth ${start.year} – $endMonth ${end.year}"
        data.period == ProgressPeriod.YEAR -> "$startMonth – $endMonth ${end.year}"
        start.month == end.month -> "${start.dayOfMonth} – ${end.dayOfMonth} $endMonth"
        else -> "${start.dayOfMonth} $startMonth – ${end.dayOfMonth} $endMonth"
    }
    return text.uppercase()
}

@Composable
private fun DeltaChip(summary: ProgressSummary, data: PeriodSummary) {
    val colors = MaterialTheme.colorScheme
    val versus = if (data.period == ProgressPeriod.WEEK) "la semana anterior" else "los 30 días anteriores"
    val delta = data.deltaPoints
    val (text, icon, color) = when {
        data.period == ProgressPeriod.YEAR -> {
            val first = summary.firstDay
            val label = if (data.start == first) {
                "Empezaste el ${first.dayOfMonth} de ${ProgressCalculator.monthFull(first.monthValue)}"
            } else {
                "Últimos 12 meses"
            }
            Triple(label, Icons.Default.Flag, colors.onSurfaceVariant)
        }
        delta == null -> Triple("Aún no hay datos de $versus para comparar", Icons.AutoMirrored.Filled.TrendingFlat, colors.onSurfaceVariant)
        delta > 0 -> Triple("+${pointsText(delta)} vs. $versus", Icons.AutoMirrored.Filled.TrendingUp, progressGreenText())
        delta < 0 -> Triple("-${pointsText(abs(delta))} vs. $versus", Icons.AutoMirrored.Filled.TrendingDown, progressAmberText())
        else -> Triple("Igual que $versus", Icons.AutoMirrored.Filled.TrendingFlat, colors.onSurfaceVariant)
    }
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun pointsText(points: Int): String = if (points == 1) "1 punto" else "$points puntos"

@Composable
private fun WeekRings(days: List<ProgressDay>) {
    val colors = MaterialTheme.colorScheme
    val track = colors.onSurfaceVariant.copy(alpha = 0.22f)
    Row(modifier = Modifier.padding(top = 20.dp)) {
        days.forEach { day ->
            val perfect = day.kind == CalendarDayKind.PERFECT
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = "${day.date.dayOfMonth}: ${day.completedCount} de ${day.scheduledCount}" +
                            if (day.isToday) ", hoy en curso" else ""
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = WeekdayShort[day.date.dayOfWeek.value - 1],
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day.isToday) colors.primary else colors.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .drawBehind {
                            when {
                                perfect -> drawCircle(color = HabitDoneAccent)
                                day.kind == CalendarDayKind.FREE || day.kind == CalendarDayKind.FUTURE -> Unit
                                else -> drawProgressRing(day.ratio, HabitDoneAccent, track, 4.dp)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.date.dayOfMonth.toString(),
                        fontSize = 13.sp,
                        fontWeight = if (perfect || day.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                        color = when {
                            perfect -> ResumenNumberOnDone
                            day.isToday -> colors.primary
                            else -> colors.onSurface
                        }
                    )
                }
                Text(
                    text = if (day.isToday) "HOY" else "",
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

@Composable
private fun MonthBars(days: List<ProgressDay>) {
    val colors = MaterialTheme.colorScheme
    val track = colors.onSurfaceVariant.copy(alpha = 0.16f)
    val todayColor = colors.primary
    val done = days.sumOf { it.completedCount }
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .semantics { contentDescription = "Cumplimiento de cada uno de los últimos 30 días; $done hábitos cumplidos" }
        ) {
            val gap = 3.dp.toPx()
            val barWidth = (size.width - gap * (days.size - 1)) / days.size
            val corner = CornerRadius(3.dp.toPx())
            days.forEachIndexed { index, day ->
                val x = index * (barWidth + gap)
                drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), corner)
                val h = size.height * day.ratio
                if (h > 0f) {
                    drawRoundRect(
                        color = if (day.isToday) todayColor else HabitDoneAccent,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = corner
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(top = 6.dp)) {
            days.forEachIndexed { index, day ->
                val label = when {
                    index == 0 -> "${day.date.dayOfMonth} ${ProgressCalculator.monthShort(day.date.monthValue)}"
                    // Lunes, salvo que choque con la etiqueta del primer dia
                    index >= 4 && day.date.dayOfWeek.value == 1 -> day.date.dayOfMonth.toString()
                    else -> ""
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearBars(data: PeriodSummary) {
    val colors = MaterialTheme.colorScheme
    val track = colors.onSurfaceVariant.copy(alpha = 0.16f)
    val currentText = progressGreenText()
    Text(
        text = "% cumplido de cada mes",
        fontSize = 11.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp)
    )
    // Hasta 12 columnas: sin el signo % y a 10 sp para que "100" y "may" quepan a 360 dp
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        data.months.forEach { month ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = ProgressCalculator.monthFull(month.month.monthValue) + ": ${month.percent}%" +
                            if (month.isCurrent) ", en curso" else ""
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = month.percent.toString(),
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    color = if (month.isCurrent) currentText else colors.onSurface
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(track),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (month.percent > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((month.percent / 100f).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(8.dp))
                                .background(HabitDoneAccent)
                        )
                    }
                }
                Text(
                    text = ProgressCalculator.monthShort(month.month.monthValue),
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    color = if (month.isCurrent) colors.onSurface else colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResumenTiles(summary: ProgressSummary, data: PeriodSummary) {
    val top = summary.streaks.maxByOrNull { it.current }
    val topHabit = top?.let { summary.habit(it.habitId) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressStatTile(
            icon = Icons.Default.CheckCircle,
            iconTint = HabitDoneAccent,
            value = data.perfectDays.toString(),
            label = "días perfectos",
            modifier = Modifier.weight(1f)
        )
        ProgressStatTile(
            icon = Icons.Default.Verified,
            iconTint = MaterialTheme.colorScheme.primary,
            value = "${data.strongHabits}/${data.habits.size}",
            label = "hábitos con 80% o más",
            modifier = Modifier.weight(1f)
        )
        ProgressStatTile(
            icon = Icons.Default.LocalFireDepartment,
            iconTint = HabitStreakColor,
            value = (top?.current ?: 0).toString(),
            label = if (top != null && top.current > 0 && topHabit != null) "días seguidos de ${topHabit.title}" else "sin racha activa",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ResumenHabits(summary: ProgressSummary, data: PeriodSummary, onOpenHabit: (Long) -> Unit) {
    ProgressCard(contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 8.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Tus hábitos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Del mejor al que más cuesta",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (data.habits.isEmpty()) {
            Text(
                text = "Ningún hábito tocaba en este periodo.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp)
            )
        }
        data.habits.forEachIndexed { index, row ->
            val habit = summary.habit(row.habitId) ?: return@forEachIndexed
            HabitPeriodRow(
                habit = habit,
                row = row,
                isBest = index == 0 && row.percent >= ProgressCalculator.STRONG_PERCENT,
                onClick = { onOpenHabit(habit.id) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitPeriodRow(habit: Habit, row: HabitPeriodStat, isBest: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = habitAccent(habit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Ver su historial en Constancia", onClick = onClick)
            .padding(start = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HabitIconTile(habit = habit)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = habit.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    when {
                        isBest -> ProgressChip("Tu mejor hábito", progressGreenText(), Icons.Default.Star)
                        row.percent < ProgressCalculator.WEAK_PERCENT -> ProgressChip("Necesita atención", progressAmberText(), Icons.Default.PriorityHigh)
                    }
                }
                Text(
                    text = "${row.percent}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = "${formatThousands(row.completed)} de ${formatThousands(row.scheduled)} días · racha de ${daysText(row.currentStreak)}",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ProgressBar(fraction = row.percent / 100f, color = color)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NearMilestoneTeaser(summary: ProgressSummary, near: NearMilestone, onClick: () -> Unit) {
    val habit = summary.habit(near.habitId) ?: return
    val color = habitAccent(habit)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = progressCardColor(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Ver Logros", onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.MilitaryTech, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "MUY CERCA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = colors.onSurfaceVariant
                )
                Text(text = "Racha de ${near.target} días en ${habit.title}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = near.hint, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            if (near.xpBonus > 0) {
                ProgressChip("+${formatThousands(near.xpBonus)} XP", colors.primary)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun InsightsCard(
    summary: ProgressSummary,
    aiInsights: List<String>,
    isLoadingAi: Boolean,
    aiConfigured: Boolean,
    onAskAi: () -> Unit,
    onOpenAiSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ProgressCard {
        ProgressCardTitle(title = "Lo que notamos", subtitle = "Calculado en tu teléfono con tus registros")
        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            summary.insights.forEach { insight ->
                InsightRow(summary = summary, insight = insight)
            }
        }

        if (aiInsights.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                color = colors.onSurfaceVariant.copy(alpha = 0.12f)
            )
            Text(text = "Resumen de la IA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                aiInsights.forEach { text ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Text(text = text, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        TextButton(
            onClick = if (aiConfigured) onAskAi else onOpenAiSettings,
            enabled = !isLoadingAi,
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 44.dp)
        ) {
            if (isLoadingAi) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !aiConfigured -> "Configurar IA para pedir resúmenes"
                    isLoadingAi -> "Pidiendo resumen..."
                    aiInsights.isEmpty() -> "Pedir un resumen a la IA"
                    else -> "Pedir otro resumen"
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InsightRow(summary: ProgressSummary, insight: ProgressInsight) {
    val colors = MaterialTheme.colorScheme
    val habit = insight.habitId?.let { summary.habit(it) }
    val (icon: ImageVector, color: Color) = when {
        habit != null -> com.example.util.IconHelper.getIconByName(habit.iconName) to habitAccent(habit)
        insight.kind == InsightKind.BEST_WEEKDAY || insight.kind == InsightKind.EVEN_WEEK -> Icons.Default.EventAvailable to HabitDoneAccent
        insight.kind == InsightKind.BEST_MONTH -> Icons.AutoMirrored.Filled.TrendingUp to colors.primary
        else -> Icons.Default.Info to colors.onSurfaceVariant
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(
            text = insight.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.model.StreakMilestones
import com.example.ui.theme.Motion
import com.example.util.AchievementsSummary
import com.example.util.BadgeProgress
import com.example.util.HabitMedals
import com.example.util.IconHelper
import com.example.util.NearMilestone
import com.example.util.ProgressCalculator
import com.example.util.ProgressSummary
import java.time.LocalDate
import kotlin.math.roundToInt

/** Hitos de racha en orden: 3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365. */
private val MedalDays: List<Int> = StreakMilestones.MILESTONE_DAYS.sorted()

/** Medallas visibles por fila. */
private const val MedalWindow = 6

/** Una regla de "Cómo ganar XP". */
private class XpRule(val icon: ImageVector, val title: String, val detail: String, val xp: String)

/**
 * Seccion Logros: ¿qué sigue?
 * Nivel y cuanto falta en habitos, rachas a punto de dar XP, medallas por habito, insignias con su
 * avance, tus numeros y como se gana XP. No calcula nada.
 */
@Composable
fun LogrosSection(
    summary: ProgressSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val a = summary.achievements
    var showRoadmap by remember { mutableStateOf(false) }
    // Por defecto se ve la insignia pendiente mas avanzada (la primera de la lista)
    var selectedBadgeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedBadge = a.badges.firstOrNull { it.badge.id == selectedBadgeId } ?: a.badges.firstOrNull()
    var xpOpen by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("progress_logros"),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "level") {
            LevelCard(a = a, onOpenRoadmap = { showRoadmap = true })
        }
        if (summary.nearMilestones.isNotEmpty()) {
            item(key = "near") {
                NearCard(summary = summary)
            }
        }
        if (a.medals.isNotEmpty()) {
            item(key = "medals") {
                MedalsCard(summary = summary)
            }
        }
        if (selectedBadge != null) {
            item(key = "badges") {
                BadgesCard(
                    a = a,
                    selected = selectedBadge,
                    onSelect = { selectedBadgeId = it }
                )
            }
        }
        item(key = "numbers") {
            NumbersGrid(a = a, today = summary.today)
        }
        item(key = "xp") {
            XpRulesCard(isHardcore = a.isHardcore, expanded = xpOpen, onToggle = { xpOpen = !xpOpen })
        }
    }

    if (showRoadmap) {
        LevelRoadmapDialog(
            currentLevel = a.level.currentLevel,
            currentXp = a.level.currentXp,
            onDismiss = { showRoadmap = false }
        )
    }
}

@Composable
private fun LevelCard(a: AchievementsSummary, onOpenRoadmap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val level = a.level
    val track = colors.onSurfaceVariant.copy(alpha = 0.18f)
    val ring by animateFloatAsState(
        targetValue = level.progressFraction,
        animationSpec = Motion.springValues(),
        label = "logros_level_ring"
    )
    val percent = (level.progressFraction * 100).roundToInt()
    val span = level.maxXpForLevel - level.minXpForLevel

    ProgressCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .drawBehind { drawProgressRing(ring, colors.primary, track, 11.dp) }
                    .clearAndSetSemantics {
                        contentDescription = "Nivel ${level.currentLevel}, $percent% hacia el nivel ${level.currentLevel + 1}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NIVEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = colors.primary
                    )
                    Text(
                        text = level.currentLevel.toString(),
                        fontSize = 44.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressChip(level.rankName, progressAmberText(), Icons.Default.Stars)
                Text(text = level.levelTitle, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "${formatThousands(level.currentXp)} XP en total",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(progressInsetColor())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Nivel ${level.currentLevel + 1} en ${formatThousands(level.xpNeededForNextLevel)} XP",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = (if (a.habitsToNextLevel == 1) "Es 1 hábito cumplido." else "Son unos ${a.habitsToNextLevel} hábitos cumplidos.") +
                        " Vas en ${formatThousands(level.xpInCurrentLevel)} de ${formatThousands(span)} XP de este nivel.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
        TextButton(
            onClick = onOpenRoadmap,
            modifier = Modifier
                .padding(top = 6.dp)
                .heightIn(min = 44.dp)
        ) {
            Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Ver todos los niveles", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NearCard(summary: ProgressSummary) {
    ProgressCard {
        ProgressCardTitle(title = "Muy cerca", subtitle = "Rachas a punto de darte XP extra")
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.nearMilestones.take(3).forEach { near ->
                val habit = summary.habit(near.habitId) ?: return@forEach
                NearRow(habit = habit, near = near)
            }
        }
    }
}

@Composable
private fun NearRow(habit: Habit, near: NearMilestone) {
    val colors = MaterialTheme.colorScheme
    val color = habitAccent(habit)
    val dim = colors.onSurfaceVariant.copy(alpha = 0.2f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(progressInsetColor())
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HabitIconTile(habit = habit)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = habit.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (near.xpBonus > 0) {
                    ProgressChip("+${formatThousands(near.xpBonus)} XP", colors.primary)
                }
            }
            // Un segmento por dia hasta 21; con metas mas largas, una barra
            if (near.target <= 21) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(near.target) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (index < near.currentStreak) color else dim)
                        )
                    }
                }
            } else {
                ProgressBar(fraction = near.currentStreak / near.target.toFloat(), color = color)
            }
            Row {
                Text(
                    text = near.hint,
                    fontSize = 12.sp,
                    color = if (near.remaining == 1) progressGreenText() else colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${near.currentStreak} de ${near.target} días",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun MedalsCard(summary: ProgressSummary) {
    val colors = MaterialTheme.colorScheme
    val first = MedalDays.take(MedalWindow).joinToString(", ")
    ProgressCard {
        ProgressCardTitle(
            title = "Medallas de racha",
            subtitle = "Una por hábito cada vez que llegas a $first… días seguidos. Se quedan aunque la racha se rompa."
        )
        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            summary.achievements.medals.forEach { medals ->
                val habit = summary.habit(medals.habitId) ?: return@forEach
                MedalRow(habit = habit, medals = medals)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
            color = colors.onSurfaceVariant.copy(alpha = 0.12f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgressLegendItem("Ganada") {
                ProgressSwatch(colors.onSurfaceVariant, shape = CircleShape, size = 14.dp)
            }
            ProgressLegendItem("La siguiente") {
                DashedCircle(color = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            ProgressLegendItem("Por ganar") {
                ProgressSwatch(colors.onSurfaceVariant.copy(alpha = 0.14f), shape = CircleShape, size = 14.dp)
            }
        }
    }
}

/** Seis hitos por fila: los primeros; si ya pasaste de 50, los que terminan en el siguiente. */
private fun medalWindow(next: Int?): List<Int> {
    if (MedalDays.size <= MedalWindow) return MedalDays
    val nextIndex = next?.let { MedalDays.indexOf(it) }?.takeIf { it >= 0 } ?: MedalDays.lastIndex
    val start = (nextIndex - (MedalWindow - 1)).coerceIn(0, MedalDays.size - MedalWindow)
    return MedalDays.subList(start, start + MedalWindow)
}

@Composable
private fun MedalRow(habit: Habit, medals: HabitMedals) {
    val colors = MaterialTheme.colorScheme
    val color = habitAccent(habit)
    val count = medals.earned.size
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitIconTile(habit = habit)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = habit.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    text = when (count) {
                        0 -> "Aún sin medallas"
                        1 -> "1 medalla"
                        else -> "$count medallas"
                    },
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                medalWindow(medals.next).forEach { days ->
                    MedalCircle(
                        days = days,
                        habit = habit,
                        color = color,
                        earned = days <= medals.bestStreak,
                        isNext = days == medals.next,
                        current = medals.currentStreak,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MedalCircle(
    days: Int,
    habit: Habit,
    color: Color,
    earned: Boolean,
    isNext: Boolean,
    current: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val description = when {
        earned -> "${habit.title}: medalla de $days días ganada"
        isNext -> "${habit.title}: siguiente medalla, $days días. Vas en $current"
        else -> "${habit.title}: medalla de $days días por ganar"
    }
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val radius = size.minDimension / 2f
                    when {
                        earned -> drawCircle(color = color)
                        isNext -> {
                            drawCircle(
                                color = color.copy(alpha = 0.55f),
                                radius = radius - stroke / 2f,
                                style = Stroke(
                                    width = stroke,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                                )
                            )
                            val sweep = 360f * (current / days.toFloat()).coerceIn(0f, 1f)
                            if (sweep > 0f) {
                                val ring = 3.dp.toPx()
                                drawArc(
                                    color = color,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(ring / 2f, ring / 2f),
                                    size = Size(size.width - ring, size.height - ring),
                                    style = Stroke(width = ring)
                                )
                            }
                        }
                        else -> drawCircle(color = colors.onSurfaceVariant.copy(alpha = 0.10f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = days.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    earned -> contentOn(color)
                    isNext -> colors.onSurface
                    else -> colors.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }
        // Solo la siguiente medalla lleva etiqueta; puede salirse de su columna sin chocar con otra
        Text(
            text = if (isNext) "$current/$days" else "",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = colors.onSurface,
            modifier = Modifier.wrapContentWidth(unbounded = true)
        )
    }
}

@Composable
private fun DashedCircle(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = color,
                radius = size.minDimension / 2f - stroke / 2f,
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
            )
        }
    )
}

@Composable
private fun BadgesCard(a: AchievementsSummary, selected: BadgeProgress, onSelect: (String) -> Unit) {
    val total = a.badges.size
    ProgressCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Insignias", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                text = "${a.unlockedBadges} de $total",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = progressAmberText()
            )
        }
        ProgressBar(
            fraction = if (total == 0) 0f else a.unlockedBadges / total.toFloat(),
            color = progressBadgeAmber(),
            modifier = Modifier.padding(top = 10.dp)
        )
        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.badges.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { badge ->
                        BadgeTile(
                            badge = badge,
                            isSelected = badge.badge.id == selected.badge.id,
                            onClick = { onSelect(badge.badge.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        BadgeDetail(badge = selected, a = a)
    }
}

@Composable
private fun BadgeTile(badge: BadgeProgress, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val amber = progressBadgeAmber()
    val shape = RoundedCornerShape(18.dp)
    val percent = (badge.fraction * 100).roundToInt()
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) amber.copy(alpha = 0.08f) else progressInsetColor())
            .border(1.5.dp, if (isSelected) amber.copy(alpha = 0.6f) else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = badge.badge.title + if (badge.unlocked) ": ganada" else ": $percent% completada"
                selected = isSelected
            }
            .padding(start = 4.dp, top = 12.dp, end = 4.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BadgeIcon(badge = badge, size = 52)
        Text(
            text = badge.badge.title,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            color = if (badge.unlocked) colors.onSurface else colors.onSurfaceVariant
        )
    }
}

/** Circulo de la insignia: ambar si esta ganada; gris con el avance en ambar si no. */
@Composable
private fun BadgeIcon(badge: BadgeProgress, size: Int) {
    val colors = MaterialTheme.colorScheme
    val amber = progressBadgeAmber()
    val track = Color.Transparent
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (badge.unlocked) amber.copy(alpha = 0.16f) else colors.onSurfaceVariant.copy(alpha = 0.10f)
            )
            .drawBehind {
                if (!badge.unlocked) drawProgressRing(badge.fraction, amber, track, 3.dp)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = IconHelper.getIconByName(badge.badge.icon),
            contentDescription = null,
            tint = if (badge.unlocked) amber else colors.onSurfaceVariant,
            modifier = Modifier.size((size / 2).dp)
        )
    }
}

@Composable
private fun BadgeDetail(badge: BadgeProgress, a: AchievementsSummary) {
    val colors = MaterialTheme.colorScheme
    val percent = (badge.fraction * 100).roundToInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(progressInsetColor())
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BadgeIcon(badge = badge, size = 44)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = badge.badge.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badge.unlocked) {
                    ProgressChip("Ganada", progressGreenText())
                } else {
                    ProgressChip("$percent%", progressAmberText())
                }
            }
            Text(text = badgeRequirement(badge), fontSize = 13.sp, color = colors.onSurface)
            if (!badge.unlocked) {
                ProgressBar(fraction = badge.fraction, color = progressBadgeAmber(), modifier = Modifier.padding(top = 2.dp))
            }
            val have = badgeHave(badge, a)
            if (have != null) {
                Text(text = have, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

private fun badgeRequirement(progress: BadgeProgress): String {
    val b = progress.badge
    return when {
        b.requiredCompletions == 1 -> "Cumple tu primer hábito."
        b.requiredCompletions > 1 -> "Cumple ${formatThousands(b.requiredCompletions)} hábitos en total."
        b.requiredStreak > 0 -> "Racha de ${b.requiredStreak} días en un hábito."
        b.requiredFocusMinutes > 0 -> "Suma ${formatThousands(b.requiredFocusMinutes)} minutos de enfoque."
        b.requiredXp > 0 -> "Llega a ${formatThousands(b.requiredXp)} XP."
        else -> b.description
    }
}

/** Cuanto llevas. Null si la insignia se gano antes y el conteo actual ya no la alcanza. */
private fun badgeHave(progress: BadgeProgress, a: AchievementsSummary): String? {
    if (progress.unlocked && progress.current < progress.required) return null
    val b = progress.badge
    return when {
        b.requiredCompletions > 0 -> "Llevas ${formatThousands(progress.current)} hábitos cumplidos."
        b.requiredStreak > 0 -> "Tu mejor racha: ${daysText(progress.current)}" +
            (a.bestStreakHabitTitle?.let { " ($it)." } ?: ".")
        b.requiredFocusMinutes > 0 -> "Llevas ${formatThousands(progress.current)} min de enfoque."
        b.requiredXp > 0 -> "Tienes ${formatThousands(progress.current)} XP."
        else -> null
    }
}

@Composable
private fun NumbersGrid(a: AchievementsSummary, today: LocalDate) {
    val since = a.firstDay?.let { first ->
        val month = ProgressCalculator.monthFull(first.monthValue)
        if (first.year == today.year) "desde $month" else "desde $month de ${first.year}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressStatTile(
                icon = Icons.Default.CheckCircle,
                iconTint = HabitDoneAccent,
                value = formatThousands(a.totalCompleted),
                label = if (since != null) "hábitos cumplidos $since" else "hábitos cumplidos",
                modifier = Modifier.weight(1f)
            )
            ProgressStatTile(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = HabitStreakColor,
                value = daysText(a.bestStreak),
                label = a.bestStreakHabitTitle?.let { "mejor racha · $it" } ?: "mejor racha",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (focusValue, focusUnit) = focusParts(a.focusMinutes)
            ProgressStatTile(
                icon = Icons.Default.Timer,
                iconTint = if (isProgressDark()) Color(0xFF22D3EE) else Color(0xFF0891B2),
                value = focusValue,
                unit = focusUnit,
                label = "de enfoque en total",
                modifier = Modifier.weight(1f)
            )
            ProgressStatTile(
                icon = Icons.Default.Verified,
                iconTint = MaterialTheme.colorScheme.primary,
                value = formatThousands(a.perfectDaysTotal),
                label = "días perfectos",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 1240 -> ("20 h", "40 min"); 45 -> ("45", "min"). Los minutos van chicos para que quepa con letra grande. */
private fun focusParts(minutes: Int): Pair<String, String?> {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m" to "min"
        m == 0 -> "$h h" to null
        else -> "$h h" to "$m min"
    }
}

@Composable
private fun XpRulesCard(isHardcore: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val maxMedal = MedalDays.last()
    val rules = listOf(
        XpRule(Icons.Default.CheckCircle, "Cumplir un hábito", "Llegar a la meta del día.", "+25 XP"),
        XpRule(Icons.AutoMirrored.Filled.TrendingUp, "Pasarte de la meta", "Si registras más de lo que pedía.", "+15 XP"),
        XpRule(Icons.Default.Checklist, "Cada sub-rutina", "Pasos dentro de un hábito.", "+5 XP"),
        XpRule(Icons.Default.Timer, "Cada minuto de enfoque", "Sesiones desde Enfoque.", "+2 XP"),
        XpRule(
            Icons.Default.MilitaryTech,
            "Llegar a una marca de racha",
            "Cada vez que tu racha llega a 3, 7, 14… días. De 50 XP a los 3 días hasta " +
                "${formatThousands(3650)} XP a los $maxMedal.",
            "+50 a +${formatThousands(3650)}"
        ),
        XpRule(
            Icons.Default.Bolt,
            "Modo difícil",
            if (isHardcore) "Activo: multiplica todo lo anterior. Se cambia en Ajustes > Motivación." else "Multiplica todo lo anterior. Se activa en Ajustes > Motivación.",
            "×1.25"
        )
    )
    ProgressCard(contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Abierto" else "Cerrado" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Text(text = "Cómo ganar XP", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rules.forEach { rule ->
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = rule.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = rule.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = rule.detail, fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
                        }
                        ProgressChip(rule.xp, colors.primary)
                    }
                }
            }
        }
    }
}

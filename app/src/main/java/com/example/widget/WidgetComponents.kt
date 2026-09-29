package com.example.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.R
import com.example.model.HabitWithStats
import java.time.LocalDate
import java.util.Locale

object WidgetDimens {
    val ContainerRadius = 24.dp
    val ContainerPadding = 14.dp
    val CardRadius = 16.dp
    val AvatarSize = 28.dp
}

object WidgetDates {
    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val DAYS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

    /** "mar 29 sep" */
    fun shortDate(date: LocalDate = LocalDate.now()): String =
        "${DAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

    /** Etiquetas de mes para el mapa: en la columna cuyo lunes cambia de mes, separadas al menos 3 columnas. */
    fun monthPositions(dateMatrix: List<List<String>>): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var lastMonth = -1
        var lastCol = -10
        dateMatrix.forEachIndexed { col, week ->
            val monday = week.firstOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@forEachIndexed
            if (monday.monthValue != lastMonth) {
                val partialFirstColumn = col == 0 && monday.dayOfMonth > 22
                if (!partialFirstColumn && col - lastCol >= 3) {
                    result += MONTHS[monday.monthValue - 1] to col
                    lastCol = col
                }
                lastMonth = monday.monthValue
            }
        }
        return result
    }
}

fun parseHabitColorInt(hex: String): Int =
    try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { 0xFF6366F1.toInt() }

fun isLightColor(colorInt: Int): Boolean = ColorUtils.calculateLuminance(colorInt) > 0.4

private fun formatAmount(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

fun HabitWithStats.isLockedForWidget(): Boolean = !isDependencyMet && !isCompletedToday

/** Segunda línea de la fila: requisito, cantidad o pasos. Null si no hay dato útil. */
fun HabitWithStats.widgetSubtitle(): String? = when {
    isLockedForWidget() -> blockingHabitTitle?.takeIf { it.isNotBlank() }?.let { "Primero: $it" }
    habit.unit.isNotBlank() -> "${formatAmount(todayLog?.value ?: 0f)} / ${formatAmount(habit.targetValue)} ${habit.unit}"
    subTasks.isNotEmpty() -> "${subTasks.count { it.isCompleted }} / ${subTasks.size} pasos"
    else -> null
}

/** Avance parcial de hoy (0 si no hay avance o si ya está completo). */
fun HabitWithStats.partialFraction(): Float {
    if (isCompletedToday) return 0f
    val f = when {
        habit.unit.isNotBlank() && habit.targetValue > 0f -> (todayLog?.value ?: 0f) / habit.targetValue
        subTasks.isNotEmpty() -> subTasks.count { it.isCompleted }.toFloat() / subTasks.size
        else -> 0f
    }
    return if (f > 0f && f < 1f) f else 0f
}

@Composable
fun HabitAvatar(item: HabitWithStats, size: Dp = WidgetDimens.AvatarSize) {
    val colorInt = parseHabitColorInt(item.habit.colorHex)
    val habitColor = Color(colorInt)
    val isDone = item.isCompletedToday
    val isLocked = item.isLockedForWidget()
    val fraction = item.partialFraction()
    val density = LocalContext.current.resources.displayMetrics.density
    val ringBitmap = remember(fraction, colorInt, size) {
        if (fraction == 0f) null
        else WidgetBitmapUtils.createAvatarRingBitmap(
            fraction = fraction,
            sizePx = (size.value * density).toInt(),
            strokeWidthPx = 2.5f * density,
            progressColorInt = colorInt
        )
    }

    when {
        isDone -> {
            val checkTint = if (isLightColor(colorInt)) WidgetColors.CheckOnLight else Color.White
            Box(
                modifier = GlanceModifier
                    .size(size)
                    .cornerRadius(size / 2)
                    .background(ColorProvider(habitColor)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = "Hecho",
                    colorFilter = ColorFilter.tint(ColorProvider(checkTint)),
                    modifier = GlanceModifier.size(16.dp)
                )
            }
        }
        isLocked -> {
            Box(
                modifier = GlanceModifier
                    .size(size)
                    .cornerRadius(size / 2)
                    .background(ColorProvider(WidgetColors.Card)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_lock),
                    contentDescription = "Bloqueado",
                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.TextSecondary)),
                    modifier = GlanceModifier.size(16.dp)
                )
            }
        }
        ringBitmap != null -> {
            Box(
                modifier = GlanceModifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(ringBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(size)
                )
                HabitGlyph(item = item, habitColor = habitColor)
            }
        }
        else -> {
            Box(
                modifier = GlanceModifier
                    .size(size)
                    .cornerRadius(size / 2)
                    .background(ColorProvider(habitColor.copy(alpha = 0.18f))),
                contentAlignment = Alignment.Center
            ) {
                HabitGlyph(item = item, habitColor = habitColor)
            }
        }
    }
}

@Composable
private fun HabitGlyph(item: HabitWithStats, habitColor: Color) {
    val iconRes = WidgetIconHelper.getWidgetIconRes(item.habit.iconName)
    if (iconRes != null) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(habitColor)),
            modifier = GlanceModifier.size(16.dp)
        )
    } else {
        Text(
            text = item.habit.title.take(1).uppercase(),
            style = TextStyle(
                color = ColorProvider(habitColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun StreakBadge(streak: Int) {
    if (streak <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_flame),
            contentDescription = "Racha",
            modifier = GlanceModifier.size(14.dp)
        )
        Text(
            text = "$streak",
            modifier = GlanceModifier.padding(start = 3.dp),
            style = TextStyle(
                color = ColorProvider(WidgetColors.Amber),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun HabitWidgetRow(
    item: HabitWithStats,
    widgetType: String,
    openAppIntent: Intent,
    rowHeight: Dp
) {
    val isDone = item.isCompletedToday
    val isLocked = item.isLockedForWidget()
    val rowAction = if (isLocked) {
        actionStartActivity(openAppIntent)
    } else {
        actionRunCallback<ToggleHabitAction>(
            actionParametersOf(
                ToggleHabitAction.habitIdKey to item.habit.id,
                ToggleHabitAction.widgetTypeKey to widgetType
            )
        )
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(rowAction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HabitAvatar(item)
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 10.dp)
        ) {
            Text(
                text = item.habit.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(
                        if (isDone || isLocked) WidgetColors.TextSecondary else WidgetColors.TextPrimary
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
            )
            item.widgetSubtitle()?.let { subtitle ->
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.TextSecondary),
                        fontSize = 12.sp
                    )
                )
            }
        }
        if (item.currentStreak > 0) {
            Box(modifier = GlanceModifier.padding(start = 8.dp)) {
                StreakBadge(item.currentStreak)
            }
        }
    }
}

@Composable
fun SegmentedProgressBar(
    habits: List<HabitWithStats>,
    modifier: GlanceModifier = GlanceModifier
) {
    if (habits.isEmpty()) return
    val completedCount = habits.count { it.isCompletedToday }
    val allDone = completedCount == habits.size
    if (habits.size > 10) {
        LinearProgressIndicator(
            progress = completedCount.toFloat() / habits.size,
            modifier = modifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(WidgetColors.Emerald),
            backgroundColor = ColorProvider(WidgetColors.Track)
        )
    } else {
        Row(modifier = modifier.fillMaxWidth()) {
            habits.forEachIndexed { index, item ->
                val isLast = index == habits.lastIndex
                val segmentColor = when {
                    allDone -> WidgetColors.Emerald
                    item.isCompletedToday -> Color(parseHabitColorInt(item.habit.colorHex))
                    else -> WidgetColors.Track
                }
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(end = if (isLast) 0.dp else 3.dp)
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .cornerRadius(3.dp)
                            .background(ColorProvider(segmentColor))
                    ) {}
                }
            }
        }
    }
}

@Composable
fun DayCompletePill() {
    Box(
        modifier = GlanceModifier
            .cornerRadius(12.dp)
            .background(ColorProvider(WidgetColors.EmeraldSoft))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.EmeraldText)),
                modifier = GlanceModifier.size(13.dp)
            )
            Text(
                text = "Día completo",
                modifier = GlanceModifier.padding(start = 4.dp),
                style = TextStyle(
                    color = ColorProvider(WidgetColors.EmeraldText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun NoHabitsTodayState(allHabits: List<HabitWithStats>) {
    val title = if (allHabits.isEmpty()) "Aún no tienes hábitos" else "No tienes hábitos para hoy"
    val subtitle = if (allHabits.isEmpty()) {
        "Crea el primero en la app"
    } else {
        val tomorrow = LocalDate.now().plusDays(1).dayOfWeek.value
        val manana = allHabits
            .filter { it.habit.frequencyDays.isEmpty() || tomorrow in it.habit.frequencyDays }
            .map { it.habit.title }
        when (manana.size) {
            0 -> "Mañana tampoco hay hábitos programados"
            1 -> "Mañana: ${manana[0]}"
            2 -> "Mañana: ${manana[0]} y ${manana[1]}"
            else -> "Mañana: ${manana[0]}, ${manana[1]} y ${manana.size - 2} más"
        }
    }

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .cornerRadius(24.dp)
                .background(ColorProvider(WidgetColors.Card)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_calendar_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.TextSecondary)),
                modifier = GlanceModifier.size(24.dp)
            )
        }
        Text(
            text = title,
            modifier = GlanceModifier.padding(top = 10.dp),
            style = TextStyle(
                color = ColorProvider(WidgetColors.TextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = subtitle,
            maxLines = 2,
            modifier = GlanceModifier.padding(top = 4.dp),
            style = TextStyle(
                color = ColorProvider(WidgetColors.TextSecondary),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

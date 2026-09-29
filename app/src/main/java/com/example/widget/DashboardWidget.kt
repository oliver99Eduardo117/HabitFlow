package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

class DashboardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()

        val initialHabits = try {
            WidgetRepositoryProvider.habitsWithStatsFlow(context, today).first()
        } catch (_: Exception) {
            emptyList()
        }

        val initialLogs = try {
            repository.allLogs.first()
        } catch (_: Exception) {
            emptyList()
        }

        val initialActiveHabits = try {
            repository.activeHabits.first()
        } catch (_: Exception) {
            emptyList()
        }

        val todayTabIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "TODAY")
        }

        val analyticsTabIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "ANALYTICS")
        }

        provideContent {
            val habitsWithStats by WidgetRepositoryProvider.habitsWithStatsFlow(context, today)
                .collectAsState(initial = initialHabits)
            val allLogs by repository.allLogs.collectAsState(initial = initialLogs)
            val activeHabits by repository.activeHabits.collectAsState(initial = initialActiveHabits)

            val size = LocalSize.current
            val density = context.resources.displayMetrics.density

            val todayHabits = habitsWithStats.forToday()
            val totalHabits = todayHabits.size
            val completedHabits = todayHabits.count { it.isCompletedToday }
            val allDone = totalHabits > 0 && completedHabits == totalHabits
            val progressRatio = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else 0f

            val topStreakHabit = habitsWithStats.maxByOrNull { it.currentStreak }

            val cardInnerDp = size.width.value - 28f - 20f
            val weeks = ((cardInnerDp + 2f) / 13f).toInt().coerceIn(8, 26)
            val dateMatrix = DateUtils.getHeatmapDateMatrix(weeks = weeks)
            val monthPositions = WidgetDates.monthPositions(dateMatrix)

            val zoneId = ZoneId.systemDefault()
            val createdDateByHabitId = activeHabits.associate { habit ->
                val createdDate = Instant.ofEpochMilli(habit.createdAt)
                    .atZone(zoneId)
                    .toLocalDate()
                    .toString()
                habit.id to createdDate
            }
            val allLogsByDate = allLogs.groupBy { it.date }

            val ratioMatrix: List<List<Float>> = dateMatrix.map { week ->
                week.map { dateStr ->
                    when {
                        dateStr == today -> progressRatio
                        dateStr > today -> 0f
                        else -> {
                            val dayOfWeek = DateUtils.getDayOfWeek(dateStr)
                            val dayLogsByHabit = (allLogsByDate[dateStr] ?: emptyList()).associateBy { it.habitId }
                            val eligible = activeHabits.filter { habit ->
                                dayLogsByHabit.containsKey(habit.id) ||
                                    ((createdDateByHabitId[habit.id] ?: "") <= dateStr &&
                                        (habit.frequencyDays.isEmpty() || dayOfWeek in habit.frequencyDays))
                            }
                            if (eligible.isEmpty()) {
                                0f
                            } else {
                                val done = eligible.count { habit ->
                                    (dayLogsByHabit[habit.id]?.value ?: 0f) >= habit.targetValue
                                }
                                done.toFloat() / eligible.size
                            }
                        }
                    }
                }
            }

            val todayCell: Pair<Int, Int>? = run {
                var found: Pair<Int, Int>? = null
                dateMatrix.forEachIndexed { col, week ->
                    val row = week.indexOf(today)
                    if (row >= 0) found = col to row
                }
                found
            }

            val heatmapBitmap: Bitmap = remember(ratioMatrix, weeks, density) {
                WidgetBitmapUtils.createHeatmapGridBitmap(
                    columns = weeks,
                    monthPositions = monthPositions,
                    cellSizePx = 11f * density,
                    gapPx = 2f * density,
                    monthLabelTextPx = 10f * density,
                    highlightCell = todayCell
                ) { col, row ->
                    val date = dateMatrix.getOrNull(col)?.getOrNull(row) ?: return@createHeatmapGridBitmap 0
                    if (date > today) 0 else WidgetColors.getHeatmapColorInt(ratioMatrix[col][row])
                }
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .cornerRadius(WidgetDimens.ContainerRadius)
                    .background(ColorProvider(WidgetColors.Container))
                    .padding(WidgetDimens.ContainerPadding)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 1. Encabezado
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(todayTabIntent)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hoy",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = WidgetDates.shortDate(),
                            modifier = GlanceModifier.padding(start = 8.dp),
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        if (totalHabits > 0 && allDone) {
                            Text(
                                text = "$completedHabits de $totalHabits",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.EmeraldText),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else if (totalHabits > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$completedHabits",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = " de $totalHabits",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    // 2. Barra de progreso por segmentos
                    if (totalHabits > 0) {
                        SegmentedProgressBar(
                            habits = todayHabits,
                            modifier = GlanceModifier.padding(top = 10.dp, bottom = 8.dp)
                        )
                    }

                    // 3. Zona central
                    when {
                        totalHabits == 0 -> {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            ) {
                                NoHabitsTodayState(habitsWithStats)
                            }
                        }
                        allDone -> {
                            Column(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(40.dp)
                                        .cornerRadius(20.dp)
                                        .background(ColorProvider(WidgetColors.EmeraldSoft)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_check),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.EmeraldText)),
                                        modifier = GlanceModifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = "Todo listo por hoy",
                                    modifier = GlanceModifier.padding(top = 8.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (topStreakHabit != null && topStreakHabit.currentStreak > 0) {
                                    Text(
                                        text = "Mejor racha: ${topStreakHabit.habit.title}, ${topStreakHabit.currentStreak} días",
                                        maxLines = 1,
                                        modifier = GlanceModifier.padding(top = 4.dp),
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                        else -> {
                            val pending = todayHabits.filter { !it.isCompletedToday }
                            val heatmapCardDp = 145f
                            val availableDp = size.height.value - 28f - 20f - 24f - 12f - heatmapCardDp
                            val maxRows = (availableDp / 40f).toInt().coerceIn(0, 4)
                            val visible = if (pending.size > maxRows) (maxRows - 1).coerceAtLeast(0) else pending.size

                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                pending.take(visible).forEach { item ->
                                    HabitWidgetRow(
                                        item = item,
                                        widgetType = "dashboard",
                                        openAppIntent = todayTabIntent,
                                        rowHeight = 40.dp
                                    )
                                }
                                if (pending.size > visible) {
                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                            .clickable(actionStartActivity(todayTabIntent)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (maxRows == 0) {
                                                "${pending.size} pendientes"
                                            } else {
                                                "+${pending.size - visible} más en la app"
                                            },
                                            style = TextStyle(
                                                color = ColorProvider(WidgetColors.TextSecondary),
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Espaciador
                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // 5. Tarjeta del mapa
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .cornerRadius(WidgetDimens.CardRadius)
                            .background(ColorProvider(WidgetColors.Card))
                            .padding(10.dp)
                            .clickable(actionStartActivity(analyticsTabIntent))
                    ) {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$weeks semanas",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = "Menos",
                                    modifier = GlanceModifier.padding(end = 4.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp
                                    )
                                )
                                listOf(0f, 0.3f, 0.5f, 0.8f, 1f).forEach { level ->
                                    Box(modifier = GlanceModifier.padding(end = 2.dp)) {
                                        Box(
                                            modifier = GlanceModifier
                                                .size(8.dp)
                                                .cornerRadius(2.dp)
                                                .background(
                                                    ColorProvider(
                                                        Color(WidgetColors.getHeatmapColorInt(level))
                                                    )
                                                )
                                        ) {}
                                    }
                                }
                                Text(
                                    text = "Más",
                                    modifier = GlanceModifier.padding(start = 2.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Image(
                                provider = ImageProvider(heatmapBitmap),
                                contentDescription = "Constancia de las últimas $weeks semanas",
                                contentScale = ContentScale.Fit,
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight()
                                    .padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

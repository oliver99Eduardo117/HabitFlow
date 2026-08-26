package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.model.HabitWithStats
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()

        val habitsWithStats = WidgetRepositoryProvider.getHabitsWithStatsCached(context, today)

        val allLogs = try {
            repository.allLogs.first()
        } catch (_: Exception) {
            emptyList()
        }

        val activeHabits = try {
            repository.activeHabits.first()
        } catch (_: Exception) {
            emptyList()
        }

        val todayDateHeader = formatHeaderDate()

        val todayTabIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "TODAY")
        }

        val analyticsTabIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "ANALYTICS")
        }

        provideContent {
            val size = LocalSize.current
            val density = context.resources.displayMetrics.density

            // 1. Single unified source of truth for daily progress & counts from Room
            val totalHabits = habitsWithStats.size
            val completedHabits = habitsWithStats.count { it.isCompletedToday }
            val progressRatio = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else 0f
            val percentage = (progressRatio * 100).toInt()

            // 2. Checklist items (take 3 for Dashboard)
            val displayHabits = habitsWithStats.take(3)

            // Top streak
            val topStreakHabit = habitsWithStats.maxByOrNull { it.currentStreak }

            // 3. Progress Ring Bitmap (derived strictly from percentage)
            val progressRingBitmap: Bitmap = WidgetBitmapUtils.createProgressRingBitmap(
                percentage = percentage,
                sizePx = 120,
                strokeWidthPx = 12f,
                trackColorInt = 0xFF334155.toInt(),
                progressColorInt = 0xFF6366F1.toInt(),
                completedColorInt = 0xFF10B981.toInt()
            )

            // 5. Adaptive heatmap generation based on available width & height
            val weeks = if (size.width < 220.dp) 10 else 14
            val dateMatrix = DateUtils.getHeatmapDateMatrix(weeks = weeks)
            val monthPositions = DateUtils.calculateMonthPositionsForHabit(dateMatrix)

            val totalActive = maxOf(1, activeHabits.size)
            val activeHabitsById = activeHabits.associateBy { it.id }
            val allLogsByDate = allLogs.groupBy { it.date }

            val ratioMatrix: List<List<Float>> = dateMatrix.map { week ->
                week.map { dateStr ->
                    if (dateStr == today) {
                        progressRatio
                    } else {
                        val dayLogs = allLogsByDate[dateStr] ?: emptyList()
                        val doneCount = dayLogs.count { log ->
                            val habit = activeHabitsById[log.habitId]
                            habit != null && log.value >= habit.targetValue
                        }
                        if (activeHabits.isNotEmpty()) {
                            (doneCount.toFloat() / totalActive).coerceIn(0f, 1f)
                        } else if (doneCount > 0) {
                            1f
                        } else {
                            0f
                        }
                    }
                }
            }

            // Estimate target heatmap dimensions (subtracting outer padding, header, cards 1, 2/3, spacers)
            val targetHeatmapWidthPx = ((size.width.value - 40f) * density).toInt().coerceAtLeast(140)
            val targetHeatmapHeightPx = ((size.height.value - 200f) * density).toInt().coerceAtLeast(60)

            val heatmapBitmap: Bitmap = WidgetBitmapUtils.createHeatmapBitmap(
                columns = weeks,
                monthPositions = monthPositions,
                targetWidthPx = targetHeatmapWidthPx,
                targetHeightPx = targetHeatmapHeightPx,
                cellColorProvider = { col, row ->
                    val ratio = ratioMatrix.getOrNull(col)?.getOrNull(row) ?: 0f
                    WidgetColors.getHeatmapColorInt(ratio)
                }
            )

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(20.dp)
                    .background(ColorProvider(WidgetColors.Background))
                    .padding(10.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Outer Header: "Hoy, 20 ago" and "HABITFLOW"
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = todayDateHeader,
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "HABITFLOW",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextMuted),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // CARD 1: Checklist "Hoy"
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(12.dp)
                            .background(ColorProvider(WidgetColors.CardSurface))
                            .padding(8.dp)
                    ) {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(actionStartActivity(todayTabIntent)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hoy",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = "$completedHabits/$totalHabits",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextMuted),
                                        fontSize = 9.sp
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            if (displayHabits.isEmpty()) {
                                Text(
                                    text = "Sin hábitos activos para hoy",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp
                                    ),
                                    modifier = GlanceModifier.padding(vertical = 2.dp)
                                )
                            } else {
                                displayHabits.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = GlanceModifier.height(2.dp))
                                    }
                                    HabitDashboardRow(item = item)
                                }
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // MIDDLE ROW: Card 2 (Streak) & Card 3 (Daily Progress)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Card 2: Top Streak
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(78.dp)
                                .cornerRadius(12.dp)
                                .background(ColorProvider(WidgetColors.CardSurface))
                                .padding(6.dp)
                                .clickable(actionStartActivity(todayTabIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = GlanceModifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = topStreakHabit?.habit?.title ?: "Sin hábitos",
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                )

                                Spacer(modifier = GlanceModifier.height(1.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${topStreakHabit?.currentStreak ?: 0}",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.width(3.dp))
                                    Text(
                                        text = "días",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 10.sp
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.height(1.dp))

                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_flame),
                                    contentDescription = "Racha",
                                    modifier = GlanceModifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Card 3: Daily Progress (Determinate Ring Bitmap)
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(78.dp)
                                .cornerRadius(12.dp)
                                .background(ColorProvider(WidgetColors.CardSurface))
                                .padding(6.dp)
                                .clickable(actionStartActivity(analyticsTabIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = GlanceModifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier.size(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(progressRingBitmap),
                                        contentDescription = "$percentage%",
                                        modifier = GlanceModifier.size(38.dp)
                                    )
                                    Text(
                                        text = "$percentage%",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.height(2.dp))

                                Text(
                                    text = "$completedHabits de $totalHabits",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // CARD 4: Constancia (Heatmap)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .cornerRadius(12.dp)
                            .background(ColorProvider(WidgetColors.CardSurface))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clickable(actionStartActivity(analyticsTabIntent))
                    ) {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Constancia",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = "$weeks semanas",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextMuted),
                                        fontSize = 9.sp
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(2.dp))

                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(heatmapBitmap),
                                    contentDescription = "Mapa de constancia de $weeks semanas",
                                    contentScale = ContentScale.Fit,
                                    modifier = GlanceModifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitDashboardRow(item: HabitWithStats) {
        val isCompleted = item.isCompletedToday

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(
                    actionRunCallback<ToggleHabitAction>(
                        actionParametersOf(
                            ToggleHabitAction.habitIdKey to item.habit.id,
                            ToggleHabitAction.widgetTypeKey to "dashboard"
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val checkBg = if (isCompleted) WidgetColors.Emerald else WidgetColors.SurfaceVariant

            Box(
                modifier = GlanceModifier
                    .size(20.dp)
                    .cornerRadius(10.dp)
                    .background(ColorProvider(checkBg)),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_check),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Habit Icon (~13dp or letter fallback Box)
            val iconRes = WidgetIconHelper.getWidgetIconRes(item.habit.iconName)
            val habitColor = try {
                Color(android.graphics.Color.parseColor(item.habit.colorHex))
            } catch (_: Exception) {
                WidgetColors.Indigo
            }

            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(habitColor)),
                    modifier = GlanceModifier.size(13.dp)
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(13.dp)
                        .cornerRadius(3.dp)
                        .background(ColorProvider(habitColor.copy(alpha = 0.25f))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.habit.title.take(1).uppercase(),
                        style = TextStyle(
                            color = ColorProvider(habitColor),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(5.dp))

            // Habit Title - TextSecondary when completed, TextPrimary when uncompleted
            Text(
                text = item.habit.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(
                        if (isCompleted) {
                            WidgetColors.TextSecondary
                        } else {
                            WidgetColors.TextPrimary
                        }
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // Right side: flame + streak, or "—"
            val effectiveStreak = item.currentStreak

            if (effectiveStreak > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_flame),
                        contentDescription = "Racha",
                        modifier = GlanceModifier.size(12.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "$effectiveStreak",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Amber),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Text(
                    text = "—",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.TextMuted),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    private fun formatHeaderDate(): String {
        return try {
            val sdf = SimpleDateFormat("d MMM", Locale("es", "ES"))
            val formatted = sdf.format(Date()).lowercase(Locale("es", "ES")).replace(".", "")
            "Hoy, $formatted"
        } catch (_: Exception) {
            "Hoy"
        }
    }
}

package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()

        val habitsWithStats = try {
            repository.getHabitsWithStats(today).first()
        } catch (_: Exception) {
            emptyList()
        }

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

        // Heatmap: 64 days (16 columns x 4 rows)
        val past64Days = DateUtils.getPastNDaysDateStrings(64)
        val totalActive = maxOf(1, activeHabits.size)
        val activeHabitsById = activeHabits.associateBy { it.id }
        val logsByDate = allLogs.filter { past64Days.contains(it.date) }.groupBy { it.date }

        val dailyRatiosList = past64Days.map { dateStr ->
            val dayLogs = logsByDate[dateStr] ?: emptyList()
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

        val heatmapBitmap: Bitmap = WidgetBitmapUtils.createHeatmapGridBitmap(
            dailyRatios = dailyRatiosList,
            columns = 16,
            rows = 4,
            widthPx = 540,
            heightPx = 120,
            gapPx = 5f,
            cornerRadiusPx = 4f
        )

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
            val prefs = currentState<Preferences>()

            // Top card items (up to 3 habits: prioritize uncompleted then completed)
            val uncompleted = habitsWithStats.filter {
                val localCompleted = prefs[booleanPreferencesKey("habit_completed_${it.habit.id}")]
                val fb = WidgetFeedbackManager.getFeedbackState(it.habit.id)
                val isDone = localCompleted ?: fb?.isOptimisticallyCompleted ?: it.isCompletedToday
                !isDone
            }
            val completed = habitsWithStats.filter {
                val localCompleted = prefs[booleanPreferencesKey("habit_completed_${it.habit.id}")]
                val fb = WidgetFeedbackManager.getFeedbackState(it.habit.id)
                val isDone = localCompleted ?: fb?.isOptimisticallyCompleted ?: it.isCompletedToday
                isDone
            }
            val displayHabits = (uncompleted + completed).take(3)

            // Top streak
            val topStreakHabit = habitsWithStats.maxByOrNull { it.currentStreak }

            // Daily progress
            val totalHabits = habitsWithStats.size
            val completedHabits = habitsWithStats.count {
                val localCompleted = prefs[booleanPreferencesKey("habit_completed_${it.habit.id}")]
                val fb = WidgetFeedbackManager.getFeedbackState(it.habit.id)
                localCompleted ?: fb?.isOptimisticallyCompleted ?: it.isCompletedToday
            }
            val progressRatio = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else 0f
            val percentage = (progressRatio * 100).toInt()

            val progressRingBitmap: Bitmap = WidgetBitmapUtils.createProgressRingBitmap(
                percentage = percentage,
                sizePx = 140,
                strokeWidthPx = 14f,
                trackColorInt = 0xFF334155.toInt(),
                progressColorInt = 0xFF6366F1.toInt(),
                completedColorInt = 0xFF10B981.toInt()
            )

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(22.dp)
                    .background(ColorProvider(WidgetColors.Background))
                    .padding(12.dp)
                    .clickable(actionStartActivity(todayTabIntent))
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "HABITFLOW",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextMuted),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // CARD 1: Checklist "Hoy"
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(14.dp)
                            .background(ColorProvider(WidgetColors.CardSurface))
                            .padding(10.dp)
                            .clickable(actionStartActivity(todayTabIntent))
                    ) {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hoy",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = "$completedHabits/$totalHabits",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextMuted),
                                        fontSize = 10.sp
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            if (displayHabits.isEmpty()) {
                                Text(
                                    text = "Sin hábitos activos para hoy",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 11.sp
                                    ),
                                    modifier = GlanceModifier.padding(vertical = 4.dp)
                                )
                            } else {
                                displayHabits.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = GlanceModifier.height(6.dp))
                                    }
                                    HabitDashboardRow(item = item, prefs = prefs)
                                }
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // MIDDLE ROW: Card 2 (Streak) & Card 3 (Daily Progress)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Card 2: Top Streak
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(94.dp)
                                .cornerRadius(14.dp)
                                .background(ColorProvider(WidgetColors.CardSurface))
                                .padding(8.dp)
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
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                )

                                Spacer(modifier = GlanceModifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${topStreakHabit?.currentStreak ?: 0}",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.width(4.dp))
                                    Text(
                                        text = "días",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 11.sp
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.height(2.dp))

                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_flame),
                                    contentDescription = "Racha",
                                    modifier = GlanceModifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // Card 3: Daily Progress (Determinate Ring Bitmap)
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(94.dp)
                                .cornerRadius(14.dp)
                                .background(ColorProvider(WidgetColors.CardSurface))
                                .padding(8.dp)
                                .clickable(actionStartActivity(analyticsTabIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = GlanceModifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier.size(46.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(progressRingBitmap),
                                        contentDescription = "$percentage%",
                                        modifier = GlanceModifier.size(46.dp)
                                    )
                                    Text(
                                        text = "$percentage%",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.height(4.dp))

                                Text(
                                    text = "$completedHabits de $totalHabits hábitos",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // CARD 4: Constancia (Heatmap)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(14.dp)
                            .background(ColorProvider(WidgetColors.CardSurface))
                            .padding(10.dp)
                            .clickable(actionStartActivity(analyticsTabIntent))
                    ) {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Constancia",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = "8 semanas",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextMuted),
                                        fontSize = 10.sp
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            // Crisp Heatmap Bitmap that fits the entire width nicely
                            Image(
                                provider = ImageProvider(heatmapBitmap),
                                contentDescription = "Mapa de constancia",
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitDashboardRow(item: HabitWithStats, prefs: Preferences) {
        val flashKey = booleanPreferencesKey("habit_flash_${item.habit.id}")
        val flashTimeKey = longPreferencesKey("habit_flash_time_${item.habit.id}")
        val completedKey = booleanPreferencesKey("habit_completed_${item.habit.id}")

        val flashTime = prefs[flashTimeKey] ?: 0L
        val isRecentFlash = (System.currentTimeMillis() - flashTime) < 1800L
        val isGlanceFlashing = (prefs[flashKey] == true) && isRecentFlash
        val localCompleted = prefs[completedKey]

        val feedback = WidgetFeedbackManager.getFeedbackState(item.habit.id)
        val isCompleted = localCompleted ?: feedback?.isOptimisticallyCompleted ?: item.isCompletedToday
        val isFlashing = isGlanceFlashing || (feedback?.isFlashing == true)

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox circle with visual flash feedback
            val checkBg = when {
                isFlashing && isCompleted -> WidgetColors.EmeraldGlow
                isFlashing && !isCompleted -> WidgetColors.FeedbackUncheckFlash
                isCompleted -> WidgetColors.Emerald
                else -> WidgetColors.SurfaceVariant
            }

            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(ColorProvider(checkBg))
                    .clickable(
                        actionRunCallback<ToggleHabitAction>(
                            actionParametersOf(
                                ToggleHabitAction.habitIdKey to item.habit.id,
                                ToggleHabitAction.currentCompletedKey to isCompleted
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Text(
                        text = "✓",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.TextPrimary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Habit Title - Green when completed/marked, white when uncompleted
            Text(
                text = item.habit.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(
                        if (isCompleted) {
                            WidgetColors.EmeraldGlow
                        } else {
                            WidgetColors.TextPrimary
                        }
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // Right side: flame + streak, or "—"
            val effectiveStreak = if (isCompleted && !item.isCompletedToday) {
                item.currentStreak + 1
            } else if (!isCompleted && item.isCompletedToday) {
                (item.currentStreak - 1).coerceAtLeast(0)
            } else {
                item.currentStreak
            }

            if (effectiveStreak > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_flame),
                        contentDescription = "Racha",
                        modifier = GlanceModifier.size(14.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(3.dp))
                    Text(
                        text = "$effectiveStreak",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Amber),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Text(
                    text = "—",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.TextMuted),
                        fontSize = 13.sp
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

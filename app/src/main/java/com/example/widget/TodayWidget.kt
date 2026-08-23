package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.model.HabitWithStats
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class TodayWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()
        val allHabitsWithStats = try {
            repository.getHabitsWithStats(today).first()
        } catch (_: Exception) {
            emptyList()
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val now = System.currentTimeMillis()

            // 1. Single unified resolution of effective completion state for each habit
            val evaluatedHabits = allHabitsWithStats.map { item ->
                val flashKey = booleanPreferencesKey("habit_flash_${item.habit.id}")
                val flashTimeKey = longPreferencesKey("habit_flash_time_${item.habit.id}")
                val completedKey = booleanPreferencesKey("habit_completed_${item.habit.id}")

                val flashTime = prefs[flashTimeKey] ?: 0L
                val isRecent = (now - flashTime) < 2000L
                val localCompleted = if (isRecent) prefs[completedKey] else null
                val fb = WidgetFeedbackManager.getFeedbackState(item.habit.id)

                val isCompleted = localCompleted ?: fb?.isOptimisticallyCompleted ?: item.isCompletedToday
                val isGlanceFlashing = (prefs[flashKey] == true) && (now - flashTime < 1800L)
                val isFlashing = isGlanceFlashing || (fb?.isFlashing == true)

                Triple(item, isCompleted, isFlashing)
            }

            // 2. Single unified source of truth for daily progress & counts
            val totalHabits = evaluatedHabits.size
            val completedCount = evaluatedHabits.count { it.second }

            // 3. Checklist items (prioritize uncompleted then completed, take 4)
            val uncompleted = evaluatedHabits.filter { !it.second }
            val completed = evaluatedHabits.filter { it.second }
            val displayHabits = (uncompleted + completed).take(4)

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("widget_target_tab", "TODAY")
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(ColorProvider(WidgetColors.Surface))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Header row: "Hoy" (13sp medium DarkOnSurface) ... "X de Y" (11sp mutedText)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hoy",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "$completedCount de $totalHabits",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.MutedText),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    if (displayHabits.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin hábitos para hoy",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.TextSecondary),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    } else {
                        Column(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            displayHabits.forEachIndexed { index, (item, isCompleted, isFlashing) ->
                                if (index > 0) {
                                    Spacer(modifier = GlanceModifier.height(10.dp))
                                }
                                HabitRowItem(
                                    item = item,
                                    isCompleted = isCompleted,
                                    isFlashing = isFlashing
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitRowItem(
        item: HabitWithStats,
        isCompleted: Boolean,
        isFlashing: Boolean
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox circle: 22dp (Emerald solid if completed, SurfaceVariant if not)
            val checkColor = when {
                isFlashing && isCompleted -> WidgetColors.EmeraldGlow
                isFlashing && !isCompleted -> WidgetColors.FeedbackUncheckFlash
                isCompleted -> WidgetColors.Emerald
                else -> WidgetColors.SurfaceVariant
            }

            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(ColorProvider(checkColor))
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
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_check),
                        contentDescription = null,
                        modifier = GlanceModifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            // Habit title: 14sp (DarkOnSurfaceVariant for completed, DarkOnSurface for uncompleted)
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // Streak: flame + number in 13sp Amber if streak > 0, or dash "—" in 13sp mutedText if streak = 0
            val effectiveStreak = if (isCompleted && !item.isCompletedToday) {
                item.currentStreak + 1
            } else if (!isCompleted && item.isCompletedToday) {
                (item.currentStreak - 1).coerceAtLeast(0)
            } else {
                item.currentStreak
            }

            if (effectiveStreak > 0) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_flame),
                        contentDescription = "Racha",
                        modifier = GlanceModifier.size(14.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "$effectiveStreak",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Amber),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "—",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.MutedText),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
    }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[habitIdKey] ?: return
        val currentCompleted = parameters[currentCompletedKey] ?: false
        val newCompleted = !currentCompleted

        // 1. Immediately update the Glance local state variables for instant UI flash & feedback
        try {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[booleanPreferencesKey("habit_flash_$habitId")] = true
                    this[booleanPreferencesKey("habit_completed_$habitId")] = newCompleted
                    this[longPreferencesKey("habit_flash_time_$habitId")] = System.currentTimeMillis()
                }
            }
        } catch (_: Exception) {}

        // Also register in memory feedback manager
        WidgetFeedbackManager.setImmediateToggle(habitId, currentCompleted)

        // 2. Immediate priority update of the triggering widget
        try {
            TodayWidget().update(context, glanceId)
        } catch (_: Exception) {}
        try {
            DashboardWidget().updateAll(context)
        } catch (_: Exception) {}

        // 3. Perform asynchronous background Room DB sync
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()
        repository.toggleHabitCompletion(habitId, today)

        // Clean up the optimistic preference key now that Room is updated
        try {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    remove(booleanPreferencesKey("habit_completed_$habitId"))
                }
            }
        } catch (_: Exception) {}

        // 4. Update all widgets across the launcher
        WidgetUpdater.refreshAll(context)
    }

    companion object {
        val habitIdKey = ActionParameters.Key<Long>("habit_id")
        val currentCompletedKey = ActionParameters.Key<Boolean>("current_completed")
    }
}


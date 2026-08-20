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

            // Prioritize uncompleted habits first, then completed ones up to 5 total
            val uncompleted = allHabitsWithStats.filter { 
                val localCompleted = prefs[booleanPreferencesKey("habit_completed_${it.habit.id}")]
                val fb = WidgetFeedbackManager.getFeedbackState(it.habit.id)
                val isDone = localCompleted ?: fb?.isOptimisticallyCompleted ?: it.isCompletedToday
                !isDone
            }
            val completed = allHabitsWithStats.filter { 
                val localCompleted = prefs[booleanPreferencesKey("habit_completed_${it.habit.id}")]
                val fb = WidgetFeedbackManager.getFeedbackState(it.habit.id)
                val isDone = localCompleted ?: fb?.isOptimisticallyCompleted ?: it.isCompletedToday
                isDone
            }
            val displayHabits = (uncompleted + completed).take(5)

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("widget_target_tab", "TODAY")
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(ColorProvider(WidgetColors.CardSurface))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hoy",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.Indigo),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        val completedCount = allHabitsWithStats.count { 
                            val localCompleted = prefs[booleanPreferencesKey("habit_completed_${it.habit.id}")]
                            val fb = WidgetFeedbackManager.getFeedbackState(it.habit.id)
                            localCompleted ?: fb?.isOptimisticallyCompleted ?: it.isCompletedToday
                        }
                        Text(
                            text = "$completedCount/${allHabitsWithStats.size} completados",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    if (displayHabits.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin hábitos activos para hoy",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.TextSecondary),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    } else {
                        Column(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            displayHabits.forEachIndexed { index, item ->
                                if (index > 0) {
                                    Spacer(modifier = GlanceModifier.height(5.dp))
                                }
                                HabitRowItem(item = item, prefs = prefs)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitRowItem(item: HabitWithStats, prefs: Preferences) {
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

            // Habit title - Green when completed/marked, white when uncompleted
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // Streak flame + count (if currentStreak > 0)
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
                        modifier = GlanceModifier.size(13.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "$effectiveStreak",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Amber),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
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
            DashboardWidget().update(context, glanceId)
        } catch (_: Exception) {}

        // 3. Perform asynchronous background Room DB sync
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()
        repository.toggleHabitCompletion(habitId, today)

        // 4. Update all widgets across the launcher
        WidgetUpdater.refreshAll(context)
    }

    companion object {
        val habitIdKey = ActionParameters.Key<Long>("habit_id")
        val currentCompletedKey = ActionParameters.Key<Boolean>("current_completed")
    }
}

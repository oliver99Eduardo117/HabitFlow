package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
        val today = DateUtils.getTodayDateString()
        val allHabitsWithStats = WidgetRepositoryProvider.getHabitsWithStatsCached(context, today)

        provideContent {
            val totalHabits = allHabitsWithStats.size
            val completedCount = allHabitsWithStats.count { it.isCompletedToday }

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
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // Header row: "Hoy" (13sp medium DarkOnSurface) ... "X de Y" (11sp mutedText) - opens app
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(mainIntent)),
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

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    if (allHabitsWithStats.isEmpty()) {
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
                        LazyColumn(
                            modifier = GlanceModifier.fillMaxSize()
                        ) {
                            items(allHabitsWithStats) { item ->
                                HabitRowItem(item = item)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitRowItem(item: HabitWithStats) {
        val isCompleted = item.isCompletedToday

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(
                    actionRunCallback<ToggleHabitAction>(
                        actionParametersOf(
                            ToggleHabitAction.habitIdKey to item.habit.id,
                            ToggleHabitAction.widgetTypeKey to "today"
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox circle: 22dp (Emerald solid if completed, SurfaceVariant if not)
            val checkColor = if (isCompleted) WidgetColors.Emerald else WidgetColors.SurfaceVariant

            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(ColorProvider(checkColor)),
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

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Habit Icon (14dp tinted with habit color or letter fallback Box)
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
                    modifier = GlanceModifier.size(14.dp)
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(14.dp)
                        .cornerRadius(3.dp)
                        .background(ColorProvider(habitColor.copy(alpha = 0.25f))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.habit.title.take(1).uppercase(),
                        style = TextStyle(
                            color = ColorProvider(habitColor),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(6.dp))

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
            val effectiveStreak = item.currentStreak

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
        val widgetType = parameters[widgetTypeKey] ?: "today"

        android.util.Log.d("HabitFlowWidget", "onAction INICIO habitId=$habitId tipo=$widgetType")

        val t0 = System.currentTimeMillis()
        // 1. Synchronously execute real Room write and await
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()
        try {
            repository.toggleHabitCompletion(habitId, today)
        } catch (e: Exception) {
            android.util.Log.e("HabitFlowWidget", "toggleHabitCompletion falló (el log pudo persistirse igual)", e)
        }
        val tDb = System.currentTimeMillis() - t0

        // Invalidate cache immediately after write so the touched instance gets fresh data
        WidgetRepositoryProvider.invalidateHabitsCache()

        // 2. Phase 1: Update ONLY the touched widget instance (awaited)
        val tUpdateStart = System.currentTimeMillis()
        try {
            if (widgetType == "dashboard") {
                DashboardWidget().update(context, glanceId)
            } else {
                TodayWidget().update(context, glanceId)
            }
        } catch (e: Exception) {
            android.util.Log.e("HabitFlowWidget", "Error updating touched widget instance ($widgetType)", e)
        }
        val tTouched = System.currentTimeMillis() - tUpdateStart

        android.util.Log.d(
            "HabitFlowWidget",
            "ToggleHabitAction: DB toggle took ${tDb}ms, Touched widget ($widgetType) update took ${tTouched}ms"
        )

        // 3. Phase 2: Awaited refresh of all widgets
        try {
            WidgetUpdater.refreshAll(context)
        } catch (e: Exception) {
            android.util.Log.e("HabitFlowWidget", "refreshAll falló", e)
        }

        android.util.Log.d("HabitFlowWidget", "onAction FIN — refresco completado")
    }

    companion object {
        val habitIdKey = ActionParameters.Key<Long>("habit_id")
        val widgetTypeKey = ActionParameters.Key<String>("widget_type")
    }
}


package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
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
        val initialHabits = try {
            WidgetRepositoryProvider.habitsWithStatsFlow(context, today).first()
        } catch (_: Exception) {
            emptyList()
        }

        provideContent {
            val habitsWithStats by WidgetRepositoryProvider.habitsWithStatsFlow(context, today)
                .collectAsState(initial = initialHabits)
            val todayHabits = habitsWithStats.forToday()
            val totalHabits = todayHabits.size
            val completedCount = todayHabits.count { it.isCompletedToday }

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("widget_target_tab", "TODAY")
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .cornerRadius(WidgetDimens.ContainerRadius)
                    .background(ColorProvider(WidgetColors.Container))
                    .padding(WidgetDimens.ContainerPadding)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // Encabezado: abre la app
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
                        if (totalHabits > 0 && completedCount == totalHabits) {
                            DayCompletePill()
                        } else if (totalHabits > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$completedCount",
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

                    if (todayHabits.isEmpty()) {
                        NoHabitsTodayState(habitsWithStats)
                    } else {
                        SegmentedProgressBar(
                            habits = todayHabits,
                            modifier = GlanceModifier.padding(top = 10.dp, bottom = 6.dp)
                        )
                        LazyColumn(
                            modifier = GlanceModifier.fillMaxSize()
                        ) {
                            items(todayHabits, itemId = { it.habit.id }) { item ->
                                HabitWidgetRow(
                                    item = item,
                                    widgetType = "today",
                                    openAppIntent = mainIntent,
                                    rowHeight = 42.dp
                                )
                            }
                        }
                    }
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


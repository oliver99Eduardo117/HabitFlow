package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingJob: Job? = null

    fun scheduleRefresh(context: Context) {
        val appContext = context.applicationContext
        WidgetRepositoryProvider.invalidateHabitsCache()
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(120)
            refreshAll(appContext)
        }
    }

    fun enqueueRefresh(context: Context) {
        scheduleRefresh(context)
    }

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        WidgetRepositoryProvider.invalidateHabitsCache()

        val startTime = System.currentTimeMillis()
        coroutineScope {
            launch {
                try {
                    TodayWidget().updateAll(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HabitFlowWidget", "Error refreshing TodayWidget", e)
                }
            }
            launch {
                try {
                    DailyProgressWidget().updateAll(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HabitFlowWidget", "Error refreshing DailyProgressWidget", e)
                }
            }
            launch {
                try {
                    StreakWidget().updateAll(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HabitFlowWidget", "Error refreshing StreakWidget", e)
                }
            }
            launch {
                try {
                    ConsistencyWidget().updateAll(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HabitFlowWidget", "Error refreshing ConsistencyWidget", e)
                }
            }
            launch {
                try {
                    DashboardWidget().updateAll(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HabitFlowWidget", "Error refreshing DashboardWidget", e)
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.d("HabitFlowWidget", "refreshAll (all 5 widgets) took ${duration}ms")
    }
}


package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueueRefresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            refreshAll(appContext)
        }
    }

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext

        coroutineScope {
            launch {
                try {
                    TodayWidget().updateAll(appContext)
                } catch (_: Exception) { }
            }
            launch {
                try {
                    DailyProgressWidget().updateAll(appContext)
                } catch (_: Exception) { }
            }
            launch {
                try {
                    StreakWidget().updateAll(appContext)
                } catch (_: Exception) { }
            }
            launch {
                try {
                    ConsistencyWidget().updateAll(appContext)
                } catch (_: Exception) { }
            }
            launch {
                try {
                    DashboardWidget().updateAll(appContext)
                } catch (_: Exception) { }
            }
        }
    }
}


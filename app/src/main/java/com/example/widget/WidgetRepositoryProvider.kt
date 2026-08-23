package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.example.database.AppDatabase
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.HabitWithStats
import com.example.model.UserStats
import com.example.repository.HabitRepository
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean

data class WidgetStateSnapshot(
    val activeHabits: List<Habit> = emptyList(),
    val totalLogs: Int = 0,
    val todayLogs: List<HabitLog> = emptyList(),
    val userStats: UserStats? = null,
    val habitsWithStats: List<HabitWithStats> = emptyList()
)

object WidgetRepositoryProvider {
    @Volatile
    private var repository: HabitRepository? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerJob: Job? = null
    private val isObserving = AtomicBoolean(false)

    fun getRepository(context: Context): HabitRepository {
        return repository ?: synchronized(this) {
            repository ?: run {
                val appContext = context.applicationContext
                val database = AppDatabase.getInstance(appContext)
                HabitRepository(database, appContext).also {
                    repository = it
                    startObserving(appContext)
                }
            }
        }
    }

    /**
     * Exposes a Flow of widget-relevant state changes combining active habits,
     * logs, user statistics, and calculated daily stats.
     */
    @OptIn(FlowPreview::class)
    fun observeWidgetState(context: Context): Flow<WidgetStateSnapshot> {
        val repo = getRepository(context)
        val today = DateUtils.getTodayDateString()
        return combine(
            repo.activeHabits,
            repo.allLogs,
            repo.userStats,
            repo.getHabitsWithStats(today)
        ) { habits, logs, stats, habitsWithStats ->
            val todayLogs = logs.filter { it.date == today }
            WidgetStateSnapshot(
                activeHabits = habits,
                totalLogs = logs.size,
                todayLogs = todayLogs,
                userStats = stats,
                habitsWithStats = habitsWithStats
            )
        }
            .debounce(150)
            .distinctUntilChanged()
    }

    /**
     * Starts collecting the DataObserver flow if not already running,
     * triggering a global glance widget state update for all active widgets immediately
     * whenever any habit toggle, edit, or deletion occurs.
     */
    fun startObserving(context: Context) {
        val appContext = context.applicationContext
        if (isObserving.compareAndSet(false, true)) {
            observerJob = observeWidgetState(appContext)
                .onEach {
                    updateAllWidgets(appContext)
                }
                .launchIn(scope)
        }
    }

    /**
     * Updates all Glance widgets immediately using GlanceAppWidgetManager.
     */
    suspend fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        try {
            val manager = GlanceAppWidgetManager(appContext)

            // Today Widget
            val todayWidget = TodayWidget()
            manager.getGlanceIds(todayWidget.javaClass).forEach { glanceId ->
                try {
                    todayWidget.update(appContext, glanceId)
                } catch (_: Exception) {}
            }

            // Daily Progress Widget
            val progressWidget = DailyProgressWidget()
            manager.getGlanceIds(progressWidget.javaClass).forEach { glanceId ->
                try {
                    progressWidget.update(appContext, glanceId)
                } catch (_: Exception) {}
            }

            // Streak Widget
            val streakWidget = StreakWidget()
            manager.getGlanceIds(streakWidget.javaClass).forEach { glanceId ->
                try {
                    streakWidget.update(appContext, glanceId)
                } catch (_: Exception) {}
            }

            // Consistency Widget
            val consistencyWidget = ConsistencyWidget()
            manager.getGlanceIds(consistencyWidget.javaClass).forEach { glanceId ->
                try {
                    consistencyWidget.update(appContext, glanceId)
                } catch (_: Exception) {}
            }

            // Dashboard Widget
            val dashboardWidget = DashboardWidget()
            manager.getGlanceIds(dashboardWidget.javaClass).forEach { glanceId ->
                try {
                    dashboardWidget.update(appContext, glanceId)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            WidgetUpdater.refreshAll(appContext)
        }
    }
}


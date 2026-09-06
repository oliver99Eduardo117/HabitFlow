package com.example.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.database.AppDatabase
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.HabitWithStats
import com.example.model.UserStats
import com.example.repository.HabitRepository
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private const val TAG = "HabitFlowWidget"

    @Volatile
    private var repository: HabitRepository? = null

    private data class CachedHabitsStats(
        val date: String,
        val timestamp: Long,
        val data: List<HabitWithStats>
    )

    @Volatile
    private var habitsWithStatsCache: CachedHabitsStats? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerJob: Job? = null
    private val isObserving = AtomicBoolean(false)

    fun invalidateHabitsCache() {
        habitsWithStatsCache = null
    }

    suspend fun getHabitsWithStatsCached(context: Context, today: String): List<HabitWithStats> {
        val currentCache = habitsWithStatsCache
        val now = System.currentTimeMillis()
        if (currentCache != null && currentCache.date == today && (now - currentCache.timestamp) < 500) {
            return currentCache.data
        }
        val repo = getRepository(context)
        val freshData = try {
            repo.getHabitsWithStats(today).first()
        } catch (e: Exception) {
            emptyList()
        }
        habitsWithStatsCache = CachedHabitsStats(
            date = today,
            timestamp = System.currentTimeMillis(),
            data = freshData
        )
        return freshData
    }

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
    fun observeWidgetState(context: Context): Flow<WidgetStateSnapshot> {
        val repo = getRepository(context)
        return combine(
            repo.activeHabits,
            repo.allLogs,
            repo.userStats
        ) { habits, logs, stats ->
            val today = DateUtils.getTodayDateString()
            val todayLogs = logs.filter { it.date == today }
            val habitsWithStats = try {
                repo.getHabitsWithStats(today).first()
            } catch (_: Exception) {
                emptyList()
            }
            WidgetStateSnapshot(
                activeHabits = habits,
                totalLogs = logs.size,
                todayLogs = todayLogs,
                userStats = stats,
                habitsWithStats = habitsWithStats
            )
        }.distinctUntilChanged()
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
            val todayIds = manager.getGlanceIds(todayWidget.javaClass)
            Log.d(TAG, "updateAllWidgets: ${todayIds.size} glanceIds encontrados para TodayWidget")
            todayIds.forEach { glanceId ->
                try {
                    todayWidget.update(appContext, glanceId)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al actualizar widget (TodayWidget, glanceId=$glanceId)", e)
                }
            }

            // Daily Progress Widget
            val progressWidget = DailyProgressWidget()
            val progressIds = manager.getGlanceIds(progressWidget.javaClass)
            Log.d(TAG, "updateAllWidgets: ${progressIds.size} glanceIds encontrados para DailyProgressWidget")
            progressIds.forEach { glanceId ->
                try {
                    progressWidget.update(appContext, glanceId)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al actualizar widget (DailyProgressWidget, glanceId=$glanceId)", e)
                }
            }

            // Streak Widget
            val streakWidget = StreakWidget()
            val streakIds = manager.getGlanceIds(streakWidget.javaClass)
            Log.d(TAG, "updateAllWidgets: ${streakIds.size} glanceIds encontrados para StreakWidget")
            streakIds.forEach { glanceId ->
                try {
                    streakWidget.update(appContext, glanceId)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al actualizar widget (StreakWidget, glanceId=$glanceId)", e)
                }
            }

            // Consistency Widget
            val consistencyWidget = ConsistencyWidget()
            val consistencyIds = manager.getGlanceIds(consistencyWidget.javaClass)
            Log.d(TAG, "updateAllWidgets: ${consistencyIds.size} glanceIds encontrados para ConsistencyWidget")
            consistencyIds.forEach { glanceId ->
                try {
                    consistencyWidget.update(appContext, glanceId)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al actualizar widget (ConsistencyWidget, glanceId=$glanceId)", e)
                }
            }

            // Dashboard Widget
            val dashboardWidget = DashboardWidget()
            val dashboardIds = manager.getGlanceIds(dashboardWidget.javaClass)
            Log.d(TAG, "updateAllWidgets: ${dashboardIds.size} glanceIds encontrados para DashboardWidget")
            dashboardIds.forEach { glanceId ->
                try {
                    dashboardWidget.update(appContext, glanceId)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al actualizar widget (DashboardWidget, glanceId=$glanceId)", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al actualizar widget (error general en updateAllWidgets)", e)
            WidgetUpdater.refreshAll(appContext)
        }
    }
}


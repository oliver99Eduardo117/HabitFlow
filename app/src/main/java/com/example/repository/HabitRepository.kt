package com.example.repository

import android.content.Context
import com.example.database.AppDatabase
import com.example.model.*
import com.example.network.AiChatClient
import com.example.notification.NotificationHelper
import com.example.util.AiProviderPreferences
import com.example.util.DateUtils
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Elimina emojis y selectores de variación de textos generados por IA. */
private val EMOJI_REGEX = Regex(
    "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}" +
        "\\x{231A}-\\x{231B}\\x{23E9}-\\x{23FA}\\x{FE0F}\\x{200D}\\x{20E3}]"
)

private fun String.stripEmojis(): String =
    replace(EMOJI_REGEX, "").replace(Regex("\\s{2,}"), " ").trim()

/**
 * Regla del requisito previo. Se cumple si el requisito no existe o esta archivado,
 * si no esta programado ese dia, o si su registro de ese dia alcanza su meta.
 */
internal fun isDependencySatisfied(dependency: Habit?, dependencyLog: HabitLog?, date: String): Boolean {
    if (dependency == null || dependency.isArchived) return true
    val dayOfWeek = DateUtils.getDayOfWeek(date)
    val scheduled = dependency.frequencyDays.isEmpty() || dayOfWeek in dependency.frequencyDays
    if (!scheduled) return true
    return dependencyLog != null && dependencyLog.value >= dependency.targetValue
}

class HabitRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val habitDao = database.habitDao()
    private val habitLogDao = database.habitLogDao()
    private val subTaskDao = database.subTaskDao()
    private val subTaskLogDao = database.subTaskLogDao()
    private val categoryDao = database.categoryDao()
    private val userStatsDao = database.userStatsDao()

    val activeHabits: Flow<List<Habit>> = habitDao.getActiveHabits()
    val archivedHabits: Flow<List<Habit>> = habitDao.getArchivedHabits()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allLogs: Flow<List<HabitLog>> = habitLogDao.getAllLogs()
    val userStats: Flow<UserStats?> = userStatsDao.getUserStatsFlow()
    val allSubTasks: Flow<List<SubTask>> = subTaskDao.getAllSubTasks()

    fun getLogsForDate(date: String): Flow<List<HabitLog>> = habitLogDao.getLogsForDate(date)

    suspend fun getHabitById(habitId: Long): Habit? = withContext(Dispatchers.IO) {
        habitDao.getHabitById(habitId)
    }

    suspend fun getLogForHabitAndDate(habitId: Long, date: String): HabitLog? = withContext(Dispatchers.IO) {
        habitLogDao.getLogForHabitAndDate(habitId, date)
    }

    fun getSubTasksForHabit(habitId: Long): Flow<List<SubTask>> = subTaskDao.getSubTasksForHabit(habitId)

    /**
     * Combines active habits with logs for a specific date to compute completion status,
     * streaks, and dependencies.
     */
    fun getHabitsWithStats(selectedDate: String): Flow<List<HabitWithStats>> {
        return combine(
            habitDao.getActiveHabits(),
            habitLogDao.getAllLogs(),
            subTaskDao.getAllSubTasks(),
            subTaskLogDao.getLogsForDate(selectedDate)
        ) { habits, logs, allSubTasks, subTaskLogsForDate ->
            val doneSubTaskIds = subTaskLogsForDate.map { it.subTaskId }.toSet()
            val habitsById = habits.associateBy { it.id }
            val logsByHabit = logs.groupBy { it.habitId }
            val logsByHabitAndDate = logs.associateBy { "${it.habitId}_${it.date}" }
            val subTasksByHabit = allSubTasks.groupBy { it.habitId }
            val dayOfWeek = DateUtils.getDayOfWeek(selectedDate) // 1 = lunes, 7 = domingo

            habits.map { habit ->
                val habitLogs = logsByHabit[habit.id] ?: emptyList()
                val todayLog = logsByHabitAndDate["${habit.id}_$selectedDate"]
                val isCompletedToday = todayLog != null && todayLog.value >= habit.targetValue
                val completedDates = habitLogs.filter { it.value >= habit.targetValue }.map { it.date }.toSet()
                val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates, habit.frequencyDays)

                // Requisito previo (misma regla que aplican las escrituras)
                val dependency = habit.dependencyHabitId?.let { habitsById[it] }
                val isDependencyMet = habit.dependencyHabitId == null || isDependencySatisfied(
                    dependency,
                    dependency?.let { logsByHabitAndDate["${it.id}_$selectedDate"] },
                    selectedDate
                )

                HabitWithStats(
                    habit = habit,
                    todayLog = todayLog,
                    isCompletedToday = isCompletedToday,
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    totalCompletions = completedDates.size,
                    subTasks = (subTasksByHabit[habit.id] ?: emptyList()).map { st ->
                        st.copy(isCompleted = st.id in doneSubTaskIds)
                    },
                    isDependencyMet = isDependencyMet,
                    isScheduled = habit.frequencyDays.isEmpty() || dayOfWeek in habit.frequencyDays,
                    blockingHabitTitle = if (isDependencyMet) null else dependency?.title
                )
            }
        }
    }

    /** Habito requisito que bloquea avanzar `habit` en la fecha, o null si no hay bloqueo. */
    private suspend fun blockingDependency(habit: Habit, date: String): Habit? {
        val dependencyId = habit.dependencyHabitId ?: return null
        val dependency = habitDao.getHabitById(dependencyId)
        val dependencyLog = dependency?.let { habitLogDao.getLogForHabitAndDate(it.id, date) }
        return if (isDependencySatisfied(dependency, dependencyLog, date)) null else dependency
    }

    /** Titulo del requisito que bloquea avanzar el habito en la fecha, o null si no hay bloqueo. */
    suspend fun dependencyBlocker(habitId: Long, date: String): String? = withContext(Dispatchers.IO) {
        val habit = habitDao.getHabitById(habitId) ?: return@withContext null
        blockingDependency(habit, date)?.title
    }

    suspend fun saveHabit(habit: Habit, subTaskTitles: List<String> = emptyList()): Long = withContext(Dispatchers.IO) {
        val habitId = if (habit.id == 0L) {
            habitDao.insertHabit(habit)
        } else {
            habitDao.updateHabit(habit)
            habit.id
        }

        val wantedTitles = subTaskTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (habit.id == 0L) {
            // Habito nuevo: insertar las sub-rutinas recibidas
            if (wantedTitles.isNotEmpty()) {
                subTaskDao.insertSubTasks(wantedTitles.map { SubTask(habitId = habitId, title = it) })
            }
        } else {
            // Edicion: sincronizar conservando el estado de las que no cambiaron
            val existing = subTaskDao.getSubTasksForHabitOnce(habitId)
            existing.filter { it.title !in wantedTitles }.forEach {
                subTaskDao.deleteSubTask(it.id)
                subTaskLogDao.deleteForSubTask(it.id)
            }
            val existingTitles = existing.map { it.title }.toSet()
            val toInsert = wantedTitles.filter { it !in existingTitles }
            if (toInsert.isNotEmpty()) {
                subTaskDao.insertSubTasks(toInsert.map { SubTask(habitId = habitId, title = it) })
            }
            // Vinculo: agregar o quitar sub-rutinas puede cambiar si el habito queda hecho hoy
            reconcileHabitWithSubTasks(habit.copy(id = habitId), DateUtils.getTodayDateString())
        }

        if (habit.reminderTime != null) {
            NotificationHelper.scheduleHabitReminder(context, habit.copy(id = habitId))
        } else {
            NotificationHelper.cancelHabitReminder(context, habitId)
        }

        WidgetUpdater.scheduleRefresh(context)
        habitId
    }

    suspend fun setArchived(habitId: Long, isArchived: Boolean) = withContext(Dispatchers.IO) {
        if (isArchived) {
            NotificationHelper.cancelHabitReminder(context, habitId)
        } else {
            val habit = habitDao.getHabitById(habitId)
            if (habit != null && !habit.reminderTime.isNullOrBlank()) {
                NotificationHelper.scheduleHabitReminder(context, habit)
            }
        }
        habitDao.setArchivedStatus(habitId, isArchived)
        WidgetUpdater.scheduleRefresh(context)
    }

    suspend fun deleteHabit(habitId: Long) = withContext(Dispatchers.IO) {
        NotificationHelper.cancelHabitReminder(context, habitId)
        habitDao.deleteHabitById(habitId)
        subTaskDao.deleteSubTasksForHabit(habitId)
        subTaskLogDao.deleteForHabit(habitId)
        WidgetUpdater.scheduleRefresh(context)
    }

    suspend fun recordHabitProgress(
        habitId: Long,
        date: String,
        value: Float,
        notes: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val habit = habitDao.getHabitById(habitId) ?: return@withContext 0
        val existingLog = habitLogDao.getLogForHabitAndDate(habitId, date)
        val newValue: Float? = if (value <= 0f) null else value
        // Requisito previo: bloqueado no permite sumar avance (si permite bajar o borrar)
        if ((newValue ?: 0f) > (existingLog?.value ?: 0f) && blockingDependency(habit, date) != null) {
            return@withContext 0
        }

        if (newValue == null) {
            habitLogDao.deleteLog(habitId, date)
        } else {
            val log = existingLog?.copy(value = newValue, notes = notes, timestamp = System.currentTimeMillis())
                ?: HabitLog(habitId = habitId, date = date, value = newValue, notes = notes)
            habitLogDao.insertOrUpdateLog(log)
        }

        // Vinculo: en habitos sin unidad (por ejemplo, con temporizador), cruzar la meta marca o desmarca todas las sub-rutinas
        val wasDone = existingLog != null && existingLog.value >= habit.targetValue
        val nowDone = newValue != null && newValue >= habit.targetValue
        val subTaskXp = if (habit.unit.isEmpty() && wasDone != nowDone) setAllSubTasksForDate(habitId, date, nowDone) else 0
        val earnedXp = subTaskXp + applyCompletionChange(habit, existingLog?.value, newValue)
        if (newValue != null && newValue >= habit.targetValue && date == DateUtils.getTodayDateString()) {
            NotificationHelper.dismissActiveReminder(context, habitId)
        }
        earnedXp
    }

    /**
     * Suma minutos de una sesión de temporizador al progreso del día.
     * recordHabitProgress reemplaza el valor; esta función acumula.
     */
    suspend fun addTimerProgress(
        habitId: Long,
        date: String,
        minutes: Float,
        notes: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val existingLog = habitLogDao.getLogForHabitAndDate(habitId, date)
        val existingValue = existingLog?.value ?: 0f
        val existingNotes = existingLog?.notes.orEmpty()
        // Conservar la nota escrita a mano; solo se reemplaza una nota vacia o una automatica anterior
        val finalNotes = if (existingNotes.isBlank() || existingNotes.startsWith("Sesión de enfoque")) notes else existingNotes
        recordHabitProgress(habitId, date, existingValue + minutes, finalNotes)
    }

    suspend fun toggleHabitCompletion(habitId: Long, date: String): Int = withContext(Dispatchers.IO) {
        val habit = habitDao.getHabitById(habitId) ?: return@withContext 0
        val existingLog = habitLogDao.getLogForHabitAndDate(habitId, date)
        val isDone = existingLog != null && existingLog.value >= habit.targetValue
        // Requisito previo: bloqueado no permite completar (si permite desmarcar)
        if (!isDone && blockingDependency(habit, date) != null) return@withContext 0

        val newValue: Float? = if (isDone) {
            habitLogDao.deleteLog(habitId, date)
            null
        } else {
            // Completar conserva el registro existente (notas, id) y solo sube el valor a la meta
            val log = existingLog?.copy(value = habit.targetValue, timestamp = System.currentTimeMillis())
                ?: HabitLog(habitId = habitId, date = date, value = habit.targetValue, timestamp = System.currentTimeMillis())
            habitLogDao.insertOrUpdateLog(log)
            if (date == DateUtils.getTodayDateString()) {
                NotificationHelper.dismissActiveReminder(context, habitId)
            }
            habit.targetValue
        }

        // Vinculo: en habitos sin unidad, completar marca todas las sub-rutinas del dia y desmarcar las limpia
        val subTaskXp = if (habit.unit.isEmpty()) setAllSubTasksForDate(habitId, date, !isDone) else 0
        subTaskXp + applyCompletionChange(habit, existingLog?.value, newValue)
    }

    /**
     * Marca o desmarca una sub-rutina en una fecha y aplica el vinculo con su habito.
     * Devuelve el XP ganado (sub-rutina y, si aplica, habito completado).
     */
    suspend fun toggleSubTask(subTaskId: Long, isCompleted: Boolean, date: String): Int = withContext(Dispatchers.IO) {
        val subTask = subTaskDao.getSubTaskById(subTaskId) ?: return@withContext 0
        val habit = habitDao.getHabitById(subTask.habitId) ?: return@withContext 0
        // Requisito previo: bloqueado no permite marcar sub-rutinas (si permite desmarcarlas)
        if (isCompleted && blockingDependency(habit, date) != null) return@withContext 0
        var earnedXp = 0
        if (isCompleted) {
            val inserted = subTaskLogDao.insert(SubTaskLog(subTaskId = subTaskId, habitId = habit.id, date = date)) != -1L
            if (!inserted) return@withContext 0
            earnedXp += adjustSubTaskXp(1)
        } else {
            val removed = subTaskLogDao.delete(subTaskId, date)
            if (removed == 0) return@withContext 0
            adjustSubTaskXp(-removed)
        }
        earnedXp + reconcileHabitWithSubTasks(habit, date)
    }

    /** Suma o resta el XP de sub-rutinas marcadas o desmarcadas. Devuelve el XP ganado. */
    private suspend fun adjustSubTaskXp(rowDelta: Int): Int {
        if (rowDelta == 0) return 0
        val stats = userStatsDao.getUserStats() ?: UserStats()
        val perRow = if (stats.isHardcoreMode) {
            (GamificationConfig.XP_SUBTASK_COMPLETION * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt()
        } else {
            GamificationConfig.XP_SUBTASK_COMPLETION
        }
        val delta = perRow * rowDelta
        val newXp = maxOf(0, stats.xp + delta)
        val updated = stats.copy(xp = newXp, level = GamificationConfig.calculateLevel(newXp))
        if (delta > 0) checkAndSaveGamification(updated) else userStatsDao.insertOrUpdate(updated)
        return maxOf(0, delta)
    }

    /** Marca (done = true) o desmarca todas las sub-rutinas del habito en la fecha. Devuelve el XP ganado. */
    private suspend fun setAllSubTasksForDate(habitId: Long, date: String, done: Boolean): Int {
        val subTasks = subTaskDao.getSubTasksForHabitOnce(habitId)
        if (subTasks.isEmpty()) return 0
        var rowDelta = 0
        subTasks.forEach { st ->
            if (done) {
                if (subTaskLogDao.insert(SubTaskLog(subTaskId = st.id, habitId = habitId, date = date)) != -1L) rowDelta++
            } else {
                rowDelta -= subTaskLogDao.delete(st.id, date)
            }
        }
        return adjustSubTaskXp(rowDelta)
    }

    /**
     * Vinculo habito / sub-rutinas (solo habitos sin unidad y con al menos una sub-rutina):
     * deja el registro del habito como hecho si todas las sub-rutinas estan marcadas en la fecha,
     * y lo quita si falta alguna. Devuelve el XP ganado.
     */
    private suspend fun reconcileHabitWithSubTasks(habit: Habit, date: String): Int {
        if (habit.unit.isNotEmpty()) return 0
        val subTasks = subTaskDao.getSubTasksForHabitOnce(habit.id)
        if (subTasks.isEmpty()) return 0
        val doneIds = subTaskLogDao.getLogsForHabitAndDate(habit.id, date).map { it.subTaskId }.toSet()
        val allDone = subTasks.all { it.id in doneIds }
        val existingLog = habitLogDao.getLogForHabitAndDate(habit.id, date)
        val isDone = existingLog != null && existingLog.value >= habit.targetValue
        if (allDone == isDone) return 0
        if (allDone && blockingDependency(habit, date) != null) return 0

        val newValue: Float? = if (allDone) {
            val log = existingLog?.copy(value = habit.targetValue, timestamp = System.currentTimeMillis())
                ?: HabitLog(habitId = habit.id, date = date, value = habit.targetValue, timestamp = System.currentTimeMillis())
            habitLogDao.insertOrUpdateLog(log)
            if (date == DateUtils.getTodayDateString()) {
                NotificationHelper.dismissActiveReminder(context, habit.id)
            }
            habit.targetValue
        } else {
            habitLogDao.deleteLog(habit.id, date)
            null
        }
        return applyCompletionChange(habit, existingLog?.value, newValue)
    }

    suspend fun addSubTask(habitId: Long, title: String) = withContext(Dispatchers.IO) {
        subTaskDao.insertSubTask(SubTask(habitId = habitId, title = title, isCompleted = false))
    }

    suspend fun addCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(oldName: String, updatedCategory: Category) = withContext(Dispatchers.IO) {
        if (oldName != updatedCategory.name) {
            // New primary key: insert new category, update associated habits, then delete old category
            categoryDao.insertCategory(updatedCategory)
            habitDao.updateHabitsCategory(oldName, updatedCategory.name)
            categoryDao.deleteCategoryByName(oldName)
        } else {
            categoryDao.insertCategory(updatedCategory)
        }
    }

    suspend fun deleteCategory(categoryName: String, fallbackCategory: String = "Rutina Personal") = withContext(Dispatchers.IO) {
        // Reassign habits in this category to the fallback category if any exist
        val count = habitDao.getHabitsCountByCategory(categoryName)
        if (count > 0) {
            // Ensure fallback category exists
            val existingFallback = categoryDao.getCategoryByName(fallbackCategory)
            if (existingFallback == null) {
                categoryDao.insertCategory(
                    Category(
                        name = fallbackCategory,
                        colorHex = "#EC4899",
                        iconName = "person",
                        isDefault = true
                    )
                )
            }
            habitDao.updateHabitsCategory(categoryName, fallbackCategory)
        }
        categoryDao.deleteCategoryByName(categoryName)
    }

    suspend fun getHabitsCountForCategory(categoryName: String): Int = withContext(Dispatchers.IO) {
        habitDao.getHabitsCountByCategory(categoryName)
    }

    /** XP que vale un registro con este valor. 0 si no alcanza la meta. */
    private fun completionXp(habit: Habit, value: Float?, hardcore: Boolean): Int {
        if (value == null || value < habit.targetValue) return 0
        var base = GamificationConfig.XP_HABIT_COMPLETION
        if (value > habit.targetValue) base += GamificationConfig.XP_OVERACHIEVEMENT_BONUS
        return if (hardcore) (base * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt() else base
    }

    /**
     * Aplica al perfil la diferencia de XP entre el valor anterior y el nuevo de un registro.
     * Debe llamarse DESPUES de escribir el registro en la base de datos.
     * Devuelve el XP ganado (0 si fue neutro o negativo).
     */
    private suspend fun applyCompletionChange(habit: Habit, oldValue: Float?, newValue: Float?): Int {
        val stats = userStatsDao.getUserStats() ?: UserStats()
        val oldXp = completionXp(habit, oldValue, stats.isHardcoreMode)
        val newXp = completionXp(habit, newValue, stats.isHardcoreMode)
        val delta = newXp - oldXp
        val wasCompleted = oldXp > 0
        val isCompleted = newXp > 0
        if (delta == 0 && wasCompleted == isCompleted) return 0

        val totalXp = maxOf(0, stats.xp + delta)
        var updated = stats.copy(
            xp = totalXp,
            level = GamificationConfig.calculateLevel(totalXp),
            totalCheckIns = when {
                isCompleted && !wasCompleted -> stats.totalCheckIns + 1
                wasCompleted && !isCompleted -> maxOf(0, stats.totalCheckIns - 1)
                else -> stats.totalCheckIns
            }
        )

        if (isCompleted && !wasCompleted) {
            val allLogs = habitLogDao.getAllLogs().first()
            val datesWithCompletion = allLogs.filter { it.value > 0f }.map { it.date }.toSet()
            val (_, bestStreak) = DateUtils.calculateStreak(datesWithCompletion)
            updated = updated.copy(
                bestStreakAllTime = maxOf(stats.bestStreakAllTime, bestStreak),
                lastActiveDate = DateUtils.getTodayDateString()
            )
        }

        if (delta > 0) checkAndSaveGamification(updated) else userStatsDao.insertOrUpdate(updated)
        return maxOf(0, delta)
    }

    private suspend fun checkAndSaveGamification(stats: UserStats) {
        val updatedBadges = stats.unlockedBadgeIds.toMutableList()
        AllBadges.forEach { badge ->
            if (!updatedBadges.contains(badge.id)) {
                val shouldUnlock = when {
                    badge.requiredCompletions > 0 && stats.totalCheckIns >= badge.requiredCompletions -> true
                    badge.requiredXp > 0 && stats.xp >= badge.requiredXp -> true
                    badge.requiredStreak > 0 && stats.bestStreakAllTime >= badge.requiredStreak -> true
                    badge.requiredFocusMinutes > 0 && stats.totalFocusMinutes >= badge.requiredFocusMinutes -> true
                    else -> false
                }
                if (shouldUnlock) {
                    updatedBadges.add(badge.id)
                }
            }
        }

        val finalStats = stats.copy(unlockedBadgeIds = updatedBadges)
        userStatsDao.insertOrUpdate(finalStats)
    }

    /**
     * Checks if the current streak for a given habit has reached a gamification milestone (e.g. 3, 7, 14, 21, 30 days).
     * If so, awards bonus milestone XP and returns the StreakMilestoneEvent.
     */
    suspend fun checkStreakMilestone(habitId: Long): StreakMilestoneEvent? = withContext(Dispatchers.IO) {
        var habit = habitDao.getHabitById(habitId) ?: return@withContext null
        val logs = habitLogDao.getLogsForHabit(habitId).first()
        val completedDates = logs.filter { it.value >= habit.targetValue }.map { it.date }.toSet()
        val (currentStreak, _) = DateUtils.calculateStreak(completedDates, habit.frequencyDays)

        if (currentStreak == 1 && habit.lastMilestoneStreakClaimed != 0) {
            habit = habit.copy(lastMilestoneStreakClaimed = 0)
            habitDao.updateHabit(habit)
        }

        if (currentStreak > habit.lastMilestoneStreakClaimed) {
            val milestone = StreakMilestones.getMilestone(habitId, habit.title, currentStreak)
            if (milestone != null) {
                habitDao.updateHabit(habit.copy(lastMilestoneStreakClaimed = currentStreak))

                val currentStats = userStatsDao.getUserStats() ?: UserStats()
                val bonus = if (currentStats.isHardcoreMode) {
                    (milestone.xpBonus * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt()
                } else {
                    milestone.xpBonus
                }
                val newXp = currentStats.xp + bonus
                val newLevel = GamificationConfig.calculateLevel(newXp)
                checkAndSaveGamification(currentStats.copy(xp = newXp, level = newLevel))

                return@withContext milestone
            }
        }
        null
    }

    suspend fun addFocusSession(minutes: Int) = withContext(Dispatchers.IO) {
        val stats = userStatsDao.getUserStats() ?: UserStats()
        var earnedXp = minutes * GamificationConfig.XP_FOCUS_PER_MINUTE
        if (stats.isHardcoreMode) {
            earnedXp = (earnedXp * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt()
        }
        val newXp = stats.xp + earnedXp
        val newLevel = GamificationConfig.calculateLevel(newXp)
        val updated = stats.copy(
            xp = newXp,
            level = newLevel,
            totalFocusMinutes = stats.totalFocusMinutes + minutes
        )
        checkAndSaveGamification(updated)
    }

    suspend fun updateHardcoreMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        val stats = userStatsDao.getUserStats() ?: UserStats()
        userStatsDao.insertOrUpdate(stats.copy(isHardcoreMode = enabled))
    }

    /**
     * AI Routine Analysis & Insights (Universal OpenAI Chat Completions endpoint or Honest Local Fallback)
     */
     suspend fun generateSmartInsights(): List<String> = withContext(Dispatchers.IO) {
        val logs = habitLogDao.getAllLogs().first()
        val habits = habitDao.getActiveHabits().first()
        val stats = userStatsDao.getUserStats() ?: UserStats()

        val aiPrefs = AiProviderPreferences.getInstance(context)
        val isAiEnabled = aiPrefs.isEnabled.value
        val baseUrl = aiPrefs.baseUrl.value
        val apiKey = aiPrefs.apiKey.value
        val modelName = aiPrefs.modelName.value

        if (!isAiEnabled || baseUrl.isBlank() || modelName.isBlank()) {
            return@withContext buildHonestLocalInsights(habits, logs, stats)
        }

        // Build data summary for LLM prompt
        val logsByHabit = logs.groupBy { it.habitId }
        val habitsSummary = habits.joinToString("\n") { h ->
            val habitLogs = logsByHabit[h.id] ?: emptyList()
            val completedDates = habitLogs.filter { it.value >= h.targetValue }.map { it.date }.toSet()
            val (curStreak, bestStreak) = DateUtils.calculateStreak(completedDates, h.frequencyDays)
            "- ${h.title} (Categoría: ${h.category}, Meta: ${h.targetValue} ${h.unit}, Racha actual: $curStreak días, Mejor racha: $bestStreak días, Total completados: ${completedDates.size})"
        }

        val levelInfo = GamificationConfig.getProgress(stats.xp)
        val statsSummary = """
            - Nivel de usuario: Lv.${levelInfo.currentLevel} (${levelInfo.levelTitle})
            - XP acumulada: ${stats.xp} XP (Próximo nivel en ${levelInfo.xpNeededForNextLevel} XP)
            - Total de check-ins registrados: ${stats.totalCheckIns}
            - Mejor racha histórica: ${stats.bestStreakAllTime} días
            - Minutos de enfoque (Pomodoro): ${stats.totalFocusMinutes} min
            - Logros desbloqueados: ${stats.unlockedBadgeIds.size} de ${AllBadges.size}
        """.trimIndent()

        val systemPrompt = "Eres un coach experto en creación de hábitos, productividad y psicología del comportamiento. Analiza los datos reales del usuario en HabitFlow y genera exactamente de 3 a 4 insights concisos, accionables y motivadores en español. Cada insight debe tener 1 o 2 oraciones máximo, escrito solo con texto: no uses emojis, pictogramas ni símbolos decorativos. Sé honesto, empático y directo sin inventar estadísticas o números que no estén en los datos."

        val userPrompt = """
            Aquí están los datos de mis hábitos y progreso actual en HabitFlow:

            [HÁBITOS ACTIVOS]
            ${habitsSummary.ifBlank { "No hay hábitos activos registrados aún." }}

            [ESTADÍSTICAS GLOBALES]
            $statsSummary

            Genera exactamente de 3 a 4 insights/recomendaciones breves basadas estrictamente en mis datos reales.
        """.trimIndent()

        val result = AiChatClient.getChatCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = modelName,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )

        result.fold(
            onSuccess = { aiText ->
                val lines = aiText.lines()
                    .map { it.stripEmojis() }
                    .filter { it.isNotBlank() }
                    .map { line ->
                        line.replace(Regex("^(\\d+\\.|[-*•])\\s*"), "").trim()
                    }
                    .filter { it.isNotBlank() }

                if (lines.isNotEmpty()) {
                    lines
                } else {
                    buildHonestLocalInsights(habits, logs, stats)
                }
            },
            onFailure = {
                buildHonestLocalInsights(habits, logs, stats)
            }
        )
    }

    private fun buildHonestLocalInsights(
        habits: List<Habit>,
        logs: List<HabitLog>,
        stats: UserStats
    ): List<String> {
        if (logs.isEmpty() || habits.isEmpty()) {
            return listOf(
                "Comienza completando tus primeros hábitos diarios para desbloquear el análisis inteligente de correlaciones.",
                "Consejo Pro: Agrupa hábitos matutinos como hidratación y estiramientos (técnica Habit Stacking) para duplicar tu consistencia."
            )
        }

        val insights = mutableListOf<String>()
        val completedLogs = logs.filter { it.value > 0f }
        insights.add("Has registrado ${completedLogs.size} check-ins reales en total con una tendencia de progreso constante.")

        val completionsByHabit = completedLogs.groupBy { it.habitId }
        val topHabitEntry = completionsByHabit.maxByOrNull { it.value.size }
        if (topHabitEntry != null) {
            val topHabit = habits.find { it.id == topHabitEntry.key }
            if (topHabit != null) {
                insights.add("Tu hábito ancla es '${topHabit.title}' con ${topHabitEntry.value.size} completados reales. ¡Úsalo como detonante para hábitos más difíciles!")
            }
        }

        val topCategory = habits.groupBy { it.category }.maxByOrNull { it.value.size }
        if (topCategory != null && topCategory.value.size > 1) {
            insights.add("Tu categoría con mayor enfoque es '${topCategory.key}' con ${topCategory.value.size} hábitos activos configurados.")
        }

        if (stats.bestStreakAllTime > 0) {
            insights.add("Récord de consistencia: Tu mejor racha histórica es de ${stats.bestStreakAllTime} días seguidos. ¡Mantén el ritmo hoy para superarla!")
        } else {
            insights.add("Recomendación de Ritmo: Mantén tu racha activa hoy para asegurar la bonificación de XP y no romper tu cadena de progreso.")
        }

        return insights
    }

    /**
     * Export all data to JSON string for backup and restore.
     */
    suspend fun exportDataJson(): String = withContext(Dispatchers.IO) {
        val habits = habitDao.getAllHabits().first()
        val logs = habitLogDao.getAllLogs().first()
        val subTasks = subTaskDao.getAllSubTasks().first()
        val categories = categoryDao.getAllCategories().first()
        val stats = userStatsDao.getUserStats() ?: UserStats()

        val root = JSONObject()
        root.put("appName", "HabitFlow")
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        // 1. Categories
        val categoriesArray = JSONArray()
        categories.forEach { c ->
            val obj = JSONObject().apply {
                put("name", c.name)
                put("colorHex", c.colorHex)
                put("iconName", c.iconName)
                put("isDefault", c.isDefault)
            }
            categoriesArray.put(obj)
        }
        root.put("categories", categoriesArray)

        // 2. Habits
        val habitsArray = JSONArray()
        habits.forEach { h ->
            val obj = JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("description", h.description)
                put("category", h.category)
                put("colorHex", h.colorHex)
                put("iconName", h.iconName)
                put("frequencyDays", JSONArray(h.frequencyDays))
                put("isArchived", h.isArchived)
                put("orderIndex", h.orderIndex)
                put("unit", h.unit)
                put("targetValue", h.targetValue.toDouble())
                put("progressiveIncrease", h.progressiveIncrease.toDouble())
                put("hasTimer", h.hasTimer)
                put("timerDurationMinutes", h.timerDurationMinutes)
                put("reminderTime", h.reminderTime ?: "")
                put("reminderMinutesAdvance", h.reminderMinutesAdvance)
                put("reminderCustomMessage", h.reminderCustomMessage ?: "")
                if (h.parentHabitId != null) put("parentHabitId", h.parentHabitId)
                if (h.dependencyHabitId != null) put("dependencyHabitId", h.dependencyHabitId)
                put("lastMilestoneStreakClaimed", h.lastMilestoneStreakClaimed)
                put("createdAt", h.createdAt)
            }
            habitsArray.put(obj)
        }
        root.put("habits", habitsArray)

        // 3. SubTasks
        val subTasksArray = JSONArray()
        subTasks.forEach { st ->
            val obj = JSONObject().apply {
                put("id", st.id)
                put("habitId", st.habitId)
                put("title", st.title)
                put("isCompleted", st.isCompleted)
                put("date", st.date)
            }
            subTasksArray.put(obj)
        }
        root.put("subTasks", subTasksArray)

        // 3b. Estado diario de sub-rutinas
        val subTaskLogsArray = JSONArray()
        subTaskLogDao.getAllOnce().forEach { l ->
            subTaskLogsArray.put(JSONObject().apply {
                put("subTaskId", l.subTaskId)
                put("habitId", l.habitId)
                put("date", l.date)
            })
        }
        root.put("subTaskLogs", subTaskLogsArray)

        // 4. Habit Logs
        val logsArray = JSONArray()
        logs.forEach { l ->
            val obj = JSONObject().apply {
                put("id", l.id)
                put("habitId", l.habitId)
                put("date", l.date)
                put("value", l.value.toDouble())
                put("notes", l.notes)
                put("timestamp", l.timestamp)
            }
            logsArray.put(obj)
        }
        root.put("logs", logsArray)

        // 5. User Stats & Gamification
        val statsObj = JSONObject().apply {
            put("id", stats.id)
            put("xp", stats.xp)
            put("level", stats.level)
            put("totalCheckIns", stats.totalCheckIns)
            put("bestStreakAllTime", stats.bestStreakAllTime)
            put("isHardcoreMode", stats.isHardcoreMode)
            put("unlockedBadgeIds", JSONArray(stats.unlockedBadgeIds))
            put("totalFocusMinutes", stats.totalFocusMinutes)
            put("lastActiveDate", stats.lastActiveDate)
        }
        root.put("userStats", statsObj)

        root.toString(2)
    }

    /**
     * Parses a JSON backup string to provide a preview summary without modifying the database.
     */
    fun parseBackupPreview(jsonString: String): Result<RestoreSummary> {
        return try {
            val root = JSONObject(jsonString)
            val habitsArray = root.optJSONArray("habits") ?: JSONArray()
            val logsArray = root.optJSONArray("logs") ?: JSONArray()
            val subTasksArray = root.optJSONArray("subTasks") ?: JSONArray()
            val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
            val statsObj = root.optJSONObject("userStats")

            val xp = statsObj?.optInt("xp", 0) ?: 0
            val level = statsObj?.optInt("level", 1) ?: 1
            val isHardcore = statsObj?.optBoolean("isHardcoreMode", false) ?: false
            val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

            Result.success(
                RestoreSummary(
                    habitsCount = habitsArray.length(),
                    logsCount = logsArray.length(),
                    subTasksCount = subTasksArray.length(),
                    categoriesCount = categoriesArray.length(),
                    userLevel = level,
                    userXp = xp,
                    exportedAt = exportedAt,
                    isHardcoreMode = isHardcore
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically restores the Room database from a valid JSON backup string.
     */
    suspend fun restoreDataJson(jsonString: String): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val habitsArray = root.optJSONArray("habits") ?: JSONArray()
            val logsArray = root.optJSONArray("logs") ?: JSONArray()
            val subTasksArray = root.optJSONArray("subTasks") ?: JSONArray()
            val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
            val statsObj = root.optJSONObject("userStats")

            // Parse Categories
            val restoredCategories = mutableListOf<Category>()
            for (i in 0 until categoriesArray.length()) {
                val obj = categoriesArray.getJSONObject(i)
                restoredCategories.add(
                    Category(
                        name = obj.getString("name"),
                        colorHex = obj.optString("colorHex", "#6366F1"),
                        iconName = obj.optString("iconName", "category"),
                        isDefault = obj.optBoolean("isDefault", false)
                    )
                )
            }

            // Parse Habits
            val restoredHabits = mutableListOf<Habit>()
            for (i in 0 until habitsArray.length()) {
                val obj = habitsArray.getJSONObject(i)
                val freqJson = obj.optJSONArray("frequencyDays")
                val freqDays = if (freqJson != null) {
                    (0 until freqJson.length()).map { freqJson.getInt(it) }
                } else listOf(1, 2, 3, 4, 5, 6, 7)

                restoredHabits.add(
                    Habit(
                        id = obj.optLong("id", 0L),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "General"),
                        colorHex = obj.optString("colorHex", "#6366F1"),
                        iconName = obj.optString("iconName", "check_circle"),
                        frequencyDays = freqDays,
                        isArchived = obj.optBoolean("isArchived", false),
                        orderIndex = obj.optInt("orderIndex", i),
                        unit = obj.optString("unit", ""),
                        targetValue = obj.optDouble("targetValue", 1.0).toFloat(),
                        progressiveIncrease = obj.optDouble("progressiveIncrease", 0.0).toFloat(),
                        hasTimer = obj.optBoolean("hasTimer", false),
                        timerDurationMinutes = obj.optInt("timerDurationMinutes", 25),
                        reminderTime = obj.optString("reminderTime", "").takeIf { it.isNotBlank() },
                        reminderMinutesAdvance = obj.optInt("reminderMinutesAdvance", 0),
                        reminderCustomMessage = obj.optString("reminderCustomMessage", "").takeIf { it.isNotBlank() },
                        parentHabitId = if (obj.has("parentHabitId") && !obj.isNull("parentHabitId")) obj.getLong("parentHabitId") else null,
                        dependencyHabitId = if (obj.has("dependencyHabitId") && !obj.isNull("dependencyHabitId")) obj.getLong("dependencyHabitId") else null,
                        lastMilestoneStreakClaimed = obj.optInt("lastMilestoneStreakClaimed", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // Parse SubTasks
            val restoredSubTasks = mutableListOf<SubTask>()
            for (i in 0 until subTasksArray.length()) {
                val obj = subTasksArray.getJSONObject(i)
                restoredSubTasks.add(
                    SubTask(
                        id = obj.optLong("id", 0L),
                        habitId = obj.getLong("habitId"),
                        title = obj.getString("title"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        date = obj.optString("date", "")
                    )
                )
            }

            // Parse Habit Logs
            val restoredLogs = mutableListOf<HabitLog>()
            for (i in 0 until logsArray.length()) {
                val obj = logsArray.getJSONObject(i)
                restoredLogs.add(
                    HabitLog(
                        id = obj.optLong("id", 0L),
                        habitId = obj.getLong("habitId"),
                        date = obj.getString("date"),
                        value = obj.optDouble("value", 1.0).toFloat(),
                        notes = obj.optString("notes", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            // Parse Sub-task Logs. Los respaldos anteriores no lo traen: se reconstruye
            // desde el estado heredado de sub_tasks y desde los dias completados.
            val subTaskLogsArray = root.optJSONArray("subTaskLogs")
            val restoredSubTaskLogs: List<SubTaskLog> = if (subTaskLogsArray != null) {
                (0 until subTaskLogsArray.length()).map { i ->
                    val obj = subTaskLogsArray.getJSONObject(i)
                    SubTaskLog(
                        subTaskId = obj.getLong("subTaskId"),
                        habitId = obj.getLong("habitId"),
                        date = obj.getString("date")
                    )
                }
            } else {
                val legacy = restoredSubTasks.filter { it.isCompleted && it.date.isNotEmpty() }
                    .map { SubTaskLog(subTaskId = it.id, habitId = it.habitId, date = it.date) }
                val habitsById = restoredHabits.associateBy { it.id }
                val subTasksByHabit = restoredSubTasks.groupBy { it.habitId }
                val fromCompletedDays = restoredLogs.flatMap { log ->
                    val h = habitsById[log.habitId]
                    if (h != null && h.unit.isEmpty() && log.value >= h.targetValue) {
                        subTasksByHabit[h.id].orEmpty().map { SubTaskLog(subTaskId = it.id, habitId = h.id, date = log.date) }
                    } else {
                        emptyList()
                    }
                }
                (legacy + fromCompletedDays).distinctBy { it.subTaskId to it.date }
            }

            // Parse User Stats
            val restoredStats = if (statsObj != null) {
                val badgesJson = statsObj.optJSONArray("unlockedBadgeIds")
                val badgeList = if (badgesJson != null) {
                    (0 until badgesJson.length()).map { badgesJson.getString(it) }
                } else emptyList()

                UserStats(
                    id = 1,
                    xp = statsObj.optInt("xp", 120),
                    level = statsObj.optInt("level", 1),
                    totalCheckIns = statsObj.optInt("totalCheckIns", restoredLogs.size),
                    bestStreakAllTime = statsObj.optInt("bestStreakAllTime", 0),
                    isHardcoreMode = statsObj.optBoolean("isHardcoreMode", false),
                    unlockedBadgeIds = badgeList,
                    totalFocusMinutes = statsObj.optInt("totalFocusMinutes", 0),
                    lastActiveDate = statsObj.optString("lastActiveDate", DateUtils.getTodayDateString())
                )
            } else {
                UserStats(id = 1, totalCheckIns = restoredLogs.size)
            }

            // Execute Transactional Database Reset & Insertion
            database.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    // 1. Clear existing data
                    subTaskDao.deleteAllSubTasks()
                    subTaskLogDao.deleteAll()
                    habitLogDao.deleteAllLogs()
                    habitDao.deleteAllHabits()
                    if (restoredCategories.isNotEmpty()) {
                        categoryDao.deleteAllCategories()
                    }

                    // 2. Insert Restored Data
                    if (restoredCategories.isNotEmpty()) {
                        categoryDao.insertCategoriesReplace(restoredCategories)
                    } else {
                        categoryDao.insertCategories(DefaultCategories)
                    }

                    if (restoredHabits.isNotEmpty()) {
                        habitDao.insertHabits(restoredHabits)
                    }

                    if (restoredSubTasks.isNotEmpty()) {
                        subTaskDao.insertSubTasks(restoredSubTasks)
                    }

                    if (restoredSubTaskLogs.isNotEmpty()) {
                        subTaskLogDao.insertAll(restoredSubTaskLogs)
                    }

                    if (restoredLogs.isNotEmpty()) {
                        habitLogDao.insertLogs(restoredLogs)
                    }

                    userStatsDao.insertOrUpdate(restoredStats)
                }
            }

            // Reschedule Reminders for active restored habits
            NotificationHelper.rescheduleAllReminders(context)

            // Coalesced widget refresh after data restore
            WidgetUpdater.scheduleRefresh(context)

            val summary = RestoreSummary(
                habitsCount = restoredHabits.size,
                logsCount = restoredLogs.size,
                subTasksCount = restoredSubTasks.size,
                categoriesCount = if (restoredCategories.isNotEmpty()) restoredCategories.size else DefaultCategories.size,
                userLevel = restoredStats.level,
                userXp = restoredStats.xp,
                exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
                isHardcoreMode = restoredStats.isHardcoreMode
            )

            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export habit logs to CSV for external spreadsheet analysis (Excel/Google Sheets).
     */
    suspend fun exportDataCsv(): String = withContext(Dispatchers.IO) {
        val habits = habitDao.getAllHabits().first().associateBy { it.id }
        val logs = habitLogDao.getAllLogs().first()

        val sb = StringBuilder()
        sb.append("Fecha,Habito,Categoria,Valor,Unidad,Notas\n")
        logs.sortedByDescending { it.date }.forEach { log ->
            val habit = habits[log.habitId]
            val habitTitle = habit?.title?.replace(",", " ") ?: "Desconocido"
            val category = habit?.category?.replace(",", " ") ?: "General"
            val unit = habit?.unit ?: ""
            val notes = log.notes.replace(",", " ").replace("\n", " ")
            sb.append("${log.date},$habitTitle,$category,${log.value},$unit,$notes\n")
        }
        sb.toString()
    }
}

data class RestoreSummary(
    val habitsCount: Int,
    val logsCount: Int,
    val subTasksCount: Int,
    val categoriesCount: Int,
    val userLevel: Int,
    val userXp: Int,
    val exportedAt: Long = 0L,
    val isHardcoreMode: Boolean = false
)

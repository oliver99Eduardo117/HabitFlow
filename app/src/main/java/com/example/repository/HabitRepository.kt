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

class HabitRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val habitDao = database.habitDao()
    private val habitLogDao = database.habitLogDao()
    private val subTaskDao = database.subTaskDao()
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
            subTaskDao.getAllSubTasks()
        ) { habits, logs, allSubTasks ->
            val logsByHabit = logs.groupBy { it.habitId }
            val logsByHabitAndDate = logs.associateBy { "${it.habitId}_${it.date}" }
            val subTasksByHabit = allSubTasks.groupBy { it.habitId }

            habits.map { habit ->
                val habitLogs = logsByHabit[habit.id] ?: emptyList()
                val todayLog = logsByHabitAndDate["${habit.id}_$selectedDate"]
                val isCompletedToday = todayLog != null && todayLog.value >= habit.targetValue
                val completedDates = habitLogs.filter { it.value >= habit.targetValue }.map { it.date }.toSet()
                val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates)

                // Check dependency
                val isDependencyMet = if (habit.dependencyHabitId != null) {
                    val depLog = logsByHabitAndDate["${habit.dependencyHabitId}_$selectedDate"]
                    depLog != null && depLog.value > 0f
                } else {
                    true
                }

                HabitWithStats(
                    habit = habit,
                    todayLog = todayLog,
                    isCompletedToday = isCompletedToday,
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    totalCompletions = completedDates.size,
                    subTasks = subTasksByHabit[habit.id] ?: emptyList(),
                    isDependencyMet = isDependencyMet
                )
            }
        }
    }

    suspend fun saveHabit(habit: Habit, subTaskTitles: List<String> = emptyList()): Long = withContext(Dispatchers.IO) {
        val habitId = if (habit.id == 0L) {
            habitDao.insertHabit(habit)
        } else {
            habitDao.updateHabit(habit)
            habit.id
        }

        if (subTaskTitles.isNotEmpty()) {
            subTaskDao.deleteSubTasksForHabit(habitId)
            val subTasks = subTaskTitles.map { title ->
                SubTask(habitId = habitId, title = title, isCompleted = false)
            }
            subTaskDao.insertSubTasks(subTasks)
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
        WidgetUpdater.scheduleRefresh(context)
    }

    suspend fun recordHabitProgress(
        habitId: Long,
        date: String,
        value: Float,
        notes: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val existingLog = habitLogDao.getLogForHabitAndDate(habitId, date)
        val habit = habitDao.getHabitById(habitId)
        val target = habit?.targetValue ?: 1f
        val wasCompleted = existingLog != null && existingLog.value >= target
        val isNowCompleted = value >= target
        var earnedXp = 0

        if (value <= 0f) {
            habitLogDao.deleteLog(habitId, date)
            if (wasCompleted) {
                deductXpForCompletion(habit, existingLog?.value ?: target)
            }
        } else {
            val log = existingLog?.copy(value = value, notes = notes, timestamp = System.currentTimeMillis())
                ?: HabitLog(habitId = habitId, date = date, value = value, notes = notes)
            habitLogDao.insertOrUpdateLog(log)

            if (!wasCompleted && isNowCompleted) {
                earnedXp = awardXpForCompletion(habit, value)
            } else if (wasCompleted && !isNowCompleted) {
                deductXpForCompletion(habit, existingLog?.value ?: target)
            }
        }
        if (isNowCompleted && date == DateUtils.getTodayDateString()) {
            NotificationHelper.dismissActiveReminder(context, habitId)
        }
        earnedXp
    }

    suspend fun toggleHabitCompletion(habitId: Long, date: String): Int = withContext(Dispatchers.IO) {
        val existingLog = habitLogDao.getLogForHabitAndDate(habitId, date)
        val habit = habitDao.getHabitById(habitId) ?: return@withContext 0

        val earnedXp = if (existingLog != null && existingLog.value >= habit.targetValue) {
            habitLogDao.deleteLog(habitId, date)
            deductXpForCompletion(habit, existingLog.value)
            0
        } else {
            val log = HabitLog(
                habitId = habitId,
                date = date,
                value = habit.targetValue,
                timestamp = System.currentTimeMillis()
            )
            habitLogDao.insertOrUpdateLog(log)
            if (date == DateUtils.getTodayDateString()) {
                NotificationHelper.dismissActiveReminder(context, habitId)
            }
            awardXpForCompletion(habit, habit.targetValue)
        }
        earnedXp
    }

    suspend fun toggleSubTask(subTaskId: Long, isCompleted: Boolean): Int = withContext(Dispatchers.IO) {
        subTaskDao.setSubTaskCompleted(subTaskId, isCompleted)
        if (isCompleted) {
            awardXpForSubTask()
        } else {
            deductXpForSubTask()
            0
        }
    }

    private suspend fun awardXpForSubTask(): Int {
        val currentStats = userStatsDao.getUserStats() ?: UserStats()
        val bonus = if (currentStats.isHardcoreMode) (GamificationConfig.XP_SUBTASK_COMPLETION * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt() else GamificationConfig.XP_SUBTASK_COMPLETION
        val newXp = currentStats.xp + bonus
        val newLevel = GamificationConfig.calculateLevel(newXp)
        checkAndSaveGamification(currentStats.copy(xp = newXp, level = newLevel))
        return bonus
    }

    private suspend fun deductXpForSubTask() {
        val currentStats = userStatsDao.getUserStats() ?: UserStats()
        val penalty = if (currentStats.isHardcoreMode) (GamificationConfig.XP_SUBTASK_COMPLETION * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt() else GamificationConfig.XP_SUBTASK_COMPLETION
        val newXp = maxOf(0, currentStats.xp - penalty)
        val newLevel = GamificationConfig.calculateLevel(newXp)
        checkAndSaveGamification(currentStats.copy(xp = newXp, level = newLevel))
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

    private suspend fun awardXpForCompletion(habit: Habit?, completedValue: Float): Int {
        val currentStats = userStatsDao.getUserStats() ?: UserStats()
        var baseHabitXp = GamificationConfig.XP_HABIT_COMPLETION
        if (habit != null && completedValue > habit.targetValue) {
            baseHabitXp += GamificationConfig.XP_OVERACHIEVEMENT_BONUS
        }
        val earnedXp = if (currentStats.isHardcoreMode) {
            (baseHabitXp * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt()
        } else {
            baseHabitXp
        }

        val newXp = currentStats.xp + earnedXp
        val newLevel = GamificationConfig.calculateLevel(newXp)
        val newTotalCheckIns = currentStats.totalCheckIns + 1

        // Calculate best streak from all logs
        val allLogs = habitLogDao.getAllLogs().first()
        val datesWithCompletion = allLogs.filter { it.value > 0f }.map { it.date }.toSet()
        val (_, bestStreak) = DateUtils.calculateStreak(datesWithCompletion)
        val allTimeBest = maxOf(currentStats.bestStreakAllTime, bestStreak)

        val updated = currentStats.copy(
            xp = newXp,
            level = newLevel,
            totalCheckIns = newTotalCheckIns,
            bestStreakAllTime = allTimeBest,
            lastActiveDate = DateUtils.getTodayDateString()
        )
        checkAndSaveGamification(updated)
        return earnedXp
    }

    private suspend fun deductXpForCompletion(habit: Habit?, previousValue: Float) {
        val currentStats = userStatsDao.getUserStats() ?: UserStats()
        var baseHabitXp = GamificationConfig.XP_HABIT_COMPLETION
        if (habit != null && previousValue > habit.targetValue) {
            baseHabitXp += GamificationConfig.XP_OVERACHIEVEMENT_BONUS
        }
        val lostXp = if (currentStats.isHardcoreMode) {
            (baseHabitXp * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt()
        } else {
            baseHabitXp
        }

        val newXp = maxOf(0, currentStats.xp - lostXp)
        val newLevel = GamificationConfig.calculateLevel(newXp)
        val newTotalCheckIns = maxOf(0, currentStats.totalCheckIns - 1)

        val updated = currentStats.copy(
            xp = newXp,
            level = newLevel,
            totalCheckIns = newTotalCheckIns
        )
        userStatsDao.insertOrUpdate(updated)
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
        val (currentStreak, _) = DateUtils.calculateStreak(completedDates)

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
            val (curStreak, bestStreak) = DateUtils.calculateStreak(completedDates)
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

        val systemPrompt = "Eres un coach experto en creación de hábitos, productividad y psicología del comportamiento. Analiza los datos reales del usuario en HabitFlow y genera exactamente de 3 a 4 insights concisos, accionables y motivadores en español. Cada insight debe comenzar con un emoji descriptivo (📊, 🏆, 💡, 🔥, ⚡, etc.) y tener 1 o 2 oraciones máximo. Sé honesto, empático y directo sin inventar estadísticas o números que no estén en los datos."

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
                    .map { it.trim() }
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
                "💡 Comienza completando tus primeros hábitos diarios para desbloquear el análisis inteligente de correlaciones.",
                "⚡ Consejo Pro: Agrupa hábitos matutinos como hidratación y estiramientos (técnica Habit Stacking) para duplicar tu consistencia."
            )
        }

        val insights = mutableListOf<String>()
        val completedLogs = logs.filter { it.value > 0f }
        insights.add("📊 Has registrado ${completedLogs.size} check-ins reales en total con una tendencia de progreso constante.")

        val completionsByHabit = completedLogs.groupBy { it.habitId }
        val topHabitEntry = completionsByHabit.maxByOrNull { it.value.size }
        if (topHabitEntry != null) {
            val topHabit = habits.find { it.id == topHabitEntry.key }
            if (topHabit != null) {
                insights.add("🏆 Tu hábito ancla es '${topHabit.title}' con ${topHabitEntry.value.size} completados reales. ¡Úsalo como detonante para hábitos más difíciles!")
            }
        }

        val topCategory = habits.groupBy { it.category }.maxByOrNull { it.value.size }
        if (topCategory != null && topCategory.value.size > 1) {
            insights.add("🎯 Tu categoría con mayor enfoque es '${topCategory.key}' con ${topCategory.value.size} hábitos activos configurados.")
        }

        if (stats.bestStreakAllTime > 0) {
            insights.add("🔥 Récord de consistencia: Tu mejor racha histórica es de ${stats.bestStreakAllTime} días seguidos. ¡Mantén el ritmo hoy para superarla!")
        } else {
            insights.add("🧠 Recomendación de Ritmo: Mantén tu racha activa hoy para asegurar la bonificación de XP y no romper tu cadena de progreso.")
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

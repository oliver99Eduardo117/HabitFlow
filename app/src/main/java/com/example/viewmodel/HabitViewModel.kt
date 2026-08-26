package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.model.*
import com.example.notification.NotificationHelper
import com.example.repository.HabitRepository
import com.example.service.TimerManager
import com.example.util.DateUtils
import com.example.util.ThemePreferences
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class NavigationTab {
    TODAY,
    CALENDAR,
    TIMER,
    PROGRESS,
    SETTINGS
}

enum class ProgressTab {
    ANALYTICS,
    HEATMAP,
    ACHIEVEMENTS
}

data class ActiveTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isPomodoro: Boolean = true,
    val habitId: Long? = null,
    val habitTitle: String = "",
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val elapsedSeconds: Int = 0
)

data class HabitUiState(
    val selectedDate: String = DateUtils.getTodayDateString(),
    val habits: List<HabitWithStats> = emptyList(),
    val archivedHabits: List<Habit> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val layoutMode: ViewLayoutMode = ViewLayoutMode.LIST,
    val activeTab: NavigationTab = NavigationTab.TODAY,
    val activeProgressTab: ProgressTab = ProgressTab.ANALYTICS,
    val userStats: UserStats = UserStats(),
    val allLogs: List<HabitLog> = emptyList(),
    val activeTimer: ActiveTimerState = ActiveTimerState(),
    val insights: List<String> = emptyList(),
    val isLoadingInsights: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)

class HabitViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HabitRepository
    private val themePrefs = ThemePreferences.getInstance(application)

    private val _uiState = MutableStateFlow(
        HabitUiState(
            layoutMode = themePrefs.layoutMode.value,
            themeMode = themePrefs.themeMode.value,
            dynamicColor = themePrefs.dynamicColor.value
        )
    )
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()

    private val _xpGainedEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val xpGainedEvent: SharedFlow<Int> = _xpGainedEvent.asSharedFlow()

    private val _streakMilestoneEvent = MutableSharedFlow<StreakMilestoneEvent>(extraBufferCapacity = 1)
    val streakMilestoneEvent: SharedFlow<StreakMilestoneEvent> = _streakMilestoneEvent.asSharedFlow()

    val themeMode: StateFlow<ThemeMode> = themePrefs.themeMode
    val dynamicColor: StateFlow<Boolean> = themePrefs.dynamicColor

    init {
        val db = AppDatabase.getInstance(application)
        repository = HabitRepository(db, application)

        // Observe Layout Mode
        viewModelScope.launch {
            themePrefs.layoutMode.collect { mode ->
                _uiState.update { it.copy(layoutMode = mode) }
            }
        }

        // Observe Theme Mode
        viewModelScope.launch {
            themePrefs.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }

        // Observe Dynamic Color
        viewModelScope.launch {
            themePrefs.dynamicColor.collect { dyn ->
                _uiState.update { it.copy(dynamicColor = dyn) }
            }
        }

        // Populate sample habits if empty on first run
        viewModelScope.launch {
            repository.activeHabits.first().let { currentList ->
                if (currentList.isEmpty()) {
                    preloadDefaultHabits()
                }
            }
        }

        // Observe Habits with Stats for selected date
        viewModelScope.launch {
            _uiState.map { it.selectedDate }.distinctUntilChanged().flatMapLatest { date ->
                repository.getHabitsWithStats(date)
            }.collect { habitsWithStats ->
                _uiState.update { it.copy(habits = habitsWithStats) }
            }
        }

        // Observe Categories
        viewModelScope.launch {
            repository.allCategories.collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }

        // Observe Archived Habits
        viewModelScope.launch {
            repository.archivedHabits.collect { archived ->
                _uiState.update { it.copy(archivedHabits = archived) }
            }
        }

        // Observe User Stats
        viewModelScope.launch {
            repository.userStats.collect { stats ->
                if (stats != null) {
                    _uiState.update { it.copy(userStats = stats) }
                }
            }
        }

        // Observe All Logs for Heatmap & Analytics
        viewModelScope.launch {
            repository.allLogs.collect { logs ->
                _uiState.update { it.copy(allLogs = logs) }
            }
        }

        // Observe Active Timer State from TimerManager (runs in background service & across lockscreen)
        viewModelScope.launch {
            TimerManager.timerState.collect { timerState ->
                _uiState.update { it.copy(activeTimer = timerState) }
            }
        }

        // Observe Timer Completion Event
        viewModelScope.launch {
            TimerManager.timerFinishedEvent.collect { message ->
                _uiState.update { it.copy(snackbarMessage = message) }
                triggerHaptic(pattern = longArrayOf(0, 300, 200, 500))
            }
        }

        // Load AI Insights
        refreshInsights()
    }

    private suspend fun preloadDefaultHabits() {
        val defaultHabits = listOf(
            Habit(
                title = "Rutina Matutina & Enfoque",
                description = "Agua, estiramientos y planificación del día",
                category = "Rutina Personal",
                colorHex = "#F59E0B",
                iconName = "sun",
                frequencyDays = listOf(1, 2, 3, 4, 5, 6, 7),
                orderIndex = 0
            ),
            Habit(
                title = "Bloque de Trabajo Profundo",
                description = "90 min de concentración absoluta sin distracciones",
                category = "Productividad",
                colorHex = "#6366F1",
                iconName = "timer",
                unit = "min",
                targetValue = 90f,
                hasTimer = true,
                timerDurationMinutes = 45,
                reminderTime = "09:00",
                orderIndex = 1
            ),
            Habit(
                title = "Entrenamiento & Fitness",
                description = "Ejercicio de fuerza o cardio para energía vital",
                category = "Fitness & Salud",
                colorHex = "#10B981",
                iconName = "fitness",
                unit = "min",
                targetValue = 45f,
                hasTimer = true,
                timerDurationMinutes = 45,
                reminderTime = "18:30",
                orderIndex = 2
            ),
            Habit(
                title = "Meditación & Calma",
                description = "15 min de respiración consciente y presencia",
                category = "Mente & Zen",
                colorHex = "#06B6D4",
                iconName = "meditation",
                unit = "min",
                targetValue = 15f,
                hasTimer = true,
                timerDurationMinutes = 15,
                orderIndex = 3
            ),
            Habit(
                title = "Lectura & Aprendizaje",
                description = "Leer al menos 15 páginas de desarrollo personal o código",
                category = "Estudio & Dev",
                colorHex = "#8B5CF6",
                iconName = "book",
                unit = "páginas",
                targetValue = 15f,
                orderIndex = 4
            )
        )

        defaultHabits.forEach { habit ->
            val id = repository.saveHabit(habit)
            if (habit.title.contains("Rutina Matutina")) {
                repository.addSubTask(id, "Vaso de agua grande")
                repository.addSubTask(id, "5 min de estiramientos")
                repository.addSubTask(id, "Definir 3 prioridades del día")
            }
        }
    }

    fun setSelectedDate(date: String) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun setNavigationTab(tab: NavigationTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == NavigationTab.PROGRESS && _uiState.value.activeProgressTab == ProgressTab.ANALYTICS) {
            refreshInsights()
        }
    }

    fun setProgressTab(tab: ProgressTab) {
        _uiState.update { it.copy(activeProgressTab = tab) }
        if (tab == ProgressTab.ANALYTICS) {
            refreshInsights()
        }
    }

    fun handleWidgetDeepLink(tabName: String?) {
        when (tabName?.uppercase()) {
            "ANALYTICS" -> {
                setProgressTab(ProgressTab.ANALYTICS)
                setNavigationTab(NavigationTab.PROGRESS)
            }
            "HEATMAP" -> {
                setProgressTab(ProgressTab.HEATMAP)
                setNavigationTab(NavigationTab.PROGRESS)
            }
            "GAMIFICATION" -> {
                setProgressTab(ProgressTab.ACHIEVEMENTS)
                setNavigationTab(NavigationTab.PROGRESS)
            }
            "CALENDAR" -> setNavigationTab(NavigationTab.CALENDAR)
            "TIMER" -> setNavigationTab(NavigationTab.TIMER)
            "TODAY" -> setNavigationTab(NavigationTab.TODAY)
            "SETTINGS" -> setNavigationTab(NavigationTab.SETTINGS)
            else -> { /* keep default */ }
        }
    }

    fun setLayoutMode(mode: ViewLayoutMode) {
        themePrefs.setLayoutMode(mode)
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun setSelectedCategory(categoryName: String?) {
        _uiState.update { it.copy(selectedCategory = categoryName) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleHabitCompletion(habitId: Long, date: String = _uiState.value.selectedDate) {
        viewModelScope.launch {
            val gainedXp = repository.toggleHabitCompletion(habitId, date)
            WidgetUpdater.scheduleRefresh(getApplication())
            val milestone = if (gainedXp > 0) repository.checkStreakMilestone(habitId) else null
            val message = if (gainedXp > 0) "¡Hábito completado! +$gainedXp XP 🔥" else "Hábito desmarcado"
            _uiState.update { it.copy(snackbarMessage = message) }
            if (gainedXp > 0) {
                _xpGainedEvent.tryEmit(gainedXp)
            }
            if (milestone != null) {
                _streakMilestoneEvent.tryEmit(milestone)
                _xpGainedEvent.tryEmit(milestone.xpBonus)
            }
            triggerHaptic()
        }
    }

    fun recordQuantitativeProgress(habitId: Long, value: Float, notes: String = "") {
        viewModelScope.launch {
            val gainedXp = repository.recordHabitProgress(habitId, _uiState.value.selectedDate, value, notes)
            val milestone = if (gainedXp > 0) repository.checkStreakMilestone(habitId) else null
            val bonusText = if (gainedXp > 0) " +$gainedXp XP" else ""
            _uiState.update { it.copy(snackbarMessage = "Progreso registrado$bonusText") }
            if (gainedXp > 0) {
                _xpGainedEvent.tryEmit(gainedXp)
            }
            if (milestone != null) {
                _streakMilestoneEvent.tryEmit(milestone)
                _xpGainedEvent.tryEmit(milestone.xpBonus)
            }
            triggerHaptic()
        }
    }

    fun toggleSubTask(subTaskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            val gainedXp = repository.toggleSubTask(subTaskId, isCompleted)
            if (gainedXp > 0) {
                _xpGainedEvent.tryEmit(gainedXp)
            }
        }
    }

    fun emitXpGained(amount: Int) {
        if (amount > 0) {
            _xpGainedEvent.tryEmit(amount)
        }
    }

    fun saveHabit(habit: Habit, subTasks: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.saveHabit(habit, subTasks)
            _uiState.update { it.copy(snackbarMessage = "Hábito guardado correctamente") }
        }
    }

    fun applyTemplate(template: HabitTemplate) {
        viewModelScope.launch {
            val newHabit = Habit(
                title = template.title,
                description = template.description,
                category = template.category,
                colorHex = template.colorHex,
                iconName = template.iconName,
                unit = template.unit,
                targetValue = template.targetValue,
                hasTimer = template.hasTimer,
                timerDurationMinutes = template.timerDurationMinutes
            )
            repository.saveHabit(newHabit, template.subTasks)
            _uiState.update { it.copy(snackbarMessage = "Plantilla '${template.title}' agregada 🎯") }
        }
    }

    fun setArchived(habitId: Long, isArchived: Boolean) {
        viewModelScope.launch {
            repository.setArchived(habitId, isArchived)
            val msg = if (isArchived) "Hábito archivado" else "Hábito desarchivado"
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun deleteHabit(habitId: Long) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
            _uiState.update { it.copy(snackbarMessage = "Hábito eliminado") }
        }
    }

    fun addCategory(category: Category) {
        viewModelScope.launch {
            repository.addCategory(category)
            _uiState.update { it.copy(snackbarMessage = "Categoría '${category.name}' creada exitosamente") }
        }
    }

    fun updateCategory(oldName: String, updatedCategory: Category) {
        viewModelScope.launch {
            repository.updateCategory(oldName, updatedCategory)
            _uiState.update { current ->
                val newSelectedCategory = if (current.selectedCategory == oldName) updatedCategory.name else current.selectedCategory
                current.copy(
                    selectedCategory = newSelectedCategory,
                    snackbarMessage = "Categoría '${updatedCategory.name}' actualizada"
                )
            }
        }
    }

    fun deleteCategory(categoryName: String, fallbackCategory: String = "Rutina Personal") {
        viewModelScope.launch {
            repository.deleteCategory(categoryName, fallbackCategory)
            _uiState.update { current ->
                val newSelectedCategory = if (current.selectedCategory == categoryName) null else current.selectedCategory
                current.copy(
                    selectedCategory = newSelectedCategory,
                    snackbarMessage = "Categoría '$categoryName' eliminada"
                )
            }
        }
    }

    fun toggleHardcoreMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHardcoreMode(enabled)
            val msg = if (enabled) "🔥 Modo Hardcore activado: ¡Cero tolerancia a fallar!" else "Modo estándar restaurado"
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun refreshInsights() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInsights = true) }
            try {
                val smartInsights = repository.generateSmartInsights()
                _uiState.update { it.copy(insights = smartInsights) }
            } finally {
                _uiState.update { it.copy(isLoadingInsights = false) }
            }
        }
    }

    // TIMER & STOPWATCH CONTROLS (Powered by Background Foreground Service & SystemClock Realtime)
    fun startTimerForHabit(habit: Habit, isPomodoro: Boolean = true, durationMinutes: Int = habit.timerDurationMinutes, switchTab: Boolean = true) {
        if (switchTab) {
            _uiState.update { it.copy(activeTab = NavigationTab.TIMER) }
        }
        TimerManager.startTimer(getApplication(), habit, isPomodoro, durationMinutes)
    }

    fun configureTimer(habit: Habit?, isPomodoro: Boolean, durationMinutes: Int) {
        TimerManager.configureTimer(habit, isPomodoro, durationMinutes)
    }

    fun toggleTimerPlayPause() {
        TimerManager.togglePlayPause(getApplication())
    }

    fun resetTimer() {
        TimerManager.resetTimer(getApplication())
    }

    fun completeTimerEarly() {
        val current = _uiState.value.activeTimer
        val loggedMinutes = maxOf(1, (if (current.isPomodoro) (current.totalSeconds - current.remainingSeconds) else current.elapsedSeconds) / 60)
        TimerManager.stopAndSave(getApplication(), loggedMinutes)
        _uiState.update {
            it.copy(snackbarMessage = "🎉 ¡Sesión de $loggedMinutes min guardada! +${loggedMinutes * 2} XP")
        }
        triggerHaptic()
    }

    fun testHabitReminder(habit: Habit) {
        NotificationHelper.showTestReminderNotification(getApplication<Application>(), habit)
        _uiState.update { it.copy(snackbarMessage = "Notificación de prueba enviada para: ${habit.title}") }
    }

    fun rescheduleAllReminders() {
        NotificationHelper.rescheduleAllReminders(getApplication<Application>())
        _uiState.update { it.copy(snackbarMessage = "Recordatorios sincronizados correctamente") }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    suspend fun getExportJson(): String = repository.exportDataJson()
    suspend fun getExportCsv(): String = repository.exportDataCsv()

    fun parseBackupPreview(jsonString: String): Result<com.example.repository.RestoreSummary> {
        return repository.parseBackupPreview(jsonString)
    }

    fun restoreDatabaseFromJson(
        jsonString: String,
        onComplete: (Result<com.example.repository.RestoreSummary>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.restoreDataJson(jsonString)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess { summary ->
                _uiState.update {
                    it.copy(snackbarMessage = "✅ Base de datos restaurada: ${summary.habitsCount} hábitos y ${summary.logsCount} check-ins cargados con éxito")
                }
                triggerHaptic(longArrayOf(0, 40, 60, 40))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(snackbarMessage = "❌ Error al restaurar: ${error.localizedMessage ?: "Formato JSON inválido"}")
                }
            }
            onComplete(result)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themePrefs.setThemeMode(mode)
        val msg = when (mode) {
            ThemeMode.LIGHT -> "☀️ Modo Claro activado"
            ThemeMode.DARK -> "🌙 Modo Oscuro activado"
            ThemeMode.SYSTEM -> "⚙️ Modo Sistema activado"
        }
        _uiState.update { it.copy(snackbarMessage = msg) }
        triggerHaptic()
    }

    fun toggleTheme(isSystemInDark: Boolean) {
        val current = _uiState.value.themeMode
        val nextMode = when (current) {
            ThemeMode.SYSTEM -> if (isSystemInDark) ThemeMode.LIGHT else ThemeMode.DARK
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
        }
        setThemeMode(nextMode)
    }

    fun setDynamicColor(enabled: Boolean) {
        themePrefs.setDynamicColor(enabled)
        val msg = if (enabled) "🎨 Colores Dinámicos activados" else "Paleta estándar restaurada"
        _uiState.update { it.copy(snackbarMessage = msg) }
        triggerHaptic()
    }

    private fun triggerHaptic(pattern: LongArray = longArrayOf(0, 50)) {
        try {
            val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }
}

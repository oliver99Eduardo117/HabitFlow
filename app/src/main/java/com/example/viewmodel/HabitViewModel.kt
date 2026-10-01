package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.model.*
import com.example.notification.NotificationHelper
import com.example.repository.HabitRepository
import com.example.service.TimerManager
import com.example.util.AiProviderPreferences
import com.example.util.BackupFileSharer
import com.example.util.BackupPreferences
import com.example.util.BackupStatus
import com.example.util.BackupStatusCalculator
import com.example.util.CalendarMonthCalculator
import com.example.util.CalendarMonthSummary
import com.example.util.DateUtils
import com.example.util.ProgressCalculator
import com.example.util.ProgressSummary
import com.example.util.ThemePreferences
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

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
    val today: String = DateUtils.getTodayDateString(),
    val habits: List<HabitWithStats> = emptyList(),
    val archivedHabits: List<Habit> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val layoutMode: ViewLayoutMode = ViewLayoutMode.LIST,
    val activeTab: NavigationTab = NavigationTab.TODAY,
    val activeProgressTab: ProgressTab = ProgressTab.ANALYTICS,
    /** Habito elegido en Progreso > Constancia (null = Todos). */
    val constancyHabitId: Long? = null,
    val userStats: UserStats = UserStats(),
    val allLogs: List<HabitLog> = emptyList(),
    val activeTimer: ActiveTimerState = ActiveTimerState(),
    val habitFocusId: Long? = null,
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

    private var lastStandaloneIsPomodoro = true
    private var lastStandaloneMinutes = 25

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

    /** Entradas del calendario; se comparan para no recalcular en cada tic del temporizador. */
    private data class CalendarInputs(
        val habits: List<Habit>,
        val logs: List<HabitLog>,
        val month: YearMonth,
        val today: LocalDate
    )

    /** Resumen del mes que muestra la pestana Calendario: siempre el mes de la fecha seleccionada. */
    val calendarMonth: StateFlow<CalendarMonthSummary?> = _uiState
        .map { state ->
            val today = parseIsoDateOr(state.today, LocalDate.now())
            CalendarInputs(
                habits = state.habits.map { it.habit },
                logs = state.allLogs,
                month = YearMonth.from(parseIsoDateOr(state.selectedDate, today)),
                today = today
            )
        }
        .distinctUntilChanged()
        .map { CalendarMonthCalculator.build(it.habits, it.logs, it.month, it.today) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Entradas de Progreso; se comparan para no recalcular en cada tic del temporizador. */
    private data class ProgressInputs(
        val habits: List<Habit>,
        val archived: List<Habit>,
        val logs: List<HabitLog>,
        val stats: UserStats,
        val today: LocalDate
    )

    /** Todo lo que muestra la pestana Progreso (Resumen, Constancia y Logros), calculado fuera del hilo principal. */
    val progress: StateFlow<ProgressSummary?> = _uiState
        .map { state ->
            ProgressInputs(
                habits = state.habits.map { it.habit },
                archived = state.archivedHabits,
                logs = state.allLogs,
                stats = state.userStats,
                today = parseIsoDateOr(state.today, LocalDate.now())
            )
        }
        .distinctUntilChanged()
        .map { ProgressCalculator.build(it.habits, it.archived, it.logs, it.stats, it.today) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val aiPrefs = AiProviderPreferences.getInstance(application)

    /** true si hay una IA configurada. Sin ella, Resumen ofrece abrir su configuracion. */
    val aiConfigured: StateFlow<Boolean> = combine(aiPrefs.isEnabled, aiPrefs.baseUrl, aiPrefs.modelName) { enabled, url, model ->
        enabled && url.isNotBlank() && model.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val backupPrefs = BackupPreferences.getInstance(application)

    /** Entradas del estado de la copia; se comparan para no recalcular en cada tic del temporizador. */
    private data class BackupInputs(val today: LocalDate, val hasProgress: Boolean)

    /** Estado de la copia de seguridad para Ajustes: color, titular y textos ya resueltos. */
    val backupStatus: StateFlow<BackupStatus> = combine(
        backupPrefs.lastBackupAt,
        _uiState
            .map { state -> BackupInputs(parseIsoDateOr(state.today, LocalDate.now()), state.allLogs.isNotEmpty()) }
            .distinctUntilChanged()
    ) { lastBackupAt, inputs ->
        BackupStatusCalculator.compute(lastBackupAt, inputs.hasProgress, inputs.today)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BackupStatusCalculator.compute(backupPrefs.lastBackupAt.value, false, LocalDate.now())
    )

    private val _backupBusy = MutableStateFlow(false)

    /** true mientras se guarda o se exporta un archivo; Ajustes muestra un indicador y bloquea los botones. */
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _safetySnapshotAt = MutableStateFlow<Long?>(null)

    /** Hora de la copia interna guardada antes de la ultima recuperacion; null si no hay nada que deshacer. */
    val safetySnapshotAt: StateFlow<Long?> = _safetySnapshotAt.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repository = HabitRepository(db, application)

        // Copia interna previa a la ultima recuperacion: habilita "Deshacer" en Copias de seguridad
        viewModelScope.launch {
            _safetySnapshotAt.value = repository.safetySnapshotTime()
        }

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

        // Al guardar o terminar una sesión de hábito abierta en pantalla completa, volver a Hoy
        viewModelScope.launch {
            TimerManager.sessionEnded.collect { endedHabitId ->
                if (endedHabitId == _uiState.value.habitFocusId) {
                    _uiState.update { it.copy(habitFocusId = null, activeTab = NavigationTab.TODAY) }
                    restoreStandaloneIfIdle()
                }
            }
        }

        // Cambio de dia con la app en primer plano: esperar a la proxima medianoche y refrescar
        viewModelScope.launch {
            while (true) {
                val now = java.time.LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val waitMs = java.time.Duration.between(now, nextMidnight).toMillis() + 1_000L
                kotlinx.coroutines.delay(waitMs)
                refreshDayIfChanged()
            }
        }
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

    /**
     * Cambia el mes del calendario. Selecciona hoy si el mes nuevo lo contiene;
     * si no, el dia 1 de ese mes.
     */
    fun shiftCalendarMonth(deltaMonths: Int) {
        val state = _uiState.value
        val today = parseIsoDateOr(state.today, LocalDate.now())
        val current = parseIsoDateOr(state.selectedDate, today)
        val target = YearMonth.from(current).plusMonths(deltaMonths.toLong())
        val newDate = if (YearMonth.from(today) == target) today else target.atDay(1)
        setSelectedDate(newDate.toString())
    }

    /** Los dias futuros se pueden consultar pero no marcar. Fechas ISO: se comparan como texto. */
    private fun isFutureDate(date: String): Boolean = date > _uiState.value.today

    /**
     * Si el dia calendario cambio desde la ultima lectura, actualiza `today`
     * y regresa la fecha seleccionada al dia nuevo para no escribir en un dia viejo.
     */
    fun refreshDayIfChanged() {
        val now = DateUtils.getTodayDateString()
        if (now != _uiState.value.today) {
            _uiState.update { it.copy(today = now, selectedDate = now) }
        }
    }

    fun setNavigationTab(tab: NavigationTab) {
        if (tab == NavigationTab.TIMER) restoreStandaloneIfIdle()
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setProgressTab(tab: ProgressTab) {
        _uiState.update { it.copy(activeProgressTab = tab) }
    }

    /** Habito elegido en Progreso > Constancia (null = Todos). */
    fun setConstancyHabit(habitId: Long?) {
        _uiState.update { it.copy(constancyHabitId = habitId) }
    }

    /** Abre Progreso > Constancia con un habito ya elegido. Lo usa Resumen al tocar un habito. */
    fun openConstancy(habitId: Long?) {
        _uiState.update { it.copy(constancyHabitId = habitId, activeProgressTab = ProgressTab.HEATMAP) }
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
        if (isFutureDate(date)) {
            _uiState.update { it.copy(snackbarMessage = FUTURE_DAY_MESSAGE) }
            return
        }
        viewModelScope.launch {
            if (!isDoneOn(habitId, date)) {
                repository.dependencyBlocker(habitId, date)?.let { title ->
                    _uiState.update { it.copy(snackbarMessage = "Completa primero '$title'") }
                    return@launch
                }
            }
            val gainedXp = repository.toggleHabitCompletion(habitId, date)
            WidgetUpdater.scheduleRefresh(getApplication())
            val milestone = if (gainedXp > 0) repository.checkStreakMilestone(habitId) else null
            // El XP ganado lo anuncia el snackbar de XP; aqui solo se avisa el desmarcado
            if (gainedXp == 0) {
                _uiState.update { it.copy(snackbarMessage = "Hábito desmarcado") }
            }
            if (milestone != null) {
                _streakMilestoneEvent.tryEmit(milestone)
            }
            val totalXp = gainedXp + (milestone?.xpBonus ?: 0)
            if (totalXp > 0) {
                _xpGainedEvent.tryEmit(totalXp)
            }
            triggerHaptic()
        }
    }

    fun recordQuantitativeProgress(habitId: Long, value: Float, notes: String = "") {
        if (isFutureDate(_uiState.value.selectedDate)) {
            _uiState.update { it.copy(snackbarMessage = FUTURE_DAY_MESSAGE) }
            return
        }
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val currentValue = _uiState.value.allLogs.firstOrNull { it.habitId == habitId && it.date == date }?.value ?: 0f
            if (value > currentValue) {
                repository.dependencyBlocker(habitId, date)?.let { title ->
                    _uiState.update { it.copy(snackbarMessage = "Completa primero '$title'") }
                    return@launch
                }
            }
            val gainedXp = repository.recordHabitProgress(habitId, date, value, notes)
            val milestone = if (gainedXp > 0) repository.checkStreakMilestone(habitId) else null
            if (gainedXp == 0) {
                _uiState.update { it.copy(snackbarMessage = "Progreso registrado") }
            }
            if (milestone != null) {
                _streakMilestoneEvent.tryEmit(milestone)
            }
            val totalXp = gainedXp + (milestone?.xpBonus ?: 0)
            if (totalXp > 0) {
                _xpGainedEvent.tryEmit(totalXp)
            }
            triggerHaptic()
        }
    }

    fun toggleSubTask(subTaskId: Long, isCompleted: Boolean) {
        if (isFutureDate(_uiState.value.selectedDate)) {
            _uiState.update { it.copy(snackbarMessage = FUTURE_DAY_MESSAGE) }
            return
        }
        viewModelScope.launch {
            val habitId = _uiState.value.habits
                .firstOrNull { hs -> hs.subTasks.any { it.id == subTaskId } }
                ?.habit?.id
            if (isCompleted && habitId != null) {
                repository.dependencyBlocker(habitId, _uiState.value.selectedDate)?.let { title ->
                    _uiState.update { it.copy(snackbarMessage = "Completa primero '$title'") }
                    return@launch
                }
            }
            val gainedXp = repository.toggleSubTask(subTaskId, isCompleted, _uiState.value.selectedDate)
            if (gainedXp > 0) {
                // Si la sub-rutina completo el habito, puede alcanzarse un hito de racha
                val milestone = habitId?.let { repository.checkStreakMilestone(it) }
                if (milestone != null) {
                    _streakMilestoneEvent.tryEmit(milestone)
                }
                _xpGainedEvent.tryEmit(gainedXp + (milestone?.xpBonus ?: 0))
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

    fun subTasksFor(habitId: Long): Flow<List<SubTask>> = repository.getSubTasksForHabit(habitId)

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
            _uiState.update { it.copy(snackbarMessage = "Plantilla '${template.title}' agregada") }
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
            val msg = if (enabled) "Modo difícil activado: ganas 25% más XP" else "Modo difícil desactivado"
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    /** Pide a la IA un resumen. Solo se llama desde el boton de Resumen; ya no se llama sola. */
    fun refreshInsights() {
        if (_uiState.value.isLoadingInsights) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInsights = true) }
            try {
                val aiInsights = repository.generateSmartInsights(fallbackToLocal = false)
                _uiState.update {
                    if (aiInsights.isEmpty()) {
                        it.copy(snackbarMessage = "La IA no respondió. Revisa su configuración e inténtalo de nuevo.")
                    } else {
                        it.copy(insights = aiInsights)
                    }
                }
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

    /**
     * Minutos con los que abre la pantalla de un hábito.
     * Unidad en minutos: lo que falta de la meta de HOY (no de la fecha seleccionada).
     * Otra unidad, o meta ya cumplida: la duración del bloque del hábito.
     */
    fun suggestedFocusMinutes(habit: Habit): Int {
        val block = habit.timerDurationMinutes.takeIf { it in 1..1440 } ?: 25
        val isMinuteUnit = habit.unit.trim().lowercase() in setOf("min", "mins", "minuto", "minutos")
        if (!isMinuteUnit) return block
        val today = DateUtils.getTodayDateString()
        val done = _uiState.value.allLogs
            .firstOrNull { it.habitId == habit.id && it.date == today }?.value ?: 0f
        val remaining = kotlin.math.ceil(habit.targetValue - done).toInt()
        return if (remaining >= 1) remaining.coerceAtMost(1440) else block
    }

    /** true si hay una sesión corriendo o en pausa que NO pertenece a este hábito. */
    fun hasOtherActiveSession(habitId: Long): Boolean {
        val t = TimerManager.timerState.value
        return (t.isRunning || t.isPaused) && t.habitId != habitId
    }

    /** Abre la pantalla completa del hábito. Nunca inicia el conteo. */
    fun openHabitFocus(habit: Habit, minutes: Int, isPomodoro: Boolean) {
        val t = TimerManager.timerState.value
        val sameHabitActive = t.habitId == habit.id && (t.isRunning || t.isPaused)
        if (!sameHabitActive) {
            if (t.isRunning || t.isPaused) {
                TimerManager.discardSession(getApplication())
            }
            TimerManager.configureTimer(habit, isPomodoro, minutes)
        }
        _uiState.update { it.copy(habitFocusId = habit.id) }
    }

    fun changeHabitFocusMode(habit: Habit, isPomodoro: Boolean) {
        TimerManager.configureTimer(habit, isPomodoro, suggestedFocusMinutes(habit))
    }

    /** Cierra la pantalla del hábito. Si la sesión corre, sigue en segundo plano. */
    fun closeHabitFocus() {
        _uiState.update { it.copy(habitFocusId = null, activeTab = NavigationTab.TODAY) }
        restoreStandaloneIfIdle()
    }

    fun configureStandaloneTimer(isPomodoro: Boolean, minutes: Int) {
        lastStandaloneIsPomodoro = isPomodoro
        lastStandaloneMinutes = minutes
        TimerManager.configureTimer(null, isPomodoro, minutes)
    }

    /** Inicia una sesión libre descartando sin guardar la sesión de hábito activa. */
    fun startStandaloneDiscardingForeign(isPomodoro: Boolean, minutes: Int) {
        lastStandaloneIsPomodoro = isPomodoro
        lastStandaloneMinutes = minutes
        TimerManager.startTimer(getApplication(), null, isPomodoro, minutes)
    }

    /** Si no hay sesión activa, deja el cronómetro con la última configuración de Enfoque. */
    private fun restoreStandaloneIfIdle() {
        val t = TimerManager.timerState.value
        if (!t.isRunning && !t.isPaused) {
            TimerManager.configureTimer(null, lastStandaloneIsPomodoro, lastStandaloneMinutes)
        }
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
            it.copy(snackbarMessage = "Sesión de $loggedMinutes min guardada")
        }
        triggerHaptic()
    }

    fun testHabitReminder(habit: Habit) {
        NotificationHelper.showTestReminderNotification(getApplication<Application>(), habit)
        _uiState.update { it.copy(snackbarMessage = "Notificación de prueba enviada para: ${habit.title}") }
    }

    fun rescheduleAllReminders() {
        NotificationHelper.rescheduleAllReminders(getApplication<Application>())
        _uiState.update { it.copy(snackbarMessage = "Recordatorios reparados") }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Muestra un aviso en la barra inferior. Ajustes lo usa para los resultados de copias y exportaciones. */
    fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    /** Anota que el usuario acaba de guardar o enviar una copia. */
    fun markBackupDone() {
        backupPrefs.markBackupDone(System.currentTimeMillis())
    }

    suspend fun getExportJson(): String = repository.exportDataJson()
    suspend fun getExportCsv(): String = repository.exportDataCsv()

    /**
     * Guarda una copia completa en el archivo que el usuario eligio con el selector de Android.
     * Corre en viewModelScope: si el usuario cambia de pestana, la escritura no se corta a la mitad.
     */
    fun saveBackupTo(uri: Uri) {
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val json = repository.exportDataJson()
                writeToUri(uri, json.toByteArray(Charsets.UTF_8))
                backupPrefs.markBackupDone(System.currentTimeMillis())
                showMessage("Copia guardada")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo guardar la copia")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /** Exporta los registros a CSV. El BOM inicial hace que Excel lea bien los acentos. */
    fun exportCsvTo(uri: Uri) {
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val csv = repository.exportDataCsv()
                val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                writeToUri(uri, bom + csv.toByteArray(Charsets.UTF_8))
                showMessage("Registros exportados")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudieron exportar los registros")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    private suspend fun writeToUri(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        stream.use { it.write(bytes) }
    }

    /** Lee el texto de un archivo elegido con el selector de Android; null si no se pudo leer. */
    suspend fun readTextFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Lo que hay ahora en el telefono, contado igual que una copia, para la tabla "Ahora -> Copia". */
    suspend fun getCurrentDataSummary(): com.example.repository.RestoreSummary = repository.currentDataSummary()

    /** Texto de la copia interna guardada antes de la ultima recuperacion; null si no hay. */
    suspend fun readSafetySnapshot(): String? = repository.readSafetySnapshot()

    /**
     * Prepara la copia como archivo para "Enviar a otra app" y devuelve el menu de compartir de Android.
     * Devuelve null si no se pudo preparar. Quien lo abre debe llamar a markBackupDone().
     */
    suspend fun prepareBackupShareIntent(): Intent? = try {
        val json = repository.exportDataJson()
        val app = getApplication<Application>()
        val file = withContext(Dispatchers.IO) { BackupFileSharer.writeShareFile(app, json) }
        BackupFileSharer.shareIntent(app, file)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun parseBackupPreview(jsonString: String): Result<com.example.repository.RestoreSummary> {
        return repository.parseBackupPreview(jsonString)
    }

    /**
     * Recupera una copia. Antes guarda lo que hay ahora en un archivo interno para poder deshacerlo;
     * si eso falla, no toca la base de datos.
     */
    fun restoreDatabaseFromJson(
        jsonString: String,
        onComplete: (Result<com.example.repository.RestoreSummary>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.restoreWithSafetySnapshot(jsonString)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess { summary ->
                _safetySnapshotAt.value = repository.safetySnapshotTime()
                val habits = if (summary.habitsCount == 1) "1 hábito" else "${summary.habitsCount} hábitos"
                val logs = if (summary.logsCount == 1) "1 registro" else "${summary.logsCount} registros"
                _uiState.update { it.copy(snackbarMessage = "Listo: recuperaste $habits y $logs") }
                triggerHaptic(longArrayOf(0, 40, 60, 40))
            }.onFailure { error ->
                // Solo se muestran los mensajes escritos para el usuario; los tecnicos se cambian por uno claro
                val known = error.message?.takeIf {
                    it == com.example.repository.NOT_A_BACKUP_MESSAGE || it.startsWith("No se pudo")
                }
                _uiState.update {
                    it.copy(snackbarMessage = known ?: "No se pudo recuperar la copia. No se cambió nada.")
                }
            }
            onComplete(result)
        }
    }

    /** El cambio de tema ya se ve en pantalla, asi que no lleva aviso; solo vibracion. */
    fun setThemeMode(mode: ThemeMode) {
        themePrefs.setThemeMode(mode)
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

    /** Igual que el tema: el cambio se ve al momento, sin aviso. */
    fun setDynamicColor(enabled: Boolean) {
        themePrefs.setDynamicColor(enabled)
        triggerHaptic()
    }

    /** true si el habito ya alcanzo su meta en la fecha, segun el estado en memoria. */
    private fun isDoneOn(habitId: Long, date: String): Boolean {
        val habit = _uiState.value.habits.firstOrNull { it.habit.id == habitId }?.habit ?: return false
        val log = _uiState.value.allLogs.firstOrNull { it.habitId == habitId && it.date == date } ?: return false
        return log.value >= habit.targetValue
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

private const val FUTURE_DAY_MESSAGE = "Ese día todavía no llega"

/** Lee una fecha "yyyy-MM-dd"; si no se puede leer, usa [fallback]. */
private fun parseIsoDateOr(value: String, fallback: LocalDate): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrDefault(fallback)

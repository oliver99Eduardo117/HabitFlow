package com.example.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.database.AppDatabase
import com.example.model.Habit
import com.example.repository.HabitRepository
import com.example.util.DateUtils
import com.example.viewmodel.ActiveTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object TimerManager {

    private val _timerState = MutableStateFlow(ActiveTimerState())
    val timerState: StateFlow<ActiveTimerState> = _timerState.asStateFlow()

    private val _timerFinishedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val timerFinishedEvent: SharedFlow<String> = _timerFinishedEvent.asSharedFlow()

    private var startTimeRealtime: Long = 0L
    private var accumulatedElapsedSeconds: Int = 0

    val currentSessionElapsed: Int
        get() {
            val state = _timerState.value
            return if (state.isRunning) {
                val now = SystemClock.elapsedRealtime()
                val delta = ((now - startTimeRealtime) / 1000).toInt()
                accumulatedElapsedSeconds + maxOf(0, delta)
            } else {
                accumulatedElapsedSeconds
            }
        }

    fun startTimer(
        context: Context,
        habit: Habit?,
        isPomodoro: Boolean,
        durationMinutes: Int
    ) {
        val effectiveMinutes = if (durationMinutes > 0) durationMinutes else 25
        val totalSecs = if (isPomodoro) effectiveMinutes * 60 else 0

        startTimeRealtime = SystemClock.elapsedRealtime()
        accumulatedElapsedSeconds = 0

        _timerState.value = ActiveTimerState(
            isRunning = true,
            isPaused = false,
            isPomodoro = isPomodoro,
            habitId = habit?.id,
            habitTitle = habit?.title ?: "",
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            elapsedSeconds = 0
        )

        val intent = Intent(context, HabitTimerService::class.java).apply {
            action = HabitTimerService.ACTION_START
            putExtra(HabitTimerService.EXTRA_HABIT_ID, habit?.id ?: -1L)
            putExtra(HabitTimerService.EXTRA_HABIT_TITLE, habit?.title ?: "")
            putExtra(HabitTimerService.EXTRA_IS_POMODORO, isPomodoro)
            putExtra(HabitTimerService.EXTRA_DURATION_MINUTES, effectiveMinutes)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun configureTimer(habit: Habit?, isPomodoro: Boolean, durationMinutes: Int) {
        val totalSecs = if (isPomodoro) durationMinutes * 60 else 0
        accumulatedElapsedSeconds = 0
        startTimeRealtime = 0L

        _timerState.value = ActiveTimerState(
            isRunning = false,
            isPaused = false,
            isPomodoro = isPomodoro,
            habitId = habit?.id,
            habitTitle = habit?.title ?: "",
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            elapsedSeconds = 0
        )
    }

    fun togglePlayPause(context: Context) {
        val current = _timerState.value
        if (current.isRunning) {
            pauseTimer(context)
        } else {
            resumeTimer(context)
        }
    }

    fun pauseTimer(context: Context) {
        val current = _timerState.value
        if (!current.isRunning) return

        accumulatedElapsedSeconds = currentSessionElapsed
        startTimeRealtime = 0L

        val remaining = if (current.isPomodoro) {
            maxOf(0, current.totalSeconds - accumulatedElapsedSeconds)
        } else {
            0
        }

        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = true,
                elapsedSeconds = accumulatedElapsedSeconds,
                remainingSeconds = remaining
            )
        }

        val intent = Intent(context, HabitTimerService::class.java).apply {
            action = HabitTimerService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTimer(context: Context) {
        val current = _timerState.value
        if (current.isRunning) return

        startTimeRealtime = SystemClock.elapsedRealtime()

        _timerState.update {
            it.copy(
                isRunning = true,
                isPaused = false
            )
        }

        val intent = Intent(context, HabitTimerService::class.java).apply {
            action = HabitTimerService.ACTION_RESUME
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun resetTimer(context: Context) {
        val current = _timerState.value
        accumulatedElapsedSeconds = 0
        startTimeRealtime = 0L
        val resetSecs = if (current.isPomodoro) current.totalSeconds else 0

        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                remainingSeconds = resetSecs,
                elapsedSeconds = 0
            )
        }

        val intent = Intent(context, HabitTimerService::class.java).apply {
            action = HabitTimerService.ACTION_RESET
        }
        context.startService(intent)
    }

    fun stopAndSave(context: Context, customMinutes: Int? = null) {
        val current = _timerState.value
        val actualElapsed = currentSessionElapsed
        val loggedMinutes = customMinutes ?: maxOf(1, (if (current.isPomodoro) (current.totalSeconds - current.remainingSeconds) else actualElapsed) / 60)

        accumulatedElapsedSeconds = 0
        startTimeRealtime = 0L

        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                remainingSeconds = if (it.isPomodoro) it.totalSeconds else 0,
                elapsedSeconds = 0
            )
        }

        // Save progress to database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = HabitRepository(db, context)
                repo.addFocusSession(loggedMinutes)
                if (current.habitId != null && current.habitId > 0) {
                    repo.recordHabitProgress(
                        current.habitId,
                        DateUtils.getTodayDateString(),
                        loggedMinutes.toFloat(),
                        "Sesión de enfoque: $loggedMinutes min"
                    )
                }
            } catch (_: Exception) {
            }
        }

        val intent = Intent(context, HabitTimerService::class.java).apply {
            action = HabitTimerService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Called on each ticker cycle by the Foreground Service.
     * Returns true if a Pomodoro just finished.
     */
    fun tick(context: Context): Boolean {
        val current = _timerState.value
        if (!current.isRunning) return false

        val elapsed = currentSessionElapsed

        if (current.isPomodoro) {
            val remaining = maxOf(0, current.totalSeconds - elapsed)
            if (remaining <= 0) {
                // Timer finished!
                accumulatedElapsedSeconds = current.totalSeconds
                startTimeRealtime = 0L

                _timerState.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        remainingSeconds = 0,
                        elapsedSeconds = current.totalSeconds
                    )
                }

                val minutes = current.totalSeconds / 60
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val repo = HabitRepository(db, context)
                        repo.addFocusSession(minutes)
                        if (current.habitId != null && current.habitId > 0) {
                            repo.recordHabitProgress(
                                current.habitId,
                                DateUtils.getTodayDateString(),
                                minutes.toFloat(),
                                "Pomodoro completado ($minutes min)"
                            )
                        }
                    } catch (_: Exception) {
                    }
                }

                val msg = if (current.habitTitle.isNotEmpty()) {
                    "🏆 ¡Pomodoro completado para ${current.habitTitle}! Has ganado +${minutes * 3} XP"
                } else {
                    "🏆 ¡Pomodoro completado! Has ganado +${minutes * 3} XP"
                }
                _timerFinishedEvent.tryEmit(msg)
                return true
            } else {
                _timerState.update {
                    it.copy(
                        remainingSeconds = remaining,
                        elapsedSeconds = elapsed
                    )
                }
                return false
            }
        } else {
            // Stopwatch mode
            _timerState.update {
                it.copy(elapsedSeconds = elapsed)
            }
            return false
        }
    }
}

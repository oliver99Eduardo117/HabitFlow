package com.example.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.database.AppDatabase
import com.example.notification.NotificationHelper
import com.example.util.DateUtils
import com.example.widget.WidgetRepositoryProvider
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NotificationHelper.rescheduleAllRemindersSync(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }

            ACTION_COMPLETE_HABIT -> {
                handleCompleteHabit(context, intent)
                return
            }

            ACTION_SNOOZE_HABIT -> {
                handleSnoozeHabit(context, intent)
                return
            }

            ACTION_TRIGGER_REMINDER, null -> {
                handleTriggerReminder(context, intent)
            }
        }
    }

    private fun handleTriggerReminder(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
            val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
            var decisionCompleted = false
            try {
                val fallbackTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "Tus hábitos de hoy"
                val fallbackDesc = intent.getStringExtra(EXTRA_HABIT_DESC) ?: ""
                val fallbackCategory = intent.getStringExtra(EXTRA_HABIT_CATEGORY) ?: "General"
                val fallbackColor = intent.getStringExtra(EXTRA_HABIT_COLOR) ?: "#6366F1"
                val fallbackAdvanceMinutes = intent.getIntExtra(EXTRA_HABIT_ADVANCE, 0)
                val fallbackCustomMsg = intent.getStringExtra(EXTRA_HABIT_CUSTOM_MSG)
                val fallbackHasTimer = intent.getBooleanExtra(EXTRA_HABIT_HAS_TIMER, false)
                val fallbackTimerMins = intent.getIntExtra(EXTRA_HABIT_TIMER_MINS, 25)
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, habitId.toInt())

                val db = AppDatabase.getInstance(context)
                val habit = if (habitId > 0) db.habitDao().getHabitById(habitId) else null
                val today = DateUtils.getTodayDateString()
                val log = if (habitId > 0) db.habitLogDao().getLogForHabitAndDate(habitId, today) else null

                val calendar = Calendar.getInstance()
                val todayIso = NotificationHelper.getIsoDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))
                val frequencyDays = if (habit != null && habit.frequencyDays.isNotEmpty()) {
                    habit.frequencyDays
                } else {
                    listOf(1, 2, 3, 4, 5, 6, 7)
                }

                val shouldSuppress = habit == null ||
                        habit.isArchived ||
                        (log != null && log.value >= habit.targetValue) ||
                        (todayIso !in frequencyDays)

                if (!shouldSuppress && habit != null) {
                    NotificationHelper.createNotificationChannel(context)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    val habitTitle = habit.title.ifBlank { fallbackTitle }
                    val habitDesc = habit.description.ifBlank { fallbackDesc }
                    val habitCategory = habit.category.ifBlank { fallbackCategory }
                    val habitColor = habit.colorHex.ifBlank { fallbackColor }
                    val advanceMinutes = habit.reminderMinutesAdvance
                    val customMsg = habit.reminderCustomMessage ?: fallbackCustomMsg
                    val hasTimer = habit.hasTimer
                    val timerMins = habit.timerDurationMinutes

                    // Intent to open Main Activity
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_OPEN_HABIT_ID, habitId)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        notificationId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Action 1: "Completar"
                    val completeIntent = Intent(context, HabitReminderReceiver::class.java).apply {
                        this.action = ACTION_COMPLETE_HABIT
                        putExtra(EXTRA_HABIT_ID, habitId)
                        putExtra(EXTRA_HABIT_TITLE, habitTitle)
                        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    }
                    val completePendingIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId + 100,
                        completeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Action 2: "Posponer 15m"
                    val snoozeIntent = Intent(context, HabitReminderReceiver::class.java).apply {
                        this.action = ACTION_SNOOZE_HABIT
                        putExtra(EXTRA_HABIT_ID, habitId)
                        putExtra(EXTRA_HABIT_TITLE, habitTitle)
                        putExtra(EXTRA_HABIT_DESC, habitDesc)
                        putExtra(EXTRA_HABIT_CATEGORY, habitCategory)
                        putExtra(EXTRA_HABIT_COLOR, habitColor)
                        putExtra(EXTRA_HABIT_HAS_TIMER, hasTimer)
                        putExtra(EXTRA_HABIT_TIMER_MINS, timerMins)
                        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    }
                    val snoozePendingIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId + 200,
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val parsedColor = try {
                        AndroidColor.parseColor(habitColor)
                    } catch (_: Exception) {
                        AndroidColor.parseColor("#6366F1")
                    }

                    val contentMessage = when {
                        !customMsg.isNullOrBlank() -> customMsg
                        advanceMinutes > 0 && !isSnooze -> "Tu hábito comenzará en $advanceMinutes minutos. ¡Prepárate!"
                        isSnooze -> "Recordatorio pospuesto: Es momento de realizar tu hábito."
                        habitDesc.isNotBlank() -> habitDesc
                        else -> "Es momento de cumplir con tu hábito diario y mantener tu racha activa."
                    }

                    val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS_ID)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("Hora de $habitTitle")
                        .setContentText(contentMessage)
                        .setSubText(habitCategory)
                        .setColor(parsedColor)
                        .setColorized(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .addAction(
                            android.R.drawable.checkbox_on_background,
                            "Marcar Hecho",
                            completePendingIntent
                        )
                        .addAction(
                            android.R.drawable.ic_popup_sync,
                            "Posponer 15m",
                            snoozePendingIntent
                        )

                    // Action 3: Focus Timer / Pomodoro if enabled for this habit
                    if (hasTimer) {
                        val pomodoroIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(EXTRA_START_POMODORO_HABIT_ID, habitId)
                        }
                        val pomodoroPendingIntent = PendingIntent.getActivity(
                            context,
                            notificationId + 300,
                            pomodoroIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.addAction(
                            android.R.drawable.ic_media_play,
                            "Pomodoro (${timerMins}m)",
                            pomodoroPendingIntent
                        )
                    }

                    notificationManager.notify(notificationId, builder.build())
                }

                // CRÍTICO: la reprogramación de la siguiente ocurrencia debe ejecutarse SIEMPRE, se muestre o no la notificación.
                if (!isSnooze && habitId > 0 && habit != null && !habit.reminderTime.isNullOrBlank()) {
                    NotificationHelper.scheduleHabitReminder(context, habit)
                }

                decisionCompleted = true
            } catch (_: Exception) {
            } finally {
                if (!decisionCompleted && !isSnooze && habitId > 0) {
                    val habitTime = intent.getStringExtra(EXTRA_HABIT_TIME)
                    val advanceMinutes = intent.getIntExtra(EXTRA_HABIT_ADVANCE, 0)
                    val freqDaysArray = intent.getIntArrayExtra(EXTRA_HABIT_FREQ_DAYS)
                    val frequencyDays = freqDaysArray?.toList() ?: listOf(1, 2, 3, 4, 5, 6, 7)
                    if (!habitTime.isNullOrBlank()) {
                        NotificationHelper.scheduleReminderFromRaw(
                            context = context,
                            habitId = habitId,
                            reminderTime = habitTime,
                            advanceMinutes = advanceMinutes,
                            frequencyDays = frequencyDays,
                            sourceIntent = intent
                        )
                    }
                }
                pendingResult.finish()
            }
        }
    }

    private fun handleCompleteHabit(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
        val habitTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "Hábito"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, habitId.toInt())

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (habitId > 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = WidgetRepositoryProvider.getRepository(context)
                    val today = DateUtils.getTodayDateString()
                    val habit = repository.getHabitById(habitId)
                    val targetVal = habit?.targetValue ?: 1f
                    val existingLog = repository.getLogForHabitAndDate(habitId, today)

                    val isAlreadyCompleted = existingLog != null && existingLog.value >= targetVal
                    if (isAlreadyCompleted) {
                        notificationManager.cancel(notificationId)
                        return@launch
                    }

                    val gainedXp = repository.toggleHabitCompletion(habitId, today)
                    WidgetUpdater.scheduleRefresh(context)
                    if (gainedXp > 0) {
                        repository.checkStreakMilestone(habitId)
                    }

                    // Show success notification feedback with real gained XP
                    val successNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS_ID)
                        .setSmallIcon(android.R.drawable.checkbox_on_background)
                        .setContentTitle("¡$habitTitle completado!")
                        .setContentText("¡Excelente trabajo! Has sumado +$gainedXp XP a tu racha diaria.")
                        .setColor(AndroidColor.parseColor("#10B981"))
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .build()

                    notificationManager.notify(notificationId, successNotification)

                    // Dismiss the celebration notification after 3 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        notificationManager.cancel(notificationId)
                    }, 3000)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun handleSnoozeHabit(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
        val habitTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "Hábito"
        val habitDesc = intent.getStringExtra(EXTRA_HABIT_DESC) ?: ""
        val habitCategory = intent.getStringExtra(EXTRA_HABIT_CATEGORY) ?: "General"
        val habitColor = intent.getStringExtra(EXTRA_HABIT_COLOR) ?: "#6366F1"
        val hasTimer = intent.getBooleanExtra(EXTRA_HABIT_HAS_TIMER, false)
        val timerMins = intent.getIntExtra(EXTRA_HABIT_TIMER_MINS, 25)

        NotificationHelper.snoozeReminder(
            context = context,
            habitId = habitId,
            habitTitle = habitTitle,
            habitDesc = habitDesc,
            habitCategory = habitCategory,
            habitColor = habitColor,
            hasTimer = hasTimer,
            timerMins = timerMins,
            snoozeMinutes = 15
        )
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.example.action.TRIGGER_HABIT_REMINDER"
        const val ACTION_COMPLETE_HABIT = "com.example.action.COMPLETE_HABIT_NOTIFICATION"
        const val ACTION_SNOOZE_HABIT = "com.example.action.SNOOZE_HABIT_NOTIFICATION"

        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_HABIT_TITLE = "extra_habit_title"
        const val EXTRA_HABIT_DESC = "extra_habit_desc"
        const val EXTRA_HABIT_CATEGORY = "extra_habit_category"
        const val EXTRA_HABIT_COLOR = "extra_habit_color"
        const val EXTRA_HABIT_TIME = "extra_habit_time"
        const val EXTRA_HABIT_ADVANCE = "extra_habit_advance"
        const val EXTRA_HABIT_FREQ_DAYS = "extra_habit_freq_days"
        const val EXTRA_HABIT_CUSTOM_MSG = "extra_habit_custom_msg"
        const val EXTRA_HABIT_HAS_TIMER = "extra_habit_has_timer"
        const val EXTRA_HABIT_TIMER_MINS = "extra_habit_timer_mins"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val EXTRA_OPEN_HABIT_ID = "EXTRA_OPEN_HABIT_ID"
        const val EXTRA_START_POMODORO_HABIT_ID = "EXTRA_START_POMODORO_HABIT_ID"
    }
}

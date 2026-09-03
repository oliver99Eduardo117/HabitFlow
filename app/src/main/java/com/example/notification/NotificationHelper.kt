package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.database.AppDatabase
import com.example.model.Habit
import com.example.receiver.HabitReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object NotificationHelper {

    const val CHANNEL_REMINDERS_ID = "habitflow_reminders"
    const val CHANNEL_REMINDERS_NAME = "Recordatorios de Hábitos"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_REMINDERS_ID,
                CHANNEL_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones y alertas para realizar tus hábitos diarios y mantener tus rachas"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lightColor = AndroidColor.parseColor("#6366F1")
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules the next exact reminder for a habit, taking into account:
     * - reminderTime ("HH:mm")
     * - reminderMinutesAdvance (e.g. 0 min, 10 min, 15 min before)
     * - habit frequency days (1=Mon, 7=Sun)
     */
    fun scheduleHabitReminder(context: Context, habit: Habit) {
        val reminderTime = habit.reminderTime
        if (reminderTime.isNullOrBlank() || habit.isArchived) {
            cancelHabitReminder(context, habit.id)
            return
        }

        val parts = reminderTime.split(":")
        if (parts.size != 2) return

        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habit.id)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TITLE, habit.title)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_DESC, habit.description)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_CATEGORY, habit.category)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_COLOR, habit.colorHex)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TIME, habit.reminderTime)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_ADVANCE, habit.reminderMinutesAdvance)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_CUSTOM_MSG, habit.reminderCustomMessage)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_HAS_TIMER, habit.hasTimer)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TIMER_MINS, habit.timerDurationMinutes)
            putExtra(HabitReminderReceiver.EXTRA_NOTIFICATION_ID, reminderRequestCode(habit.id))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(habit.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetTriggerTime = calculateNextTriggerMillis(
            hour = hour,
            minute = minute,
            advanceMinutes = habit.reminderMinutesAdvance,
            frequencyDays = if (habit.frequencyDays.isNotEmpty()) habit.frequencyDays else listOf(1, 2, 3, 4, 5, 6, 7)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTriggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTriggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTriggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    targetTriggerTime,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    targetTriggerTime,
                    pendingIntent
                )
            } catch (_: Exception) { }
        }
    }

    /**
     * Calculates the exact next millisecond timestamp when the reminder should fire.
     */
    fun calculateNextTriggerMillis(
        hour: Int,
        minute: Int,
        advanceMinutes: Int,
        frequencyDays: List<Int>
    ): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (advanceMinutes > 0) {
                add(Calendar.MINUTE, -advanceMinutes)
            }
        }

        // Loop up to 8 days to find the next valid occurrence that is in the future
        // and matches one of the frequencyDays (1=Monday, 7=Sunday)
        for (dayOffset in 0..7) {
            val candidateTime = calendar.timeInMillis
            val isoDayOfWeek = getIsoDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))

            if (candidateTime > now && isoDayOfWeek in frequencyDays) {
                return candidateTime
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return calendar.timeInMillis
    }

    /**
     * Converts Java Calendar DAY_OF_WEEK (1=Sun, 2=Mon... 7=Sat) to ISO 8601 (1=Mon, 7=Sun).
     */
    fun getIsoDayOfWeek(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    /**
     * Cancels scheduled alarm and removes active notification.
     */
    private fun reminderRequestCode(habitId: Long): Int = habitId.toInt()
    private fun snoozeRequestCode(habitId: Long): Int = (habitId + 10000).toInt()

    fun cancelHabitReminder(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        cancelSnoozeAlarm(context, habitId)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderRequestCode(habitId))
    }

    fun cancelSnoozeAlarm(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun dismissActiveReminder(context: Context, habitId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderRequestCode(habitId))
        cancelSnoozeAlarm(context, habitId)
    }

    /**
     * Snoozes a habit reminder by scheduling a one-time exact alarm after [minutes] (default 15).
     */
    fun snoozeReminder(
        context: Context,
        habitId: Long,
        habitTitle: String,
        habitDesc: String,
        habitCategory: String,
        habitColor: String,
        hasTimer: Boolean,
        timerMins: Int,
        snoozeMinutes: Int = 15
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TITLE, habitTitle)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_DESC, habitDesc)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_CATEGORY, habitCategory)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_COLOR, habitColor)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_HAS_TIMER, hasTimer)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TIMER_MINS, timerMins)
            putExtra(HabitReminderReceiver.EXTRA_NOTIFICATION_ID, reminderRequestCode(habitId))
            putExtra(HabitReminderReceiver.EXTRA_IS_SNOOZE, true)
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeTarget = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTarget,
                    snoozePendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTarget,
                    snoozePendingIntent
                )
            }
        } catch (_: Exception) { }

        // Dismiss current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderRequestCode(habitId))
    }

    /**
     * Reschedules all active habit reminders (called after device reboot, timezone change, or app start).
     */
    fun rescheduleAllReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val activeHabits = db.habitDao().getActiveHabits().first()
                for (habit in activeHabits) {
                    if (!habit.reminderTime.isNullOrBlank()) {
                        scheduleHabitReminder(context, habit)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * Shows an immediate test notification for the habit so user can verify sound, appearance, and actions.
     */
    fun showTestReminderNotification(context: Context, habit: Habit) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = habit.id.toInt().let { if (it != 0) it else 999 }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_HABIT_ID", habit.id)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Mark as Completed directly
        val completeIntent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_COMPLETE_HABIT
            putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habit.id)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TITLE, habit.title)
            putExtra(HabitReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Snooze 15 min
        val snoozeIntent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_SNOOZE_HABIT
            putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habit.id)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TITLE, habit.title)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_DESC, habit.description)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_CATEGORY, habit.category)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_COLOR, habit.colorHex)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_HAS_TIMER, habit.hasTimer)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TIMER_MINS, habit.timerDurationMinutes)
            putExtra(HabitReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 200,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val habitParsedColor = try {
            AndroidColor.parseColor(habit.colorHex)
        } catch (_: Exception) {
            AndroidColor.parseColor("#6366F1")
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Prueba: Hora de ${habit.title}")
            .setContentText(
                if (!habit.reminderCustomMessage.isNullOrBlank()) {
                    habit.reminderCustomMessage
                } else if (habit.reminderMinutesAdvance > 0) {
                    "Tu hábito comenzará en ${habit.reminderMinutesAdvance} minutos. ¡Mantén tu racha activa!"
                } else if (habit.description.isNotEmpty()) {
                    habit.description
                } else {
                    "Recuerda realizar este hábito hoy para mantener tu racha activa en HabitFlow."
                }
            )
            .setColor(habitParsedColor)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
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

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}

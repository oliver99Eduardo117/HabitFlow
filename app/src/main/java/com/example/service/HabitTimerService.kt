package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.receiver.TimerNotificationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HabitTimerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var tickerJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESUME

        when (action) {
            ACTION_START -> {
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildOngoingNotification())
                startTicker()
            }
            ACTION_RESUME -> {
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildOngoingNotification())
                startTicker()
            }
            ACTION_PAUSE -> {
                releaseWakeLock()
                stopTicker()
                notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification())
            }
            ACTION_RESET -> {
                releaseWakeLock()
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SAVE, ACTION_STOP -> {
                releaseWakeLock()
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val finished = TimerManager.tick(applicationContext)
                if (finished) {
                    onPomodoroCompleted()
                    break
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification())
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun onPomodoroCompleted() {
        releaseWakeLock()
        stopTicker()

        triggerCompletionHaptic()
        showCompletionNotification()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildOngoingNotification(): Notification {
        val state = TimerManager.timerState.value
        val isPomodoro = state.isPomodoro

        val displaySeconds = if (isPomodoro) state.remainingSeconds else state.elapsedSeconds
        val timeStr = com.example.util.DateUtils.formatTimerDisplay(displaySeconds)

        val title = if (state.habitTitle.isNotEmpty()) {
            if (isPomodoro) "${state.habitTitle} (Pomodoro)" else "${state.habitTitle} (Libre)"
        } else {
            if (isPomodoro) "Enfoque Pomodoro" else "Cronómetro Libre"
        }

        val statusText = if (state.isRunning) {
            if (isPomodoro) "$timeStr restante • En curso" else "$timeStr transcurrido • En curso"
        } else {
            "Pausado ($timeStr)"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Pause or Resume
        val toggleIntent = Intent(this, TimerNotificationReceiver::class.java).apply {
            action = if (state.isRunning) TimerNotificationReceiver.ACTION_PAUSE else TimerNotificationReceiver.ACTION_RESUME
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Finish & Save
        val saveIntent = Intent(this, TimerNotificationReceiver::class.java).apply {
            action = TimerNotificationReceiver.ACTION_SAVE
        }
        val savePendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(state.isRunning)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (state.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isRunning) "Pausar" else "Reanudar",
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_save,
                "Guardar",
                savePendingIntent
            )

        if (isPomodoro && state.totalSeconds > 0) {
            builder.setProgress(state.totalSeconds, state.elapsedSeconds, false)
        }

        return builder.build()
    }

    private fun showCompletionNotification() {
        val state = TimerManager.lastFinishedSession ?: TimerManager.timerState.value
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            103,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.habitTitle.isNotEmpty()) {
            "¡Pomodoro completado para ${state.habitTitle}!"
        } else {
            "¡Pomodoro completado!"
        }

        val completedMins = state.totalSeconds / 60
        val text = "Completaste $completedMins minutos de enfoque. Tu progreso ha sido guardado automáticamente."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FINISHED)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
    }

    private fun triggerCompletionHaptic() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(longArrayOf(0, 400, 200, 400, 200, 600), -1)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HabitFlow:FocusTimerWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours safety timeout
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Running timer channel (low importance so it doesn't chime repeatedly)
            val runningChannel = NotificationChannel(
                CHANNEL_ID_RUNNING,
                "Temporizador y Pomodoro en Curso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado activo y tiempo restante del cronómetro Pomodoro"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(runningChannel)

            // Finished timer channel (high importance with vibration/sound)
            val finishedChannel = NotificationChannel(
                CHANNEL_ID_FINISHED,
                "Finalización de Pomodoro",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación al completar una sesión Pomodoro"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
            }
            notificationManager.createNotificationChannel(finishedChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_ID_COMPLETED = 2002
        const val CHANNEL_ID_RUNNING = "habitflow_focus_timer_running"
        const val CHANNEL_ID_FINISHED = "habitflow_focus_timer_finished"

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_RESET = "com.example.service.ACTION_RESET"
        const val ACTION_SAVE = "com.example.service.ACTION_SAVE"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_HABIT_TITLE = "extra_habit_title"
        const val EXTRA_IS_POMODORO = "extra_is_pomodoro"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    }
}

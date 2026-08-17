package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.TimerManager

class TimerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_PAUSE -> {
                TimerManager.pauseTimer(context)
            }
            ACTION_RESUME -> {
                TimerManager.resumeTimer(context)
            }
            ACTION_SAVE -> {
                TimerManager.stopAndSave(context)
            }
            ACTION_RESET -> {
                TimerManager.resetTimer(context)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.receiver.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.receiver.ACTION_RESUME"
        const val ACTION_SAVE = "com.example.receiver.ACTION_SAVE"
        const val ACTION_RESET = "com.example.receiver.ACTION_RESET"
    }
}

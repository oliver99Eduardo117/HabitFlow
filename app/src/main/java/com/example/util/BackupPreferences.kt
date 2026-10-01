package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recuerda cuando se guardo o se envio la ultima copia de seguridad. */
class BackupPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastBackupAt = MutableStateFlow(getSavedLastBackupAt())

    /** Milisegundos de la ultima copia; null si nunca hubo una. */
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    private fun getSavedLastBackupAt(): Long? =
        prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L }

    fun markBackupDone(atMillis: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, atMillis).apply()
        _lastBackupAt.value = atMillis
    }

    companion object {
        private const val PREFS_NAME = "habitflow_backup_prefs"
        private const val KEY_LAST_BACKUP_AT = "key_last_backup_at"

        @Volatile
        private var INSTANCE: BackupPreferences? = null

        fun getInstance(context: Context): BackupPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

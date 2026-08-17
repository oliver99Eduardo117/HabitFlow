package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ThemeMode
import com.example.model.ViewLayoutMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(getSavedDynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _layoutMode = MutableStateFlow(getSavedLayoutMode())
    val layoutMode: StateFlow<ViewLayoutMode> = _layoutMode.asStateFlow()

    private fun getSavedThemeMode(): ThemeMode {
        val modeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun getSavedDynamicColor(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    private fun getSavedLayoutMode(): ViewLayoutMode {
        val layoutStr = prefs.getString(KEY_LAYOUT_MODE, ViewLayoutMode.LIST.name) ?: ViewLayoutMode.LIST.name
        return try {
            val mode = ViewLayoutMode.valueOf(layoutStr)
            mode
        } catch (_: Exception) {
            ViewLayoutMode.LIST
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setLayoutMode(mode: ViewLayoutMode) {
        prefs.edit().putString(KEY_LAYOUT_MODE, mode.name).apply()
        _layoutMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "habitflow_theme_prefs"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_LAYOUT_MODE = "key_layout_mode"

        @Volatile
        private var INSTANCE: ThemePreferences? = null

        fun getInstance(context: Context): ThemePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

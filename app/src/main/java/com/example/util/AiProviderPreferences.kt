package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiProviderPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(getSavedIsEnabled())
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _baseUrl = MutableStateFlow(getSavedBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(getSavedApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelName = MutableStateFlow(getSavedModelName())
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private fun getSavedIsEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_ENABLED, false)
    }

    private fun getSavedBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, "") ?: ""
    }

    private fun getSavedApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    private fun getSavedModelName(): String {
        return prefs.getString(KEY_MODEL_NAME, "") ?: ""
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
        _isEnabled.value = enabled
    }

    fun setBaseUrl(url: String) {
        val trimmed = url.trim()
        prefs.edit().putString(KEY_BASE_URL, trimmed).apply()
        _baseUrl.value = trimmed
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        _apiKey.value = trimmed
    }

    fun setModelName(name: String) {
        val trimmed = name.trim()
        prefs.edit().putString(KEY_MODEL_NAME, trimmed).apply()
        _modelName.value = trimmed
    }

    fun saveConfiguration(enabled: Boolean, baseUrl: String, apiKey: String, modelName: String) {
        val trimmedUrl = baseUrl.trim()
        val trimmedKey = apiKey.trim()
        val trimmedModel = modelName.trim()
        prefs.edit()
            .putBoolean(KEY_IS_ENABLED, enabled)
            .putString(KEY_BASE_URL, trimmedUrl)
            .putString(KEY_API_KEY, trimmedKey)
            .putString(KEY_MODEL_NAME, trimmedModel)
            .apply()
        _isEnabled.value = enabled
        _baseUrl.value = trimmedUrl
        _apiKey.value = trimmedKey
        _modelName.value = trimmedModel
    }

    companion object {
        private const val PREFS_NAME = "habitflow_ai_prefs"
        private const val KEY_IS_ENABLED = "key_ai_is_enabled"
        private const val KEY_BASE_URL = "key_ai_base_url"
        private const val KEY_API_KEY = "key_ai_api_key"
        private const val KEY_MODEL_NAME = "key_ai_model_name"

        @Volatile
        private var INSTANCE: AiProviderPreferences? = null

        fun getInstance(context: Context): AiProviderPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiProviderPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

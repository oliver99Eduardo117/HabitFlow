package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiProviderPreferencesTest {

    private lateinit var context: Context
    private lateinit var aiPrefs: AiProviderPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        aiPrefs = AiProviderPreferences(context)
    }

    @Test
    fun `default values are properly initialized`() {
        // Defaults: disabled, empty strings
        assertFalse(aiPrefs.isEnabled.value)
        assertEquals("", aiPrefs.baseUrl.value)
        assertEquals("", aiPrefs.apiKey.value)
        assertEquals("", aiPrefs.modelName.value)
    }

    @Test
    fun `updating individual preferences works and updates state flow`() {
        aiPrefs.setEnabled(true)
        assertTrue(aiPrefs.isEnabled.value)

        aiPrefs.setBaseUrl("http://192.168.1.50:11434/v1")
        assertEquals("http://192.168.1.50:11434/v1", aiPrefs.baseUrl.value)

        aiPrefs.setApiKey("test-api-key")
        assertEquals("test-api-key", aiPrefs.apiKey.value)

        aiPrefs.setModelName("llama3.2:latest")
        assertEquals("llama3.2:latest", aiPrefs.modelName.value)
    }

    @Test
    fun `saveConfiguration saves all fields atomically`() {
        aiPrefs.saveConfiguration(
            enabled = true,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-123456",
            modelName = "gpt-4o-mini"
        )

        assertTrue(aiPrefs.isEnabled.value)
        assertEquals("https://api.openai.com/v1", aiPrefs.baseUrl.value)
        assertEquals("sk-123456", aiPrefs.apiKey.value)
        assertEquals("gpt-4o-mini", aiPrefs.modelName.value)

        // Read from a fresh instance to ensure persistence
        val freshPrefs = AiProviderPreferences(context)
        assertTrue(freshPrefs.isEnabled.value)
        assertEquals("https://api.openai.com/v1", freshPrefs.baseUrl.value)
        assertEquals("sk-123456", freshPrefs.apiKey.value)
        assertEquals("gpt-4o-mini", freshPrefs.modelName.value)
    }
}

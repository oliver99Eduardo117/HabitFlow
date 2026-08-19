package com.example.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatClientTest {

    @Test
    fun `empty base url returns failure without throwing`() = runTest {
        val result = AiChatClient.getChatCompletion(
            baseUrl = "",
            apiKey = "",
            model = "gpt-4o-mini",
            systemPrompt = "System",
            userPrompt = "User"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception?.message?.contains("URL base") == true)
    }

    @Test
    fun `empty model name returns failure without throwing`() = runTest {
        val result = AiChatClient.getChatCompletion(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "",
            model = "",
            systemPrompt = "System",
            userPrompt = "User"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception?.message?.contains("modelo") == true)
    }

    @Test
    fun `unreachable host returns failure with readable error message without throwing`() = runTest {
        val result = AiChatClient.getChatCompletion(
            baseUrl = "http://127.0.0.1:59999/v1",
            apiKey = "",
            model = "local-model",
            systemPrompt = "Test",
            userPrompt = "Hello"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertFalse(exception?.message.isNullOrBlank())
    }
}

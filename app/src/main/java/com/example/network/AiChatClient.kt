package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<ChatMessage>,
    @Json(name = "temperature") val temperature: Double = 0.7
)

@JsonClass(generateAdapter = true)
data class ChatChoice(
    @Json(name = "message") val message: ChatMessage? = null
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    @Json(name = "choices") val choices: List<ChatChoice>? = null
)

object AiChatClient {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)

    /**
     * Executes a chat completion request to any OpenAI-compatible API endpoint
     * (Ollama, LM Studio, OpenAI, Gemini OpenAI endpoint, Groq, OpenRouter, etc.).
     */
    suspend fun getChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedBaseUrl = baseUrl.trim()
        val trimmedModel = model.trim()
        val trimmedKey = apiKey.trim()

        if (trimmedBaseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("La URL base del proveedor de IA no puede estar vacía."))
        }
        if (trimmedModel.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Debes especificar un modelo de IA."))
        }

        val fullUrl = buildChatCompletionsUrl(trimmedBaseUrl)

        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt.trim()))
        }
        messages.add(ChatMessage(role = "user", content = userPrompt.trim()))

        val requestPayload = ChatCompletionRequest(
            model = trimmedModel,
            messages = messages,
            temperature = 0.7
        )

        val jsonBody = try {
            requestAdapter.toJson(requestPayload)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Error al serializar el cuerpo JSON del request: ${e.message}", e))
        }

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")

        if (trimmedKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $trimmedKey")
        }

        val request = requestBuilder.build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                val responseBodyString = resp.body?.string() ?: ""

                if (!resp.isSuccessful) {
                    val preview = if (responseBodyString.length > 200) {
                        responseBodyString.take(200) + "..."
                    } else {
                        responseBodyString
                    }
                    return@withContext Result.failure(
                        IOException("Error HTTP ${resp.code} (${resp.message}): ${preview.ifBlank { "Sin detalles" }}")
                    )
                }

                if (responseBodyString.isBlank()) {
                    return@withContext Result.failure(IOException("El servidor de IA devolvió una respuesta vacía."))
                }

                val completionResponse = try {
                    responseAdapter.fromJson(responseBodyString)
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        Exception("Error al deserializar la respuesta del servidor de IA: ${e.message}", e)
                    )
                }

                val assistantContent = completionResponse?.choices?.firstOrNull()?.message?.content
                if (assistantContent.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException("La respuesta de la IA no contiene texto en 'choices[0].message.content'.")
                    )
                }

                Result.success(assistantContent.trim())
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Tiempo de espera agotado (Timeout) al comunicarse con el servidor de IA ($fullUrl).", e))
        } catch (e: IOException) {
            Result.failure(Exception("Error de conexión con el proveedor de IA: ${e.localizedMessage ?: e.message}", e))
        } catch (e: Exception) {
            Result.failure(Exception("Error inesperado en la consulta de IA: ${e.localizedMessage ?: e.message}", e))
        }
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val sanitized = baseUrl.trim().removeSuffix("/")
        return if (sanitized.endsWith("/chat/completions", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized/chat/completions"
        }
    }
}

package com.calistapp.app.data.ai

import com.calistapp.app.BuildConfig
import com.calistapp.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin client over Google's Gemini `generateContent` REST endpoint (free-tier friendly).
 * The API key + model come from BuildConfig (populated from local.properties).
 */
@Singleton
class GeminiClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val jsonMedia = "application/json".toMediaType()

    suspend fun generate(prompt: String): AiResult = withContext(io) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            return@withContext AiResult.Failure(
                "No Gemini API key configured. Add GEMINI_API_KEY to local.properties and rebuild.",
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${BuildConfig.GEMINI_MODEL}:generateContent?key=$key"

        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt))))),
        ).toRequestBody(jsonMedia)

        val request = Request.Builder().url(url).post(body).build()

        try {
            okHttp.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        json.decodeFromString(GeminiError.serializer(), raw).error?.message
                    }.getOrNull()
                    return@withContext AiResult.Failure(
                        msg ?: "Gemini request failed (HTTP ${resp.code}).",
                    )
                }
                val parsed = runCatching {
                    json.decodeFromString(GeminiResponse.serializer(), raw)
                }.getOrNull()
                val text = parsed?.text
                when {
                    text != null -> AiResult.Success(text.trim())
                    parsed?.promptFeedback?.blockReason != null ->
                        AiResult.Failure("Blocked by safety filter: ${parsed.promptFeedback.blockReason}")
                    else -> AiResult.Failure("Empty response from Gemini.")
                }
            }
        } catch (e: Exception) {
            AiResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }
}

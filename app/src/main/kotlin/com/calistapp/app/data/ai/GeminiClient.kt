package com.calistapp.app.data.ai

import com.calistapp.app.BuildConfig
import com.calistapp.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
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
 *
 * The API key and the model list come from [AiSettingsRepository] (editable in Settings → AI),
 * falling back to the `BuildConfig` values baked from `local.properties`. Each call picks a tier
 * ([AiModelTier]) and walks that tier's primary → fallback → fallback models, moving on when one is
 * rate-limited, overloaded or unknown — so a busy free-tier quota degrades to the next model instead
 * of just failing.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json,
    private val aiSettings: AiSettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val jsonMedia = "application/json".toMediaType()

    /**
     * Generate against [tier]'s models. Defaults to [AiModelTier.FAST]: most callers are the helper
     * kind, and the reasoning-heavy ones (session analysis, coaching) opt into THINKING explicitly.
     */
    suspend fun generate(prompt: String, tier: AiModelTier = AiModelTier.FAST): AiResult = withContext(io) {
        val settings = aiSettings.settings.first()
        val key = settings.apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        if (key.isBlank()) {
            return@withContext AiResult.Failure("No Gemini API key. Add one in Settings → AI.")
        }

        val models = settings.modelsFor(tier).ifEmpty { listOf(BuildConfig.GEMINI_MODEL) }
        var lastMessage = "No AI model produced a response."
        for (model in models) {
            when (val outcome = callOnce(model, key, prompt)) {
                is CallOutcome.Ok -> return@withContext AiResult.Success(outcome.text)
                // Rate-limited / overloaded / unknown model / transient — try the next fallback.
                is CallOutcome.Retry -> lastMessage = outcome.message
                // Safety block, bad key, malformed request — a fallback won't help; stop now.
                is CallOutcome.Fatal -> return@withContext AiResult.Failure(outcome.message)
            }
        }
        AiResult.Failure(lastMessage)
    }

    private fun callOnce(model: String, key: String, prompt: String): CallOutcome {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt))))),
        ).toRequestBody(jsonMedia)
        val request = Request.Builder().url(url).post(body).build()

        return try {
            okHttp.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        json.decodeFromString(GeminiError.serializer(), raw).error?.message
                    }.getOrNull() ?: "Gemini request failed (HTTP ${resp.code})."
                    // 429 quota, 500/503 overloaded, 404 unknown model → a different model may work.
                    // 400/401/403 (bad request or auth) → fatal; the fallbacks would fail the same way.
                    if (resp.code == 429 || resp.code == 404 || resp.code >= 500) {
                        CallOutcome.Retry("$model: $msg")
                    } else {
                        CallOutcome.Fatal(msg)
                    }
                } else {
                    val parsed = runCatching {
                        json.decodeFromString(GeminiResponse.serializer(), raw)
                    }.getOrNull()
                    val text = parsed?.text
                    when {
                        text != null -> CallOutcome.Ok(text.trim())
                        parsed?.promptFeedback?.blockReason != null ->
                            CallOutcome.Fatal("Blocked by safety filter: ${parsed.promptFeedback.blockReason}")
                        else -> CallOutcome.Retry("$model: empty response")
                    }
                }
            }
        } catch (e: Exception) {
            CallOutcome.Retry("Network error: ${e.message ?: "unknown"}")
        }
    }

    /** One model attempt: succeeded, worth trying the next model, or not worth retrying at all. */
    private sealed interface CallOutcome {
        data class Ok(val text: String) : CallOutcome
        data class Retry(val message: String) : CallOutcome
        data class Fatal(val message: String) : CallOutcome
    }
}

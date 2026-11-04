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
    suspend fun generate(
        prompt: String,
        tier: AiModelTier = AiModelTier.FAST,
        // Thinking models spend output tokens *reasoning*, and that reasoning shares this budget with
        // the answer. At 4,000 the coach's deepest reasoning used most of it and the analysis stopped
        // mid-sentence; the thinking tier now gets room for both. The fast helpers stay tight — and any
        // reply that still runs out is retried with more room rather than kept (see [CallOutcome.Truncated]).
        maxOutputTokens: Int = if (tier == AiModelTier.THINKING) THINKING_BUDGET else FAST_BUDGET,
    ): AiResult = withContext(io) {
        val settings = aiSettings.settings.first()
        val key = settings.apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        if (key.isBlank()) {
            return@withContext AiResult.Failure("No Gemini API key. Add one in Settings → AI.")
        }

        val models = settings.modelsFor(tier).ifEmpty { listOf(BuildConfig.GEMINI_MODEL) }
        var lastMessage = "No AI model produced a response."
        for (model in models) {
            var budget = maxOutputTokens
            var thinking = thinkingLevelFor(model, tier)
            attempts@ while (true) {
                when (val outcome = callOnce(model, key, prompt, budget, thinking)) {
                    is CallOutcome.Ok -> return@withContext AiResult.Success(outcome.text)
                    // Cut off before it finished: ask the same model again with more room, then give up
                    // on it. A half answer is never returned — it used to be saved as the analysis.
                    is CallOutcome.Truncated -> if (budget < MAX_BUDGET) {
                        budget = minOf(budget * 4, MAX_BUDGET)
                    } else {
                        lastMessage = "The answer was cut off before it finished. Try again."
                        break@attempts
                    }
                    // A model that doesn't take the reasoning-depth setting: ask it again without.
                    is CallOutcome.RejectedThinking -> if (thinking != null) thinking = null else {
                        lastMessage = outcome.message
                        break@attempts
                    }
                    // Rate-limited / overloaded / unknown model / transient — try the next fallback.
                    is CallOutcome.Retry -> { lastMessage = outcome.message; break@attempts }
                    // Safety block, bad key, malformed request — a fallback won't help; stop now.
                    is CallOutcome.Fatal -> return@withContext AiResult.Failure(outcome.message)
                }
            }
        }
        AiResult.Failure(lastMessage)
    }

    /**
     * The reasoning depth to ask for: the deepest ("high") for the thinking tier on Gemini 3 and
     * later, which take the setting; nothing for the fast helpers or older models, which keep their
     * own defaults.
     */
    private fun thinkingLevelFor(model: String, tier: AiModelTier): String? = thinkingLevel(model, tier)

    /**
     * A one-shot connectivity check for a single model, using [apiKey] as typed in Settings (so it can
     * be tested before it's saved), falling back to the build key. Any 2xx is "connected"; otherwise
     * the API's own error message (bad key, unknown model, quota) is surfaced verbatim so the user can
     * see exactly which slot is wrong.
     */
    suspend fun testModel(model: String, apiKey: String): AiResult = withContext(io) {
        val key = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        if (key.isBlank()) return@withContext AiResult.Failure("No API key set.")
        val id = model.trim()
        if (id.isBlank()) return@withContext AiResult.Failure("No model id.")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$id:generateContent?key=$key"
        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part("Reply with OK.")))),
                generationConfig = GeminiRequest.GenerationConfig(maxOutputTokens = 16),
            ),
        ).toRequestBody(jsonMedia)
        val request = Request.Builder().url(url).post(body).build()
        try {
            okHttp.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    AiResult.Success("Connected")
                } else {
                    val msg = runCatching {
                        json.decodeFromString(GeminiError.serializer(), raw).error?.message
                    }.getOrNull() ?: "HTTP ${resp.code}"
                    AiResult.Failure(msg)
                }
            }
        } catch (e: Exception) {
            AiResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }

    private fun callOnce(model: String, key: String, prompt: String, maxOutputTokens: Int, thinkingLevel: String?): CallOutcome {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt)))),
                generationConfig = GeminiRequest.GenerationConfig(
                    maxOutputTokens = maxOutputTokens,
                    thinkingConfig = thinkingLevel?.let { GeminiRequest.ThinkingConfig(it) },
                ),
            ),
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
                    } else if (resp.code == 400 && thinkingLevel != null && msg.contains("thinking", ignoreCase = true)) {
                        CallOutcome.RejectedThinking("$model: $msg")
                    } else {
                        CallOutcome.Fatal(msg)
                    }
                } else {
                    val parsed = runCatching {
                        json.decodeFromString(GeminiResponse.serializer(), raw)
                    }.getOrNull()
                    val text = parsed?.text
                    when {
                        parsed?.truncated == true -> CallOutcome.Truncated
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

        /** Ran out of output tokens mid-answer (finishReason MAX_TOKENS) — retried with more room. */
        data object Truncated : CallOutcome

        /** The model refused the reasoning-depth setting — asked again without it. */
        data class RejectedThinking(val message: String) : CallOutcome
        data class Retry(val message: String) : CallOutcome
        data class Fatal(val message: String) : CallOutcome
    }

    private companion object {
        /** The thinking tier's first budget: room for deep reasoning and a full analysis together. */
        const val THINKING_BUDGET = 32_768

        /** The fast helpers' budget — short structured replies. */
        const val FAST_BUDGET = 900

        /** The ceiling a truncated reply is retried up to (Gemini Flash's maximum output). */
        const val MAX_BUDGET = 65_536
    }
}

/**
 * The reasoning depth [GeminiClient] asks a model for: "high" for the thinking tier on Gemini 3 and
 * later, nothing otherwise. (The version pattern once read `gemini-(d+)` — a literal "d" in a raw
 * string — so it never matched and the deepest setting was silently never sent.)
 */
internal fun thinkingLevel(model: String, tier: AiModelTier): String? {
    if (tier != AiModelTier.THINKING) return null
    val major = Regex("""gemini-(\d+)""").find(model)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return if (major >= 3) "high" else null
}

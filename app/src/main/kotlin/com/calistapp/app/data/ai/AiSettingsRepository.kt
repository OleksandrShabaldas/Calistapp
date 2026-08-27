package com.calistapp.app.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which class of model a call should use.
 *
 * The split exists to spend the free tier well: **thinking** models (the newest Flash) do the
 * reasoning-heavy work — session analysis and coaching — where quality matters and volume is low;
 * **fast** models (Flash-Lite) do the high-volume helper work — bulk exercise enrichment, quick
 * suggestions — where the generous daily quota lives. Routing the bulk work to Lite keeps the scarce
 * daily quota of the good models for the analysis that actually needs it.
 *
 * A future Claude Code instance wiring a new AI feature should pick the tier by this rule: anything
 * that reasons about the athlete → THINKING; any "make the app nicer to use" helper → FAST.
 */
enum class AiModelTier { THINKING, FAST }

/**
 * The user's AI configuration: one API key, and two tiers each with a primary model and two
 * fallbacks (six model ids total), tried in order when one is rate-limited or unavailable.
 */
data class AiSettings(
    val apiKey: String = "",
    /** [primary, fallback1, fallback2] for [AiModelTier.THINKING]. */
    val thinkingModels: List<String> = DEFAULT_THINKING,
    /** [primary, fallback1, fallback2] for [AiModelTier.FAST]. */
    val fastModels: List<String> = DEFAULT_FAST,
) {
    /** The non-blank model ids to try, in order, for [tier]. */
    fun modelsFor(tier: AiModelTier): List<String> =
        (if (tier == AiModelTier.THINKING) thinkingModels else fastModels).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /**
         * Defaults chosen against the user's Gemini free-tier limits (Aug 2026): the Pro models show
         * 0/0 there (not available on the tier), so both tiers are Flash-family. Thinking = newest
         * Flash (best quality, ~20 req/day); Fast = Flash-Lite (500 req/day — volume goes where the
         * quota is). All editable in Settings → AI, which is the point of storing them here.
         */
        val DEFAULT_THINKING = listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3-flash")
        val DEFAULT_FAST = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite")
    }
}

private val Context.aiPrefs by preferencesDataStore(name = "ai_prefs")

@Singleton
class AiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val apiKeyKey = stringPreferencesKey("api_key")
    private fun modelKey(tier: AiModelTier, index: Int) =
        stringPreferencesKey("model_${tier.name.lowercase()}_$index")

    val settings: Flow<AiSettings> = context.aiPrefs.data.map { p ->
        fun tierModels(tier: AiModelTier, defaults: List<String>) =
            List(3) { i -> p[modelKey(tier, i)] ?: defaults[i] }
        AiSettings(
            apiKey = p[apiKeyKey] ?: "",
            thinkingModels = tierModels(AiModelTier.THINKING, AiSettings.DEFAULT_THINKING),
            fastModels = tierModels(AiModelTier.FAST, AiSettings.DEFAULT_FAST),
        )
    }

    suspend fun setApiKey(key: String) = context.aiPrefs.edit { it[apiKeyKey] = key.trim() }

    /** Set one slot ([index] 0 = primary, 1–2 = fallbacks) of [tier]. */
    suspend fun setModel(tier: AiModelTier, index: Int, id: String) =
        context.aiPrefs.edit { it[modelKey(tier, index)] = id.trim() }

    /** Restore all six model slots to the built-in defaults (leaves the API key alone). */
    suspend fun resetModels() = context.aiPrefs.edit { p ->
        AiModelTier.entries.forEach { tier -> (0..2).forEach { i -> p.remove(modelKey(tier, i)) } }
    }
}

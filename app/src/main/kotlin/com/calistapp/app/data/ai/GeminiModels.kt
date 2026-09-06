package com.calistapp.app.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig = GenerationConfig(),
) {
    @Serializable
    data class Content(val parts: List<Part>, val role: String = "user")

    @Serializable
    data class Part(val text: String)

    @Serializable
    data class GenerationConfig(
        val temperature: Double = 0.7,
        @SerialName("maxOutputTokens") val maxOutputTokens: Int = 900,
    )
}

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
) {
    @Serializable
    data class Candidate(val content: Content? = null, val finishReason: String? = null)

    @Serializable
    data class Content(val parts: List<Part> = emptyList())

    @Serializable
    data class Part(
        val text: String = "",
        /**
         * True on a *thinking* model's reasoning parts. The newest Flash models (the THINKING tier)
         * stream their scratch work back as ordinary parts flagged with this; joining them into the
         * answer is how "3. What to improve → Checked (bullets)" ended up on screen. The final answer
         * is the non-thought parts, so those are all we keep.
         */
        val thought: Boolean = false,
    )

    @Serializable
    data class PromptFeedback(val blockReason: String? = null)

    val text: String?
        get() = candidates.firstOrNull()?.content?.parts
            ?.filterNot { it.thought }
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }
}

@Serializable
data class GeminiError(val error: ErrorBody? = null) {
    @Serializable
    data class ErrorBody(val code: Int = 0, val message: String = "", val status: String = "")
}

/** Outcome of an AI call, kept explicit so the UI can show a helpful message on failure. */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Failure(val message: String) : AiResult
}

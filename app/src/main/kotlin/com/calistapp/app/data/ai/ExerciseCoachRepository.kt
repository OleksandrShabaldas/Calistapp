package com.calistapp.app.data.ai

import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.Faq
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ExerciseCoaching(
    val overview: String = "",
    val commonMistakes: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
)

/** A richer AI-generated draft returned to the manual editor (not persisted until the user saves). */
@Serializable
data class ExerciseAiSuggestion(
    val overview: String = "",
    val instructions: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val problematicAreas: List<String> = emptyList(),
    val efficiency: Int = 0,
)

sealed interface CoachSuggestResult {
    data class Success(val suggestion: ExerciseAiSuggestion) : CoachSuggestResult
    data class Failure(val message: String) : CoachSuggestResult
}

/** The AI shape of one answered FAQ, before it's turned into a [Faq]. */
@Serializable
private data class FaqDraft(val question: String = "", val answer: String = "")

sealed interface FaqAnswerResult {
    data class Success(val faq: Faq) : FaqAnswerResult
    data class Failure(val message: String) : FaqAnswerResult
}

/**
 * Fills in the "authored feel" coaching fields (overview, common mistakes, tips) for exercises that
 * came from the raw dataset, using Gemini. Results are cached back into the exercise row so each
 * exercise is only generated once. This is how the full ~873-exercise library gets rich content
 * without hand-writing every entry.
 */
@Singleton
class ExerciseCoachRepository @Inject constructor(
    private val gemini: GeminiClient,
    private val exerciseRepository: ExerciseRepository,
    private val json: Json,
) {
    /** True once an exercise has the specific coaching content (authored or AI-generated). */
    fun isEnriched(exercise: Exercise): Boolean =
        exercise.commonMistakes.isNotEmpty() || exercise.tips.isNotEmpty()

    suspend fun enrich(exercise: Exercise): AiResult {
        if (isEnriched(exercise)) return AiResult.Success("already enriched")

        return when (val result = gemini.generate(buildPrompt(exercise))) {
            is AiResult.Success -> {
                val coaching = parse(result.text)
                if (coaching == null) {
                    AiResult.Failure("Couldn't parse the AI response.")
                } else {
                    exerciseRepository.upsertAll(
                        listOf(
                            exercise.copy(
                                overview = coaching.overview.ifBlank { exercise.overview },
                                commonMistakes = coaching.commonMistakes,
                                tips = coaching.tips,
                                tags = (exercise.tags + "ai-enriched").distinct(),
                            ),
                        ),
                    )
                    AiResult.Success("enriched")
                }
            }
            is AiResult.Failure -> result
        }
    }

    /**
     * Generate a full coaching draft (overview, instructions, mistakes, tips, problematic areas,
     * efficiency) for a not-yet-saved exercise, so the editor can pre-fill the form and let the user
     * tweak before saving. Does NOT touch the database.
     */
    suspend fun suggest(exercise: Exercise): CoachSuggestResult =
        when (val result = gemini.generate(buildSuggestPrompt(exercise))) {
            is AiResult.Success -> {
                val start = result.text.indexOf('{')
                val end = result.text.lastIndexOf('}')
                val parsed = if (start in 0 until end) {
                    runCatching {
                        json.decodeFromString(ExerciseAiSuggestion.serializer(), result.text.substring(start, end + 1))
                    }.getOrNull()
                } else null
                if (parsed == null) CoachSuggestResult.Failure("Couldn't parse the AI response.")
                else CoachSuggestResult.Success(parsed)
            }
            is AiResult.Failure -> CoachSuggestResult.Failure(result.message)
        }

    /**
     * Answer a user's typed question about [exercise] in the FAQ voice, and cache it onto the
     * exercise row (as a [Faq] with `generated = true`) so it stays on the movement for good. Asking
     * the same question again returns the cached answer rather than stacking a duplicate or spending
     * another call. A single Q&A is light, so it rides the FAST tier.
     */
    suspend fun answerFaq(exercise: Exercise, question: String): FaqAnswerResult {
        val q = question.trim()
        if (q.isBlank()) return FaqAnswerResult.Failure("Type a question first.")

        val existing = exercise.faqs.firstOrNull { it.question.trim().equals(q, ignoreCase = true) }
        if (existing != null) return FaqAnswerResult.Success(existing)

        return when (val result = gemini.generate(buildFaqPrompt(exercise, q))) {
            is AiResult.Success -> {
                val faq = parseFaq(result.text, fallbackQuestion = q)
                    ?: return FaqAnswerResult.Failure("Couldn't parse the AI response.")
                // Re-check against the (possibly cleaned) question the model returned before appending.
                val already = exercise.faqs.firstOrNull {
                    it.question.trim().equals(faq.question.trim(), ignoreCase = true)
                }
                if (already == null) {
                    exerciseRepository.upsert(exercise.copy(faqs = exercise.faqs + faq))
                }
                FaqAnswerResult.Success(already ?: faq)
            }
            is AiResult.Failure -> FaqAnswerResult.Failure(result.message)
        }
    }

    private fun parseFaq(text: String, fallbackQuestion: String): Faq? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start !in 0 until end) return null
        val parsed = runCatching {
            json.decodeFromString(FaqDraft.serializer(), text.substring(start, end + 1))
        }.getOrNull() ?: return null
        val answer = parsed.answer.trim()
        if (answer.isBlank()) return null
        return Faq(
            question = parsed.question.trim().ifBlank { fallbackQuestion },
            answer = answer,
            generated = true,
        )
    }

    private fun buildFaqPrompt(e: Exercise, question: String): String = buildString {
        appendLine("You are an expert strength & conditioning coach answering a FAQ about one exercise.")
        appendLine("Respond with ONLY minified JSON (no markdown, no prose) with keys:")
        appendLine("""  "question": the user's question, tidied into a clean, concise FAQ question,""")
        appendLine("""  "answer": a direct, accurate 1-3 sentence answer specific to THIS exercise.""")
        appendLine()
        appendLine("Exercise: ${e.name}")
        appendLine("Body part: ${e.bodyPart.displayName}; Difficulty: ${e.difficulty.displayName}")
        appendLine("Equipment: ${e.equipment.joinToString().ifBlank { "body only" }}")
        if (e.primaryMuscles.isNotEmpty()) appendLine("Primary muscles: ${e.primaryMuscles.joinToString()}")
        appendLine("""User question: "$question"""")
        appendLine("Be specific and accurate to THIS exercise. Don't invent equipment it doesn't use.")
    }

    private fun buildSuggestPrompt(e: Exercise): String = buildString {
        appendLine("You are an expert strength & conditioning coach.")
        appendLine("For the exercise below, respond with ONLY minified JSON (no markdown, no prose) with keys:")
        appendLine("""  "overview": a 1-2 sentence honest description (who it's for, how effective),""")
        appendLine("""  "instructions": array of 3-5 short step-by-step strings,""")
        appendLine("""  "commonMistakes": array of 2-4 short strings,""")
        appendLine("""  "tips": array of 2-3 short strings,""")
        appendLine("""  "problematicAreas": array of joints/areas it can stress (e.g. "Knees","Lower back","Shoulders"),""")
        appendLine("""  "efficiency": integer 1-5 (strength built vs energy required; 5 = excellent).""")
        appendLine()
        appendLine("Exercise: ${e.name}")
        appendLine("Body part: ${e.bodyPart.displayName}; Difficulty: ${e.difficulty.displayName}")
        appendLine("Equipment: ${e.equipment.joinToString().ifBlank { "body only" }}")
        if (e.primaryMuscles.isNotEmpty()) appendLine("Primary muscles: ${e.primaryMuscles.joinToString()}")
        if (e.secondaryMuscles.isNotEmpty()) appendLine("Secondary muscles: ${e.secondaryMuscles.joinToString()}")
        appendLine("Be specific and accurate to THIS exercise. Do not invent equipment it doesn't use.")
    }

    private fun parse(text: String): ExerciseCoaching? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.decodeFromString(ExerciseCoaching.serializer(), text.substring(start, end + 1))
        }.getOrNull()?.takeIf { it.commonMistakes.isNotEmpty() || it.tips.isNotEmpty() }
    }

    private fun buildPrompt(e: Exercise): String = buildString {
        appendLine("You are an expert strength & conditioning coach.")
        appendLine("For the exercise below, respond with ONLY minified JSON (no markdown, no prose) with keys:")
        appendLine("""  "overview": a 1-2 sentence honest description (who it's for, how effective),""")
        appendLine("""  "commonMistakes": array of 2-4 short strings,""")
        appendLine("""  "tips": array of 2-3 short strings.""")
        appendLine()
        appendLine("Exercise: ${e.name}")
        appendLine("Body part: ${e.bodyPart.displayName}; Difficulty: ${e.difficulty.displayName}")
        appendLine("Equipment: ${e.equipment.joinToString().ifBlank { "body only" }}")
        appendLine("Primary muscles: ${e.primaryMuscles.joinToString()}")
        if (e.secondaryMuscles.isNotEmpty()) appendLine("Secondary muscles: ${e.secondaryMuscles.joinToString()}")
        if (e.instructions.isNotEmpty()) appendLine("Instructions: ${e.instructions.joinToString(" ")}")
        appendLine("Be specific and accurate to THIS exercise. Do not invent equipment it doesn't use.")
    }
}

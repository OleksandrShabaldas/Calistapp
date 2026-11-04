package com.calistapp.app.data.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String) = json.decodeFromString(GeminiResponse.serializer(), raw)

    @Test
    fun `thinking parts are excluded from the answer`() {
        // A thinking-tier reply: the model's scratch work comes back as a part flagged thought=true,
        // followed by the real answer. Only the answer should surface — this is the bug that put
        // "3. What to improve -> Checked (bullets)" on screen.
        val raw = """
            {
              "candidates": [
                { "content": { "parts": [
                  { "text": "Plan: 1. Overall -> Checked. 3. What to improve -> Checked (bullets).", "thought": true },
                  { "text": "1. Overall assessment\nSolid pull session." }
                ] } }
              ]
            }
        """.trimIndent()

        assertEquals("1. Overall assessment\nSolid pull session.", decode(raw).text)
    }

    @Test
    fun `a plain reply with no thought parts is unchanged`() {
        val raw = """
            {"candidates":[{"content":{"parts":[{"text":"Just the answer."}]}}]}
        """.trimIndent()
        assertEquals("Just the answer.", decode(raw).text)
    }

    @Test
    fun `multiple answer parts are joined, thought parts dropped`() {
        val raw = """
            {"candidates":[{"content":{"parts":[
              {"text":"thinking...","thought":true},
              {"text":"Part one. "},
              {"text":"Part two."}
            ]}}]}
        """.trimIndent()
        assertEquals("Part one. Part two.", decode(raw).text)
    }

    @Test
    fun `a reply that is only thinking has no usable text`() {
        val raw = """
            {"candidates":[{"content":{"parts":[{"text":"just thinking","thought":true}]}}]}
        """.trimIndent()
        assertNull(decode(raw).text)
    }

    @Test
    fun `a reply cut off by the token limit is flagged, not taken as the answer`() {
        // What happened to the coach: the thinking used most of the budget and the answer stopped
        // mid-sentence. The API says so in finishReason; the client must not keep the half answer.
        val raw = """
            {"candidates":[{"content":{"parts":[{"text":"Your average heart rate remained low at 106 bpm ("}]},"finishReason":"MAX_TOKENS"}]}
        """.trimIndent()
        val parsed = decode(raw)
        assertTrue(parsed.truncated)
        assertEquals("Your average heart rate remained low at 106 bpm (", parsed.text)
    }

    @Test
    fun `a finished reply is not flagged`() {
        val raw = """{"candidates":[{"content":{"parts":[{"text":"Done."}]},"finishReason":"STOP"}]}"""
        assertFalse(decode(raw).truncated)
    }

    @Test
    fun `the reasoning depth is sent only when set`() {
        val encoder = Json { encodeDefaults = true }
        fun body(level: String?) = encoder.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part("hi")))),
                generationConfig = GeminiRequest.GenerationConfig(
                    maxOutputTokens = 10,
                    thinkingConfig = level?.let { GeminiRequest.ThinkingConfig(it) },
                ),
            ),
        )
        assertFalse(body(null).contains("thinkingConfig"))
        assertTrue(body("high").contains(""""thinkingConfig":{"thinkingLevel":"high"}"""))
    }

    @Test
    fun `the deepest reasoning is asked of Gemini 3 and later thinking models only`() {
        assertEquals("high", thinkingLevel("gemini-3-pro-preview", AiModelTier.THINKING))
        assertEquals("high", thinkingLevel("gemini-3.1-pro", AiModelTier.THINKING))
        assertNull(thinkingLevel("gemini-2.5-pro", AiModelTier.THINKING))
        assertNull(thinkingLevel("gemini-3-flash", AiModelTier.FAST))
    }
}

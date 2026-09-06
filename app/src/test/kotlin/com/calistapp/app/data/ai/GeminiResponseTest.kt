package com.calistapp.app.data.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

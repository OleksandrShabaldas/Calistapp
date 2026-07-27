package com.calistapp.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `bold and italic markers are removed but the words stay`() {
        assertEquals("In-app updates. Now works.", ReleaseNotes.toPlainText("**In-app updates.** Now works."))
        assertEquals("Check for updates now", ReleaseNotes.toPlainText("*Check for updates* now"))
    }

    @Test
    fun `links keep their text and drop the url`() {
        assertEquals(
            "See the README for details",
            ReleaseNotes.toPlainText("See the [README](https://example.com/readme) for details"),
        )
    }

    @Test
    fun `code spans lose their backticks`() {
        assertEquals("Run CHECK_UPDATE first", ReleaseNotes.toPlainText("Run `CHECK_UPDATE` first"))
    }

    @Test
    fun `headings, quotes and bullets become plain lines`() {
        val md = """
            ## What's new
            - First thing
            - Second thing
            > A note
        """.trimIndent()
        assertEquals(
            "What's new\n• First thing\n• Second thing\nA note",
            ReleaseNotes.toPlainText(md),
        )
    }

    @Test
    fun `blank lines are dropped so the card stays compact`() {
        val md = "First\n\n\nSecond"
        assertEquals("First\nSecond", ReleaseNotes.toPlainText(md))
    }

    @Test
    fun `output is capped to the requested number of lines`() {
        val md = (1..20).joinToString("\n") { "Line $it" }
        assertEquals(3, ReleaseNotes.toPlainText(md, maxLines = 3).lines().size)
    }

    @Test
    fun `real release notes come out clean`() {
        val md = """
            **In-app updates.** Calistapp now checks for new versions itself.

            - **Phone:** Profile → App version → *Check for updates*
            - **Watch:** *Update watch app* asks the watch to update itself
        """.trimIndent()

        val text = ReleaseNotes.toPlainText(md)
        assertFalse("Markdown markers leaked through: $text", text.contains("**"))
        assertEquals(
            "In-app updates. Calistapp now checks for new versions itself.\n" +
                "• Phone: Profile → App version → Check for updates\n" +
                "• Watch: Update watch app asks the watch to update itself",
            text,
        )
    }

    @Test
    fun `plain text passes through untouched`() {
        assertEquals("Just a normal line.", ReleaseNotes.toPlainText("Just a normal line."))
    }

    @Test
    fun `empty notes produce empty output`() {
        assertEquals("", ReleaseNotes.toPlainText(""))
    }
}

package com.calistapp.core.update

/**
 * Renders GitHub release notes as plain text.
 *
 * Release bodies are Markdown, and neither the phone card nor the watch chip renders Markdown — so
 * without this they show the source, literal `**` and `[link](url)` included, which looks broken.
 * Pulling in a Markdown renderer for a few lines of changelog would be a poor trade; stripping the
 * handful of constructs release notes actually use is enough.
 */
object ReleaseNotes {

    private val BOLD = Regex("""\*\*(.+?)\*\*""")
    private val ITALIC = Regex("""(?<![*\w])\*([^*\n]+?)\*(?!\*)""")
    private val CODE = Regex("""`([^`\n]+?)`""")
    private val LINK = Regex("""\[([^]]+)]\([^)]*\)""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s*""")
    private val QUOTE = Regex("""^\s{0,3}>\s?""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+""")

    /**
     * Flatten [markdown] to readable text, keeping at most [maxLines] non-empty lines.
     *
     * Blank lines are dropped rather than preserved: the card shows a few lines in a small type
     * size, and paragraph spacing there costs more room than it earns.
     */
    fun toPlainText(markdown: String, maxLines: Int = 6): String =
        markdown.lineSequence()
            .map { line ->
                line.replace(HEADING, "")
                    .replace(QUOTE, "")
                    .replace(BULLET, "$1• ")
                    .replace(BOLD, "$1")
                    .replace(CODE, "$1")
                    .replace(LINK, "$1")
                    .replace(ITALIC, "$1")
                    .trimEnd()
            }
            .filter { it.isNotBlank() }
            .take(maxLines)
            .joinToString("\n")
            .trim()
}

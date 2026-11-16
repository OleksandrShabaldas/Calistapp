package com.calistapp.app.ui.exercises

/**
 * Rewrites SVG path data into a form Compose's `PathParser` reads correctly.
 *
 * SVG lets an arc's two flags run together with the numbers around them — `a1.14 1.14 0 01-2.22.23`
 * means flags `0` and `1`, then `-2.22 .23`. Compose's parser (1.7) doesn't split those: it **drops the
 * whole arc**, so every shape with a compact arc lost a segment. That is what "cracked" the front
 * figure's head and took the back head apart in the exercise muscle map (Chrome draws the same data
 * whole, which is how it was found). This tokenises the data the way the SVG grammar does — an arc's
 * flags are a single `0`/`1` character each — and re-emits every number separated by spaces.
 */
internal object SvgPathNormalizer {

    fun normalize(d: String): String {
        val out = StringBuilder(d.length + 16)
        var i = 0
        var command = ' '
        var argIndex = 0
        while (i < d.length) {
            val c = d[i]
            when {
                c.isLetter() && c != 'e' && c != 'E' -> {
                    command = c
                    argIndex = 0
                    if (out.isNotEmpty()) out.append(' ')
                    out.append(c)
                    i++
                }
                c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r' -> i++
                else -> {
                    // An arc's 4th and 5th arguments (large-arc, sweep) are one character each.
                    val isArc = command == 'a' || command == 'A'
                    val end = if (isArc && (argIndex % 7 == 3 || argIndex % 7 == 4)) i + 1 else numberEnd(d, i)
                    out.append(' ').append(d, i, end)
                    argIndex++
                    i = end
                }
            }
        }
        return out.toString()
    }

    /** The end (exclusive) of the SVG number starting at [start]: sign, digits, one dot, exponent. */
    private fun numberEnd(s: String, start: Int): Int {
        var i = start
        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
        var seenDot = false
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() -> i++
                c == '.' && !seenDot -> { seenDot = true; i++ }
                (c == 'e' || c == 'E') && i + 1 < s.length -> {
                    i++
                    if (s[i] == '+' || s[i] == '-') i++
                }
                else -> break
            }
        }
        // A lone sign or dot never forms a number; consume one character so parsing always advances.
        return if (i == start) start + 1 else i
    }
}

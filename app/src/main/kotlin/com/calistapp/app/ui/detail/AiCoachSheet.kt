package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.OnyxRaised
import java.util.concurrent.TimeUnit

/**
 * The AI coach's read on the session, as a slide-up sheet rather than a block of text dumped into the
 * page. The analysis is saved on the session, so this is where you *read* it on later visits; the
 * regenerate control lives here too — a small, destructive-red button gated behind a confirmation,
 * because a regenerate spends a quota call and replaces what's here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachSheet(
    insight: String?,
    aiState: AiUiState,
    generatedAtMs: Long?,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmRegen by remember { mutableStateOf(false) }
    val loading = aiState is AiUiState.Loading
    val error = (aiState as? AiUiState.Error)?.message

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OnyxRaised) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header — title left, regenerate right (only once there's something to replace).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    GlowIcon(Icons.Filled.AutoAwesome, null, Flame, size = 26.dp, glowRadius = 8.dp, glowAlpha = 0.55f)
                    Column {
                        Text("AI Coach", style = MaterialTheme.typography.headlineSmall, color = Chalk)
                        Text("Read on this session", style = MaterialTheme.typography.bodySmall, color = Ash)
                    }
                }
                if (insight != null && !loading) {
                    // Icon-only circular button — the meaning is carried by the glyph, and the confirm
                    // dialog spells out what regenerate does before it spends a quota call.
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Coral.copy(alpha = 0.12f))
                            .border(1.dp, Coral.copy(alpha = 0.35f), CircleShape)
                            .clickable { confirmRegen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", tint = Coral, modifier = Modifier.size(19.dp))
                    }
                }
            }

            val maxBody = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxBody).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    loading -> LoadingBlock()
                    error != null -> ErrorBlock(error, onRetry = onRegenerate)
                    insight != null -> {
                        CoachBody(insight)
                        Footer(generatedAtMs)
                    }
                    else -> Text(
                        "Tap analyze to get a read on this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ash,
                    )
                }
            }
        }
    }

    if (confirmRegen) {
        AlertDialog(
            onDismissRequest = { confirmRegen = false },
            title = { Text("Regenerate analysis?") },
            text = { Text("This replaces the current analysis and spends one AI request.") },
            confirmButton = {
                TextButton(onClick = { confirmRegen = false; onRegenerate() }) {
                    Text("Regenerate", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRegen = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun LoadingBlock() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        CircularProgressIndicator(color = FlameHot, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Text("Reading your heart-rate and effort data…", style = MaterialTheme.typography.bodyMedium, color = Ash)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Coral)
        TextButton(onClick = onRetry) { Text("Try again", color = FlameHot) }
    }
}

@Composable
private fun Footer(generatedAtMs: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        Text(
            (generatedAtMs?.let { "Generated ${ago(it)} · " } ?: "") +
                "grounded in your numbers — it reads them, it never invents them.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

private fun ago(ms: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - ms).coerceAtLeast(0))
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

// ---- Formatted coach output ----

private const val GLOSS_TAG = "gloss"

/** A raised, slightly-lighter surface for the glossary tooltip so it reads as floating over the sheet. */
private val TooltipSurface = Color(0xFF232228)

private sealed interface Block {
    data class Heading(val n: String?, val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Para(val text: String) : Block
    data object Divider : Block
}

/** The coach's reply split into the readable analysis and the term→explanation glossary after it. */
private data class Coach(val body: String, val glossary: Map<String, String>)

/** A glossary tooltip in flight: which term, its explanation, and the window point it was tapped at. */
private data class Tip(val term: String, val explanation: String, val anchor: IntOffset)

/**
 * A light parser for the coach's reply — numbered/bold section titles become headings, dash/•/star
 * lines become bullets, a rule line ("---") becomes a styled divider, everything else a paragraph, so
 * the analysis reads as a structured brief. Inline **bold** and *italic* are honoured (markers
 * stripped); jargon the model glossed is underlined and taps to a tooltip; and the "@" shorthand it
 * sometimes writes for "at" is spelled out.
 */
@Composable
private fun CoachBody(text: String) {
    val coach = remember(text) { splitGlossary(text) }
    val blocks = remember(coach.body) { parseCoach(coach.body) }
    var tip by remember { mutableStateOf<Tip?>(null) }
    val onTip: (String, IntOffset) -> Unit = { term, at -> coach.glossary[term]?.let { tip = Tip(term, it, at) } }

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (block.n != null) {
                        Box(
                            Modifier.size(22.dp).background(Flame.copy(alpha = 0.14f), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(block.n, style = MaterialTheme.typography.labelMedium, color = FlameHot, fontWeight = FontWeight.Bold) }
                    }
                    Text(block.text, style = MaterialTheme.typography.titleMedium, color = Chalk, fontWeight = FontWeight.SemiBold)
                }
                is Block.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).background(FlameHot, RoundedCornerShape(3.dp)))
                    GlossaryText(mdInline(block.text, coach.glossary), MaterialTheme.typography.bodyMedium, Chalk, onTip)
                }
                is Block.Para -> GlossaryText(mdInline(block.text, coach.glossary), MaterialTheme.typography.bodyMedium, Ash, onTip)
                Block.Divider -> CoachDivider()
            }
        }
    }

    tip?.let { t ->
        Popup(popupPositionProvider = TipPositionProvider(t.anchor), onDismissRequest = { tip = null }) {
            GlossaryTooltip(t.term, t.explanation) { tip = null }
        }
    }
}

/** A thin, centre-fading rule with a small glowing dot — a proper section break, not a bare "---". */
@Composable
private fun CoachDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Chalk.copy(alpha = 0.20f)))))
        Box(Modifier.glow(Flame, spread = 5.dp, alpha = 0.5f).size(5.dp).clip(CircleShape).background(Flame))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Chalk.copy(alpha = 0.20f), Color.Transparent))))
    }
}

/**
 * A paragraph/bullet of coach text whose glossary terms tap to a tooltip. Taps are hit-tested against
 * the laid-out text's string annotations; a tap that lands on a term reports its window position so the
 * tooltip can be anchored right at it.
 */
@Composable
private fun GlossaryText(
    annotated: AnnotatedString,
    style: TextStyle,
    color: Color,
    onTip: (term: String, anchor: IntOffset) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Text(
        text = annotated,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier
            .onGloballyPositioned { coords = it }
            .pointerInput(annotated) {
                detectTapGestures { pos ->
                    val lr = layout ?: return@detectTapGestures
                    val offset = lr.getOffsetForPosition(pos)
                    annotated.getStringAnnotations(GLOSS_TAG, offset, offset).firstOrNull()?.let { ann ->
                        val w = coords?.localToWindow(pos)
                        if (w != null) onTip(ann.item, IntOffset(w.x.toInt(), w.y.toInt()))
                    }
                }
            },
    )
}

@Composable
private fun GlossaryTooltip(term: String, explanation: String, onDismiss: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .glow(Color.Black, spread = 22.dp, alpha = 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(TooltipSurface)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(term, style = MaterialTheme.typography.labelLarge, color = FlameHot, fontWeight = FontWeight.Bold)
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = Chalk)
    }
}

/** Places the tooltip centred under the tapped word, flipping above and clamping to stay on-screen. */
private class TipPositionProvider(private val anchor: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 12
        val x = (anchor.x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = anchor.y + 20
        val y = if (below + popupContentSize.height + margin <= windowSize.height) {
            below
        } else {
            (anchor.y - popupContentSize.height - 14).coerceAtLeast(margin)
        }
        return IntOffset(x, y)
    }
}

private val HEADING = Regex("""^\s*(\d+)[.)]\s+(.*)""")
private val MD_HEADING = Regex("""^\s*#{1,4}\s+(.*)""")
private val BULLET = Regex("""^\s*[-*•]\s+(.*)""")
private val BOLD_ONLY = Regex("""^\s*\*\*(.+?)\*\*[:：]?\s*$""")
private val GLOSSARY_DELIM = Regex("""(?im)^\s*=+\s*glossary\s*=+\s*$""")

/**
 * Split the reply at the `===GLOSSARY===` marker into the body and the term map. Each glossary line is
 * `term :: explanation` (leading bullets/`**` tolerated); the body is tidied of the "@"-for-"at" tic.
 */
private fun splitGlossary(text: String): Coach {
    val m = GLOSSARY_DELIM.find(text) ?: return Coach(sanitize(text), emptyMap())
    val body = sanitize(text.substring(0, m.range.first))
    val glossary = LinkedHashMap<String, String>()
    text.substring(m.range.last + 1).lines().forEach { raw ->
        val line = raw.trim().trimStart('-', '*', '•', ' ')
        val sep = line.indexOf("::")
        if (sep <= 0) return@forEach
        val term = line.substring(0, sep).replace("**", "").trim().trimEnd(':').trim()
        val explanation = line.substring(sep + 2).replace("**", "").trim()
        if (term.isNotEmpty() && explanation.isNotEmpty()) glossary[term] = explanation
    }
    return Coach(body, glossary)
}

/** Spell out the "@" the model uses as shorthand for "at" (e.g. "10 reps @ +15 kg"). */
private fun sanitize(s: String): String = s.replace(Regex("""[ \t]*@[ \t]*"""), " at ").trim()

/** A horizontal-rule line — only rule characters, at least three: "---", "***", "———". */
private fun isRule(line: String): Boolean =
    line.length >= 3 && line.all { it == '-' || it == '*' || it == '_' || it == '—' || it == ' ' } &&
        line.count { it == '-' || it == '*' || it == '_' || it == '—' } >= 3

private fun parseCoach(text: String): List<Block> = buildList {
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isBlank()) continue
        if (isRule(line)) { add(Block.Divider); continue }
        val h = HEADING.find(line)
        val mh = MD_HEADING.find(line)
        val bo = BOLD_ONLY.find(line)
        val b = BULLET.find(line)
        when {
            h != null -> add(Block.Heading(h.groupValues[1], stripBold(h.groupValues[2])))
            mh != null -> add(Block.Heading(null, stripBold(mh.groupValues[1])))
            bo != null -> add(Block.Heading(null, bo.groupValues[1]))
            b != null -> add(Block.Bullet(b.groupValues[1]))
            else -> add(Block.Para(line))
        }
    }
}

private fun stripBold(s: String) = s.replace("**", "").trim()

/**
 * Build an [AnnotatedString] rendering `**bold**` and `*italic*` (markers dropped), then underline any
 * glossary terms it contains and tag them so a tap can look the term up. Longest terms match first so
 * "Banister TRIMP" wins over "TRIMP"; matches respect word boundaries and never overlap.
 */
private fun mdInline(s: String, glossary: Map<String, String>): AnnotatedString {
    val base = buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '*' && i + 1 < s.length && s[i + 1] == '*') {
                val close = s.indexOf("**", i + 2)
                if (close >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Chalk)) { append(s.substring(i + 2, close)) }
                    i = close + 2
                    continue
                }
            } else if (c == '*') {
                val close = s.indexOf('*', i + 1)
                if (close > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, close)) }
                    i = close + 1
                    continue
                }
            }
            append(c)
            i++
        }
    }
    return withGlossary(base, glossary)
}

private fun withGlossary(base: AnnotatedString, glossary: Map<String, String>): AnnotatedString {
    if (glossary.isEmpty()) return base
    val plain = base.text
    val lower = plain.lowercase()
    val taken = BooleanArray(plain.length)
    return buildAnnotatedString {
        append(base)
        glossary.keys.sortedByDescending { it.length }.forEach { term ->
            val t = term.lowercase().trim()
            if (t.isEmpty()) return@forEach
            var from = 0
            while (true) {
                val idx = lower.indexOf(t, from)
                if (idx < 0) break
                val end = idx + t.length
                val leftOk = idx == 0 || !plain[idx - 1].isLetterOrDigit()
                val rightOk = end >= plain.length || !plain[end].isLetterOrDigit()
                if (leftOk && rightOk && (idx until end).none { taken[it] }) {
                    addStyle(SpanStyle(color = FlameHot, textDecoration = TextDecoration.Underline), idx, end)
                    addStringAnnotation(GLOSS_TAG, term, idx, end)
                    for (k in idx until end) taken[k] = true
                }
                from = end
            }
        }
    }
}

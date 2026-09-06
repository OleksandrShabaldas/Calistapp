package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.GlowIcon
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
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Coral.copy(alpha = 0.12f))
                            .clickable { confirmRegen = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null, tint = Coral, modifier = Modifier.size(15.dp))
                        Text(
                            "Regenerate",
                            style = MaterialTheme.typography.labelLarge,
                            color = Coral,
                            fontWeight = FontWeight.SemiBold,
                        )
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

private sealed interface Block {
    data class Heading(val n: String?, val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Para(val text: String) : Block
}

/**
 * A light parser for the coach's reply — numbered section titles become headings, dash/•/star lines
 * become bullets, everything else a paragraph — so the analysis reads as a structured brief rather
 * than a wall of text. Inline **bold** is honoured; the markers themselves are stripped.
 */
@Composable
private fun CoachBody(text: String) {
    val blocks = remember(text) { parseCoach(text) }
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
                    Text(mdInline(block.text), style = MaterialTheme.typography.bodyMedium, color = Chalk)
                }
                is Block.Para -> Text(mdInline(block.text), style = MaterialTheme.typography.bodyMedium, color = Ash)
            }
        }
    }
}

private val HEADING = Regex("""^\s*(\d+)[.)]\s+(.*)""")
private val MD_HEADING = Regex("""^\s*#{1,4}\s+(.*)""")
private val BULLET = Regex("""^\s*[-*•]\s+(.*)""")
private val BOLD_ONLY = Regex("""^\s*\*\*(.+?)\*\*[:：]?\s*$""")

private fun parseCoach(text: String): List<Block> = buildList {
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isBlank()) continue
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

/** Build an [AnnotatedString] that renders `**bold**` spans bold and drops the markers. */
private fun mdInline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val open = s.indexOf("**", i)
        if (open < 0) { append(s.substring(i)); break }
        append(s.substring(i, open))
        val close = s.indexOf("**", open + 2)
        if (close < 0) { append(s.substring(open)); break }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Chalk)) { append(s.substring(open + 2, close)) }
        i = close + 2
    }
}

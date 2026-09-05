package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.dashboard.CardBorder
import com.calistapp.app.ui.dashboard.CardSurface
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Sky
import com.calistapp.app.ui.theme.Violet

/** Shared visual language for the summary screen — the flat onyx card, the tracked eyebrow, and the
 *  colour assignments that make the stat rings and the exercise list agree. */

internal val SummaryCardShape = RoundedCornerShape(22.dp)

/**
 * The flat card that replaces the retired glass surface: a near-black fill with a hairline border, no
 * translucency. Optionally tappable — the whole card lifts to a click, per the convention that info
 * which looks like it should react to a tap does.
 */
@Composable
internal fun FlatCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(SummaryCardShape)
        .background(CardSurface)
        .border(1.dp, CardBorder, SummaryCardShape)
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The small uppercase section label. Archivo tracked out wide — the app carries no monospace face,
 *  and tracked Archivo reads as the same "technical caption" without bundling one. */
@Composable
internal fun Eyebrow(text: String, color: Color = Ash, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/**
 * Perceived-exertion colour: green (easy) climbing through yellow and orange to red (maximal). A
 * deliberate exception to the onyx/orange palette — a difficulty scale reads as a traffic-light ramp
 * to everyone, so this one widget borrows that convention rather than forcing orange to mean "easy".
 */
internal fun difficultyColor(level: Int): Color {
    val green = Color(0xFF54C878)
    val t = ((level - 1) / 9f).coerceIn(0f, 1f)
    return when {
        t < 0.34f -> lerp(green, Amber, t / 0.34f)
        t < 0.67f -> lerp(Amber, Flame, (t - 0.34f) / 0.33f)
        else -> lerp(Flame, Coral, (t - 0.67f) / 0.33f)
    }
}

/** Warm categorical ramp, one colour per exercise — shared by the reps/exercises rings and the list
 *  dots so a colour means the same movement in all three. */
private val exercisePalette = listOf(
    FlameHot,
    FlameGlow,
    Color(0xFFFFAB5C),
    Color(0xFFC96A4A),
    Amber,
    Sky,
    Violet,
)

internal fun exerciseColor(index: Int): Color = exercisePalette[index.mod(exercisePalette.size)]

/** The muted fill of a skipped exercise — present but greyed, in the ring and the list alike. */
internal val SkippedColor = Color(0x22FFFFFF)

/** For a skipped row's text. */
internal val SkippedText = Ash.copy(alpha = 0.7f)

internal val ChalkText = Chalk

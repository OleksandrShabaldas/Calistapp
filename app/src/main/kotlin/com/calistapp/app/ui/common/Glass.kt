package com.calistapp.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.CardShape
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.GlassBorder
import com.calistapp.app.ui.theme.GlassFill
import com.calistapp.app.ui.theme.GlassFillStrong

/**
 * The app's one card.
 *
 * Barely-tinted white over a hairline border, so the screen's ambient wash reads straight through
 * it. That translucency is the point — an opaque panel on a dark background is what made the old
 * layout look like stacked grey boxes.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                // A faint top-down sheen gives the glass a light source instead of a flat fill.
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.025f)),
                ),
            )
            .then(
                if (accent != null) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .border(BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: GlassBorder), shape)
            .then(clickable),
    ) {
        Column(
            Modifier.padding(contentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** A full-width capsule row — the list primitive used for meals, exercises, settings entries. */
@Composable
fun GlassRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    accent: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .clip(Capsule)
            .background(if (selected) GlassFillStrong else GlassFill)
            .border(BorderStroke(1.dp, accent?.copy(alpha = 0.4f) ?: GlassBorder), Capsule)
            .then(clickable)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Section heading in the display serif, with an optional count in the accent colour — the pattern
 * that gives long scrolling screens a spine.
 */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Cream)
        if (count != null) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Box(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Small capsule label — filters, tags, metadata. */
@Composable
fun PillChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier
            .clip(Capsule)
            .background(if (selected) accent.copy(alpha = 0.18f) else GlassFill)
            .border(BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.55f) else GlassBorder), Capsule)
            .then(clickable)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else CreamMuted,
        )
    }
}

/** Two-up segmented switch, as used for Week / 30 days in the reference. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Capsule)
            .background(GlassFill)
            .border(BorderStroke(1.dp, GlassBorder), Capsule)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(Capsule)
                    .background(if (active) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) accent else CreamMuted,
                )
            }
        }
    }
}

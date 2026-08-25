package com.calistapp.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Ash

/**
 * A titled surface used to group content across screens.
 *
 * Now a thin wrapper over [GlassCard] so every screen picks up the translucent treatment without
 * being touched individually.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    GlassCard(modifier = modifier, accent = accent) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
        }
        content()
    }
}

/** A single labelled metric (e.g. "Calories · 342 kcal"). */
@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = Ash)
    }
}

@Composable
fun StatRow(vararg tiles: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        tiles.forEach { tile ->
            Column(verticalArrangement = Arrangement.Center) { tile() }
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Chalk)
    }
}

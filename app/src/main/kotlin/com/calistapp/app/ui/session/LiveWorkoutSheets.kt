package com.calistapp.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.NumberPadSheet
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.formatKg

/**
 * Effort entry — the shared numpad with the three scales as tabs and the `?` explainer wired to the
 * selected scale's blurb. Returns the chosen scale and value together.
 */
@Composable
fun EffortInputSheet(
    initialScale: EffortScale?,
    initialValue: Int?,
    onConfirm: (EffortScale, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scales = EffortScale.entries
    var tab by remember { mutableStateOf(initialScale?.ordinal ?: 0) }
    NumberPadSheet(
        title = "Effort",
        initial = initialValue ?: 0,
        tabs = scales.map { it.label },
        selectedTab = tab,
        onSelectTab = { tab = it },
        help = scales[tab].blurb,
        onConfirm = { v -> onConfirm(scales[tab], v) },
        onDismiss = onDismiss,
    )
}

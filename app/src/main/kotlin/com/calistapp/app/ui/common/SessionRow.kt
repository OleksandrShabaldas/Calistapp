package com.calistapp.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamFaint
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.core.model.SessionOverview

@Composable
fun SessionRow(session: SessionOverview, onClick: () -> Unit) {
    GlassRow(onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                session.title,
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
            )
            Text(
                formatDate(session.startMs),
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${session.totalKcal} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Emerald,
            )
            Text(
                if (session.totalReps > 0) {
                    "${session.totalReps} reps · ${session.avgHr} bpm"
                } else {
                    "avg ${session.avgHr} bpm"
                },
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CreamFaint,
        )
    }
}

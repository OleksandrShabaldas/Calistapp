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
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Flame
import com.calistapp.core.model.SessionOverview

@Composable
fun SessionRow(session: SessionOverview, onClick: () -> Unit) {
    GlassRow(onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                session.title,
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
            )
            Text(
                formatDate(session.startMs),
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${session.totalKcal} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Flame,
            )
            Text(
                if (session.totalReps > 0) {
                    "${session.totalReps} reps · ${session.avgHr} bpm"
                } else {
                    "avg ${session.avgHr} bpm"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AshFaint,
        )
    }
}

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
import com.calistapp.core.model.WorkoutSession

@Composable
fun SessionRow(session: WorkoutSession, onClick: () -> Unit) {
    val summary = session.summary
    GlassRow(onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                session.exerciseName ?: session.exerciseType.displayName,
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
                "${summary?.totalKcal?.toInt() ?: 0} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Emerald,
            )
            val reps = summary?.totalReps ?: 0
            Text(
                if (reps > 0) "$reps reps · ${summary?.avgHr ?: 0} bpm" else "avg ${summary?.avgHr ?: 0} bpm",
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

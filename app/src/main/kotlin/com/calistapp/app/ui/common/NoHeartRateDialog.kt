package com.calistapp.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.calistapp.app.ui.theme.Coral

/**
 * Asks before recording a workout that can't produce a calorie figure.
 *
 * Shared by every screen that can start a session, so the warning can't be attached to one entry
 * point and quietly missing from another — which is exactly what would have happened when the
 * planner became the main way in.
 */
@Composable
fun NoHeartRateDialog(
    onDismiss: () -> Unit,
    onStartAnyway: () -> Unit,
    onReconnect: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No heart rate from your watch") },
        text = {
            Text(
                "Calistapp measures calories from heart rate, and nothing is arriving. Started now, " +
                    "this workout's calories are estimated from your sets instead — and filled in later " +
                    "if a ring or tracker recorded heart rate to Health Connect.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onStartAnyway() }) {
                Text("Start anyway", color = Coral)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(); onReconnect() }) { Text("Reconnect") }
        },
    )
}

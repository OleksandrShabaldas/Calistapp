package com.calistapp.app.ui.detail

import android.graphics.Picture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.formatDate
import com.calistapp.app.ui.exercises.sharePicture
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.progress.PersonalRecord
import kotlinx.coroutines.launch

/**
 * Share a finished session as an image: an onyx/orange summary card — calories, the headline stats,
 * and any personal bests — drawn from text and vectors (never the HR trace or a network frame) so the
 * capture is deterministic. Same record-to-Picture-to-PNG path as the exercise share card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionShareSheet(
    session: WorkoutSession,
    summary: SessionSummary,
    records: List<PersonalRecord>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picture = remember { Picture() }
    var sharing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OnyxRaised) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Share workout",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Chalk,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(18.dp))
                    .drawWithCache {
                        val w = size.width.toInt()
                        val h = size.height.toInt()
                        onDrawWithContent {
                            val pictureCanvas = Canvas(picture.beginRecording(w, h))
                            draw(this, layoutDirection, pictureCanvas, size) {
                                this@onDrawWithContent.drawContent()
                            }
                            picture.endRecording()
                            drawIntoCanvas { it.nativeCanvas.drawPicture(picture) }
                        }
                    },
            ) {
                SessionCardContent(session, summary, records)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(Capsule)
                    .background(Flame)
                    .clickable(enabled = !sharing) {
                        sharing = true
                        scope.launch {
                            sharePicture(context, picture, session.exerciseType.displayName)
                            sharing = false
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Share, null, tint = Onyx, modifier = Modifier.size(18.dp))
                    Text(
                        if (sharing) "Preparing…" else "Share image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Onyx,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCardContent(session: WorkoutSession, s: SessionSummary, records: List<PersonalRecord>) {
    Column(
        Modifier.fillMaxSize().background(Onyx).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                session.exerciseType.displayName.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Flame,
                fontWeight = FontWeight.Bold,
            )
            Text(formatDate(session.startMs), style = MaterialTheme.typography.labelMedium, color = Ash)
        }

        Column {
            Text("${s.totalKcal.toInt()}", style = NumericLarge, color = Chalk)
            Text("kcal burned", style = MaterialTheme.typography.titleMedium, color = Ash)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShareStat("Time", formatClock(s.totalDurationMs), Chalk)
            if (s.peakHr > 0) ShareStat("Peak HR", "${s.peakHr}", Coral)
            if (s.totalReps > 0) ShareStat("Reps", "${s.totalReps}", Flame)
        }

        if (records.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NEW PERSONAL BEST", style = MaterialTheme.typography.labelMedium, color = Amber, fontWeight = FontWeight.Bold)
                records.take(2).forEach { r ->
                    Text(
                        "${r.exerciseName} · ${r.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chalk,
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Flame))
            Text("Calistapp", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Chalk)
        }
    }
}

@Composable
private fun ShareStat(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

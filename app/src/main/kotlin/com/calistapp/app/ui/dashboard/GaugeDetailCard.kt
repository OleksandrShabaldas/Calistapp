package com.calistapp.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calistapp.app.data.recommend.RecFactor
import com.calistapp.app.data.recommend.Recommendation
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.FlameHot

private val MedallionSize = 96.dp
private val MedallionRadius = 48.dp

/**
 * The detail that opens when a recommendation gauge is tapped: the gauge as a medallion on top, the
 * card body flowing from its centre (see the user's sketch), the weighed inputs as bars, and the AI's
 * reasoning below. Scales and fades in over a dimmed backdrop.
 */
@Composable
fun GaugeDetailOverlay(
    kind: GaugeKind?,
    rec: Recommendation?,
    onDismiss: () -> Unit,
) {
    if (kind == null || rec == null) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var appear by remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { appear = true }
        val scrim by androidx.compose.animation.animateColorAsState(
            if (appear) Color.Black.copy(alpha = 0.62f) else Color.Transparent,
            tween(200),
            label = "scrim",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = appear,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
            ) {
                GaugeDetailContent(kind, rec)
            }
        }
    }
}

@Composable
private fun GaugeDetailContent(kind: GaugeKind, rec: Recommendation) {
    val readiness = kind == GaugeKind.READINESS
    val accent = if (readiness) readinessColor(rec.readiness.score) else FlameHot
    val progress = if (readiness) rec.readiness.score / 100f else conditionsFill(rec.conditions.label)
    val top = if (readiness) shortReadiness(rec.readiness.score) else rec.conditions.label
    val bottom = if (readiness) "${rec.readiness.score}%" else rec.conditions.detail
    val headline = if (readiness) "Should you train today?" else "Indoors or out?"
    val factors = if (readiness) rec.readiness.factors else rec.conditions.factors
    val reason = if (readiness) rec.readiness.reason else rec.conditions.reason

    Box(
        Modifier.padding(horizontal = 28.dp).widthIn(max = 400.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = MedallionRadius)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
                // Swallow taps so clicking the card doesn't dismiss.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp, top = MedallionRadius + 20.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleLarge, color = Chalk, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            factors.forEach { factor ->
                FactorBar(factor, accent)
                Spacer(Modifier.height(14.dp))
            }
            if (reason.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("WHY", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = Ash)
            }
        }

        // The medallion, centred over the card's top edge.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(MedallionSize)
                .clip(CircleShape)
                .background(CardSurface)
                .border(1.dp, CardBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(progress = progress, accent = accent, diameter = MedallionSize, strokeWidth = 8.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(top, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(bottom, style = MaterialTheme.typography.labelMedium, color = Chalk)
                }
            }
        }
    }
}

@Composable
private fun FactorBar(factor: RecFactor, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(factor.label, style = MaterialTheme.typography.bodyMedium, color = Ash)
            Text(factor.value, style = MaterialTheme.typography.labelLarge, color = Chalk, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(factor.progress.coerceIn(0f, 1f)).height(7.dp).clip(CircleShape).background(color))
        }
    }
}

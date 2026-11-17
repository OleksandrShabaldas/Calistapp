package com.calistapp.app.ui.medals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.pressScale
import com.calistapp.app.ui.common.rememberInteraction
import com.calistapp.app.ui.common.rememberReduceMotion
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Body
import com.calistapp.app.ui.theme.Chalk

/**
 * The medals this session struck, on the summary. During the unlock moment a slot whose medal hasn't
 * arrived yet is an empty socket ([waiting]); when the medal lands the socket pops once. Each slot
 * reports where it is ([onBounds]) so the unlock can fly the medal into it, and opens big on a tap.
 */
@Composable
fun EarnedSection(
    medals: List<StruckMedal>,
    waiting: Set<String>,
    onBounds: (String, Rect) -> Unit,
    onOpen: (StruckMedal, Rect?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        medals.forEach { m -> EarnedSlot(m, waiting = m.award.key in waiting, onBounds = onBounds, onOpen = onOpen) }
    }
}

@Composable
private fun EarnedSlot(m: StruckMedal, waiting: Boolean, onBounds: (String, Rect) -> Unit, onOpen: (StruckMedal, Rect?) -> Unit) {
    val reduce = rememberReduceMotion()
    val pop = remember { Animatable(1f) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var wasWaiting by remember { mutableStateOf(waiting) }
    LaunchedEffect(waiting) {
        // The socket takes the medal with one small pop; nothing when it was never empty.
        if (wasWaiting && !waiting && !reduce) {
            pop.animateTo(1f, keyframes { durationMillis = 260; 1.1f at 90 using FastOutSlowInEasing })
        }
        wasWaiting = waiting
    }
    val interaction = rememberInteraction()
    Column(
        Modifier
            .width(76.dp)
            .pressScale(interaction)
            .clickable(interaction, indication = null, enabled = !waiting) { onOpen(m, bounds) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(64.dp)
                .onGloballyPositioned { c -> c.boundsInRoot().also { bounds = it; onBounds(m.award.key, it) } }
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        ) {
            if (waiting) {
                // An empty socket where the disc will sit (the disc is r 36 at (50, 78) of 100 × 118).
                Canvas(Modifier.fillMaxWidth().aspectRatio(100f / 118f)) {
                    val k = size.width / 100f
                    drawCircle(
                        Color.White.copy(alpha = 0.18f), 36f * k, Offset(50f * k, 78f * k),
                        style = Stroke(1.5f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 4f * k))),
                    )
                }
            } else {
                Medal(m.definition, m.award.tierIndex, MedalLook.STRUCK)
            }
        }
        Text(
            m.definition.titleFor(m.award.tierIndex),
            style = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 14.sp, hyphens = Hyphens.Auto),
            color = if (waiting) Ash else Chalk,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            m.award.valueLine(),
            style = TextStyle(fontFamily = Body, fontSize = 10.5.sp, lineHeight = 13.sp),
            color = Ash,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

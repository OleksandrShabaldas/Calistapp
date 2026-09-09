package com.calistapp.app.ui.exercises

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Flame

import kotlin.math.min

/**
 * A real anatomical muscle map. The figure geometry is MuscleMap's (MIT — Melih Colpan, see
 * [MuscleMapData]): front and back bodies drawn from per-muscle SVG paths. The whole musculature is
 * drawn faintly so it reads as a body, and the exercise's primary muscles are lit in the accent with
 * secondary ones dimmer. `free-exercise-db`'s coarse muscle names are mapped onto MuscleMap's richer
 * groups; nothing else on the screen needs to change — the public [MuscleDiagram] signature is the same.
 */

private val BODY = Color(0xFF2A2A31)          // cosmetic parts (head/hair, hands, feet, joints)
private val MUSCLE_IDLE = Color(0xFF3B3B45)   // an un-worked muscle
private val PRIMARY = Flame
private val SECONDARY = Flame.copy(alpha = 0.5f)

/** The coarse group a drawn slug belongs to, for highlighting. "cosmetic" is never lit. */
private fun slugGroup(slug: String): String = when (slug) {
    "chest", "upperChest", "lowerChest" -> "chest"
    "abs", "upperAbs", "lowerAbs" -> "abs"
    "obliques", "serratus" -> "obliques"
    "deltoids", "frontDeltoid", "rearDeltoid" -> "shoulders"
    "biceps" -> "biceps"
    "triceps" -> "triceps"
    "forearm" -> "forearm"
    "upperBack", "rhomboids" -> "lats"
    "trapezius", "upperTrapezius", "lowerTrapezius" -> "traps"
    "lowerBack" -> "lowerBack"
    "quadriceps", "innerQuad", "outerQuad", "hipFlexors" -> "quads"
    "hamstring" -> "hamstring"
    "gluteal" -> "gluteal"
    "calves" -> "calves"
    "adductors" -> "adductors"
    "tibialis" -> "tibialis"
    "neck" -> "neck"
    else -> "cosmetic"
}

/** A free-exercise-db muscle name → the group key(s) it lights. */
private fun muscleGroups(name: String): Set<String> = when (name.trim().lowercase()) {
    "chest" -> setOf("chest")
    "shoulders" -> setOf("shoulders")
    "triceps" -> setOf("triceps")
    "biceps" -> setOf("biceps")
    "forearms" -> setOf("forearm")
    "lats" -> setOf("lats")
    "middle back" -> setOf("lats", "traps")
    "lower back" -> setOf("lowerBack")
    "traps" -> setOf("traps")
    "neck" -> setOf("neck")
    "abdominals" -> setOf("abs", "obliques")
    "quadriceps" -> setOf("quads")
    "hamstrings" -> setOf("hamstring")
    "glutes" -> setOf("gluteal")
    "calves" -> setOf("calves", "tibialis")
    "abductors" -> setOf("gluteal")
    "adductors" -> setOf("adductors")
    else -> emptySet()
}

private fun groupsOf(names: List<String>): Set<String> = names.flatMapTo(mutableSetOf()) { muscleGroups(it) }

/** A body part ready to draw: the group it highlights with, and its parsed paths. */
private class DrawPart(val group: String, val paths: List<Path>)

private fun buildParts(src: List<MuscleMapPart>): List<DrawPart> =
    src.map { part -> DrawPart(slugGroup(part.slug), part.paths.map { PathParser().parsePathString(it).toPath() }) }

@Composable
fun MuscleDiagram(
    primaryMuscles: List<String>,
    secondaryMuscles: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = remember(primaryMuscles) { groupsOf(primaryMuscles) }
    val secondary = remember(secondaryMuscles) { groupsOf(secondaryMuscles) - primary }
    val front = remember { buildParts(MuscleMapData.maleFront) }
    val back = remember { buildParts(MuscleMapData.maleBack) }

    Row(modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyFigure(front, MuscleMapData.VIEW_ORIGIN_X_FRONT, primary, secondary, Modifier.weight(1f))
        BodyFigure(back, MuscleMapData.VIEW_ORIGIN_X_BACK, primary, secondary, Modifier.weight(1f))
    }
}

@Composable
private fun BodyFigure(
    parts: List<DrawPart>,
    originX: Float,
    primary: Set<String>,
    secondary: Set<String>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(300.dp)) {
        val s = min(size.width / MuscleMapData.VIEW_W, size.height / MuscleMapData.VIEW_H)
        val padX = (size.width - MuscleMapData.VIEW_W * s) / 2f
        val padY = (size.height - MuscleMapData.VIEW_H * s) / 2f
        // screen = pad + s * (point - viewOrigin), so the viewBox fills the canvas, centred.
        withTransform({
            translate(padX, padY)
            scale(s, s, pivot = Offset.Zero)
            translate(-originX, -MuscleMapData.VIEW_ORIGIN_Y)
        }) {
            parts.forEach { part ->
                val color = when {
                    part.group in primary -> PRIMARY
                    part.group in secondary -> SECONDARY
                    part.group == "cosmetic" -> BODY
                    else -> MUSCLE_IDLE
                }
                part.paths.forEach { drawPath(it, color) }
            }
        }
    }
}

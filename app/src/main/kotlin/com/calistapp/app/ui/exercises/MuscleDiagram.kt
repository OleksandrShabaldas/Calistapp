package com.calistapp.app.ui.exercises

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Flame

/**
 * The highlightable muscle regions of the schematic body. Coarser than a medical chart on purpose —
 * free-exercise-db's muscle vocabulary is coarse ("Chest", not "Upper/Lower chest"), so the diagram
 * resolves to muscle groups. A licensed anatomical SVG can be swapped in later behind this same
 * [MuscleDiagram] interface; nothing else needs to change.
 */
enum class MuscleRegion {
    // Front
    CHEST, ABS, FRONT_DELTS, BICEPS, FOREARMS, QUADS, ADDUCTORS,
    // Back
    TRAPS, REAR_DELTS, LATS, LOWER_BACK, TRICEPS, GLUTES, HAMSTRINGS, CALVES,
    // Both
    NECK,
}

/** The one-time map: free-exercise-db muscle name → the region(s) it lights up. */
private val MUSCLE_MAP: Map<String, Set<MuscleRegion>> = mapOf(
    "chest" to setOf(MuscleRegion.CHEST),
    "shoulders" to setOf(MuscleRegion.FRONT_DELTS, MuscleRegion.REAR_DELTS),
    "triceps" to setOf(MuscleRegion.TRICEPS),
    "biceps" to setOf(MuscleRegion.BICEPS),
    "forearms" to setOf(MuscleRegion.FOREARMS),
    "lats" to setOf(MuscleRegion.LATS),
    "middle back" to setOf(MuscleRegion.LATS, MuscleRegion.TRAPS),
    "lower back" to setOf(MuscleRegion.LOWER_BACK),
    "traps" to setOf(MuscleRegion.TRAPS),
    "neck" to setOf(MuscleRegion.NECK),
    "abdominals" to setOf(MuscleRegion.ABS),
    "quadriceps" to setOf(MuscleRegion.QUADS),
    "hamstrings" to setOf(MuscleRegion.HAMSTRINGS),
    "glutes" to setOf(MuscleRegion.GLUTES),
    "calves" to setOf(MuscleRegion.CALVES),
    "abductors" to setOf(MuscleRegion.GLUTES),
    "adductors" to setOf(MuscleRegion.ADDUCTORS),
)

fun musclesToRegions(names: List<String>): Set<MuscleRegion> =
    names.flatMap { MUSCLE_MAP[it.trim().lowercase()].orEmpty() }.toSet()

private val BASE = Color(0xFF2A2A2E)
private val BASE_HI = Color(0xFF34343A)
private val PRIMARY = Flame
private val SECONDARY = Flame.copy(alpha = 0.5f)

/**
 * Front and back schematic bodies with the worked muscles lit — primary bright, secondary dim.
 * Coordinates are authored in a 0..100 × 0..200 space and scaled to the canvas.
 */
@Composable
fun MuscleDiagram(
    primaryMuscles: List<String>,
    secondaryMuscles: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = remember(primaryMuscles) { musclesToRegions(primaryMuscles) }
    val secondary = remember(secondaryMuscles) { musclesToRegions(secondaryMuscles) - primary }

    fun colorFor(r: MuscleRegion): Color = when {
        r in primary -> PRIMARY
        r in secondary -> SECONDARY
        else -> BASE_HI
    }

    Row(modifier.fillMaxWidth().height(196.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.weight(1f).fillMaxWidth().height(196.dp)) { drawFront(::colorFor) }
        Canvas(Modifier.weight(1f).fillMaxWidth().height(196.dp)) { drawBack(::colorFor) }
    }
}

private fun DrawScope.blob(x0: Float, y0: Float, x1: Float, y1: Float, color: Color, rad: Float = 3.5f) {
    val sx = size.width / 100f
    val sy = size.height / 200f
    drawRoundRect(
        color = color,
        topLeft = Offset(x0 * sx, y0 * sy),
        size = Size((x1 - x0) * sx, (y1 - y0) * sy),
        cornerRadius = CornerRadius(rad * sx, rad * sx),
    )
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    val sx = size.width / 100f
    val sy = size.height / 200f
    drawOval(
        color = color,
        topLeft = Offset((cx - rx) * sx, (cy - ry) * sy),
        size = Size(rx * 2 * sx, ry * 2 * sy),
    )
}

private fun DrawScope.skeleton() {
    oval(50f, 15f, 11f, 12f, BASE)      // head
    blob(44f, 25f, 56f, 33f, BASE)      // neck
    blob(30f, 32f, 70f, 92f, BASE, rad = 10f) // torso
    blob(17f, 36f, 29f, 100f, BASE, rad = 6f) // left arm
    blob(71f, 36f, 83f, 100f, BASE, rad = 6f) // right arm
    blob(38f, 90f, 62f, 108f, BASE, rad = 6f) // pelvis
    blob(37f, 106f, 49f, 192f, BASE, rad = 6f) // left leg
    blob(51f, 106f, 63f, 192f, BASE, rad = 6f) // right leg
}

private fun DrawScope.drawFront(colorFor: (MuscleRegion) -> Color) {
    skeleton()
    blob(45f, 26f, 55f, 33f, colorFor(MuscleRegion.NECK))
    oval(31f, 40f, 8f, 7f, colorFor(MuscleRegion.FRONT_DELTS))
    oval(69f, 40f, 8f, 7f, colorFor(MuscleRegion.FRONT_DELTS))
    blob(33f, 36f, 49f, 53f, colorFor(MuscleRegion.CHEST))   // left pec
    blob(51f, 36f, 67f, 53f, colorFor(MuscleRegion.CHEST))   // right pec
    blob(42f, 55f, 58f, 88f, colorFor(MuscleRegion.ABS))     // abs
    blob(19f, 42f, 28f, 70f, colorFor(MuscleRegion.BICEPS))
    blob(72f, 42f, 81f, 70f, colorFor(MuscleRegion.BICEPS))
    blob(18f, 72f, 27f, 99f, colorFor(MuscleRegion.FOREARMS))
    blob(73f, 72f, 82f, 99f, colorFor(MuscleRegion.FOREARMS))
    blob(38f, 108f, 49f, 150f, colorFor(MuscleRegion.QUADS))
    blob(51f, 108f, 62f, 150f, colorFor(MuscleRegion.QUADS))
    blob(47f, 108f, 53f, 144f, colorFor(MuscleRegion.ADDUCTORS))
}

private fun DrawScope.drawBack(colorFor: (MuscleRegion) -> Color) {
    skeleton()
    blob(45f, 26f, 55f, 33f, colorFor(MuscleRegion.NECK))
    blob(37f, 33f, 63f, 50f, colorFor(MuscleRegion.TRAPS))
    oval(31f, 41f, 8f, 7f, colorFor(MuscleRegion.REAR_DELTS))
    oval(69f, 41f, 8f, 7f, colorFor(MuscleRegion.REAR_DELTS))
    blob(34f, 50f, 49f, 74f, colorFor(MuscleRegion.LATS))
    blob(51f, 50f, 66f, 74f, colorFor(MuscleRegion.LATS))
    blob(42f, 74f, 58f, 90f, colorFor(MuscleRegion.LOWER_BACK))
    blob(19f, 42f, 28f, 71f, colorFor(MuscleRegion.TRICEPS))
    blob(72f, 42f, 81f, 71f, colorFor(MuscleRegion.TRICEPS))
    blob(38f, 92f, 49f, 112f, colorFor(MuscleRegion.GLUTES))
    blob(51f, 92f, 62f, 112f, colorFor(MuscleRegion.GLUTES))
    blob(38f, 114f, 49f, 150f, colorFor(MuscleRegion.HAMSTRINGS))
    blob(51f, 114f, 62f, 150f, colorFor(MuscleRegion.HAMSTRINGS))
    blob(38f, 154f, 49f, 186f, colorFor(MuscleRegion.CALVES))
    blob(51f, 154f, 62f, 186f, colorFor(MuscleRegion.CALVES))
}

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    CHEST, ABS, OBLIQUES, FRONT_DELTS, BICEPS, FOREARMS, QUADS, ADDUCTORS,
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
    "abdominals" to setOf(MuscleRegion.ABS, MuscleRegion.OBLIQUES),
    "quadriceps" to setOf(MuscleRegion.QUADS),
    "hamstrings" to setOf(MuscleRegion.HAMSTRINGS),
    "glutes" to setOf(MuscleRegion.GLUTES),
    "calves" to setOf(MuscleRegion.CALVES),
    "abductors" to setOf(MuscleRegion.GLUTES),
    "adductors" to setOf(MuscleRegion.ADDUCTORS),
)

fun musclesToRegions(names: List<String>): Set<MuscleRegion> =
    names.flatMap { MUSCLE_MAP[it.trim().lowercase()].orEmpty() }.toSet()

// Body fill sits a step above the card; unworked muscle a step above that; worked muscle in the
// accent. The whole musculature is drawn faintly so it reads as a body, with the exercise's muscles
// lit orange on top. A hairline separates bellies so they read as distinct muscles, not one slab.
private val BODY = Color(0xFF24242B)
private val MUSCLE_IDLE = Color(0xFF33333C)
private val SEPARATOR = Color(0x22000000)
private val PRIMARY = Flame
private val SECONDARY = Flame.copy(alpha = 0.5f)

/**
 * Front and back schematic bodies with the worked muscles lit — primary bright, secondary dim, the
 * rest of the musculature drawn faintly so it reads as a whole figure. Built from smooth rounded
 * bellies (ellipses + soft silhouette), not boxes. Coordinates are authored in a 0..100 × 0..212
 * space and scaled to the canvas.
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
        else -> MUSCLE_IDLE
    }

    Row(modifier.fillMaxWidth().height(224.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.weight(1f).fillMaxWidth().height(224.dp)) { drawFront(::colorFor) }
        Canvas(Modifier.weight(1f).fillMaxWidth().height(224.dp)) { drawBack(::colorFor) }
    }
}

// ---- scaled drawing helpers (author in 0..100 × 0..212) ---------------------------------------

private fun DrawScope.px(v: Float): Float = v / 100f * size.width
private fun DrawScope.py(v: Float): Float = v / 212f * size.height

/** A smooth belly. [ry]≈[rx] gives a round muscle; a long [ry] a limb/strap. */
private fun DrawScope.belly(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, outline: Boolean = true) {
    val tl = Offset(px(cx - rx), py(cy - ry))
    val sz = Size(px(rx * 2), py(ry * 2))
    drawOval(color = color, topLeft = tl, size = sz)
    if (outline) drawOval(color = SEPARATOR, topLeft = tl, size = sz, style = Stroke(width = px(0.6f)))
}

/** A rounded block — the ab segments, where a soft rectangle reads better than an oval. */
private fun DrawScope.slab(x0: Float, y0: Float, x1: Float, y1: Float, color: Color, r: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(px(x0), py(y0)),
        size = Size(px(x1 - x0), py(y1 - y0)),
        cornerRadius = CornerRadius(px(r), px(r)),
    )
}

private fun DrawScope.poly(color: Color, vararg pts: Float) {
    val p = Path()
    var i = 0
    while (i < pts.size) {
        val x = px(pts[i]); val y = py(pts[i + 1])
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        i += 2
    }
    p.close()
    drawPath(p, color)
}

/** The shared body silhouette both views sit on — soft rounded torso, capsule limbs. */
private fun DrawScope.bodyBase() {
    belly(50f, 14f, 9.5f, 11f, BODY, outline = false)          // head
    slab(45f, 23f, 55f, 34f, BODY, 5f)                          // neck
    // Torso: shoulders → waist, generously rounded so it reads soft, not boxy.
    slab(30f, 37f, 70f, 74f, BODY, 15f)                         // chest/upper torso
    slab(37f, 68f, 63f, 100f, BODY, 12f)                        // abdomen
    slab(37f, 96f, 63f, 116f, BODY, 11f)                        // pelvis
    belly(25f, 56f, 6f, 19f, BODY, outline = false)             // left upper arm
    belly(75f, 56f, 6f, 19f, BODY, outline = false)             // right upper arm
    belly(23f, 88f, 5f, 18f, BODY, outline = false)             // left forearm
    belly(77f, 88f, 5f, 18f, BODY, outline = false)             // right forearm
    belly(44f, 138f, 8.5f, 30f, BODY, outline = false)          // left thigh
    belly(56f, 138f, 8.5f, 30f, BODY, outline = false)          // right thigh
    belly(44f, 184f, 6.5f, 24f, BODY, outline = false)          // left lower leg
    belly(56f, 184f, 6.5f, 24f, BODY, outline = false)          // right lower leg
}

private fun DrawScope.drawFront(colorFor: (MuscleRegion) -> Color) {
    bodyBase()
    slab(45f, 24f, 55f, 33f, colorFor(MuscleRegion.NECK), 4f)
    belly(30f, 42f, 8f, 7f, colorFor(MuscleRegion.FRONT_DELTS))
    belly(70f, 42f, 8f, 7f, colorFor(MuscleRegion.FRONT_DELTS))
    // Pectorals — two round bellies meeting at the sternum.
    belly(43f, 48f, 9f, 8f, colorFor(MuscleRegion.CHEST))
    belly(57f, 48f, 9f, 8f, colorFor(MuscleRegion.CHEST))
    // Biceps + forearm flexors.
    belly(25f, 56f, 5f, 13f, colorFor(MuscleRegion.BICEPS))
    belly(75f, 56f, 5f, 13f, colorFor(MuscleRegion.BICEPS))
    belly(23f, 88f, 4.5f, 15f, colorFor(MuscleRegion.FOREARMS))
    belly(77f, 88f, 4.5f, 15f, colorFor(MuscleRegion.FOREARMS))
    // Obliques flank the abs.
    belly(40f, 74f, 4f, 12f, colorFor(MuscleRegion.OBLIQUES))
    belly(60f, 74f, 4f, 12f, colorFor(MuscleRegion.OBLIQUES))
    // Rectus abdominis — a real six-pack grid, then the lower band.
    val abs = colorFor(MuscleRegion.ABS)
    for (row in 0..2) {
        val y0 = 61f + row * 8f
        slab(43.6f, y0, 49.4f, y0 + 6.4f, abs, 2.4f)
        slab(50.6f, y0, 56.4f, y0 + 6.4f, abs, 2.4f)
    }
    slab(44.5f, 85f, 55.5f, 92f, abs, 3f)
    // Quadriceps — rectus femoris down the middle with the vastus bellies either side.
    belly(43f, 138f, 6.5f, 26f, colorFor(MuscleRegion.QUADS))
    belly(57f, 138f, 6.5f, 26f, colorFor(MuscleRegion.QUADS))
    belly(48f, 132f, 3.5f, 20f, colorFor(MuscleRegion.ADDUCTORS))
    belly(52f, 132f, 3.5f, 20f, colorFor(MuscleRegion.ADDUCTORS))
    // Shins (tibialis) read as part of the lower-leg group.
    belly(43f, 186f, 4f, 18f, colorFor(MuscleRegion.CALVES))
    belly(57f, 186f, 4f, 18f, colorFor(MuscleRegion.CALVES))
}

private fun DrawScope.drawBack(colorFor: (MuscleRegion) -> Color) {
    bodyBase()
    slab(45f, 24f, 55f, 33f, colorFor(MuscleRegion.NECK), 4f)
    // Trapezius — a kite from the neck out to the shoulders and down between the blades.
    poly(colorFor(MuscleRegion.TRAPS), 50f, 34f, 66f, 41f, 57f, 60f, 50f, 56f, 43f, 60f, 34f, 41f)
    belly(30f, 43f, 8f, 7f, colorFor(MuscleRegion.REAR_DELTS))
    belly(70f, 43f, 8f, 7f, colorFor(MuscleRegion.REAR_DELTS))
    // Lats — wings tapering from the armpits to the waist.
    poly(colorFor(MuscleRegion.LATS), 34f, 54f, 48f, 58f, 47f, 84f, 39f, 82f, 33f, 68f)
    poly(colorFor(MuscleRegion.LATS), 66f, 54f, 52f, 58f, 53f, 84f, 61f, 82f, 67f, 68f)
    // Erector spinae column low in the back.
    slab(46f, 74f, 54f, 96f, colorFor(MuscleRegion.LOWER_BACK), 3f)
    // Triceps + forearm extensors.
    belly(25f, 56f, 5f, 13f, colorFor(MuscleRegion.TRICEPS))
    belly(75f, 56f, 5f, 13f, colorFor(MuscleRegion.TRICEPS))
    belly(23f, 88f, 4.5f, 15f, colorFor(MuscleRegion.FOREARMS))
    belly(77f, 88f, 4.5f, 15f, colorFor(MuscleRegion.FOREARMS))
    // Glutes, then hamstrings and calves.
    belly(44f, 104f, 7.5f, 8f, colorFor(MuscleRegion.GLUTES))
    belly(56f, 104f, 7.5f, 8f, colorFor(MuscleRegion.GLUTES))
    belly(44f, 140f, 6.5f, 24f, colorFor(MuscleRegion.HAMSTRINGS))
    belly(56f, 140f, 6.5f, 24f, colorFor(MuscleRegion.HAMSTRINGS))
    belly(44f, 184f, 5f, 20f, colorFor(MuscleRegion.CALVES))
    belly(56f, 184f, 5f, 20f, colorFor(MuscleRegion.CALVES))
}

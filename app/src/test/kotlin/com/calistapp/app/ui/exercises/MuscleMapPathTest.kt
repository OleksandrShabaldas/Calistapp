package com.calistapp.app.ui.exercises

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The muscle map's head "cracked" (front) and fell apart (back) because Compose's path parser drops
 * an SVG arc whose flags are written without separators. These pin the parser's behaviour and the fix.
 */
class MuscleMapPathTest {

    private fun arcs(d: String) = PathParser().parsePathString(d).toNodes()
        .count { it is PathNode.ArcTo || it is PathNode.RelativeArcTo }

    @Test
    fun composeDropsCompactArcFlags_soNormalisingIsNeeded() {
        val compact = "M0 0a1.14 1.14 0 01-2.22.23l5 5z"
        // If this ever starts passing as 1, Compose fixed its parser and the normaliser is optional.
        assertEquals(0, arcs(compact))
        assertEquals(1, arcs(SvgPathNormalizer.normalize(compact)))
    }

    @Test
    fun normalisedArcKeepsItsFlagsAndEndpoint() {
        val arc = PathParser().parsePathString(SvgPathNormalizer.normalize("M0 0a910.025 910.025 0 0136.94-.25"))
            .toNodes().filterIsInstance<PathNode.RelativeArcTo>().single()
        assertEquals(910.025f, arc.horizontalEllipseRadius, 0.001f)
        assertEquals(false, arc.isMoreThanHalf)
        assertEquals(true, arc.isPositiveArc)
        assertEquals(36.94f, arc.arcStartDx, 0.001f)
        assertEquals(-0.25f, arc.arcStartDy, 0.001f)
    }

    @Test
    fun numbersRunTogetherSplitLikeSvg() {
        assertEquals("M 1.5 -2.5 .25 c 1e-3 -4", SvgPathNormalizer.normalize("M1.5-2.5.25c1e-3-4"))
    }

    @Test
    fun everyArcInTheMuscleMapSurvivesParsing() {
        val all = MuscleMapData.maleFront + MuscleMapData.maleBack
        var expected = 0
        var parsed = 0
        all.forEach { part ->
            part.paths.forEach { d ->
                expected += d.count { it == 'a' || it == 'A' }
                parsed += arcs(SvgPathNormalizer.normalize(d))
            }
        }
        assertTrue("the map should contain arcs", expected > 0)
        // An arc command can carry several arcs, so there are at least as many arcs as `a` commands.
        assertTrue("$parsed arcs parsed from $expected arc commands", parsed >= expected)
    }
}

/*
 * Unit tests for speed curve editing and the logarithmic speed scale.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.SpeedPoint
import com.framecraft.editor.domain.model.SpeedPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Verifies [SpeedCurveEdit] on the flat custom curve and [SpeedScale] on the slider ranges. */
class SpeedCurveEditTest {
    private val flat = SpeedPreset.customStart

    /** Points move vertically within 0.1x to 10x and nudge by 0.1x. */
    @Test
    fun pointsMoveWithinRange() {
        val raised = SpeedCurveEdit.withSpeed(flat, 2, 50_000)
        assertEquals(10_000, raised.points[2].speedMilli)
        assertEquals(flat.points[2].position, raised.points[2].position)
        assertEquals(100, SpeedCurveEdit.withSpeed(flat, 0, 0).points[0].speedMilli)
        assertEquals(1_100, SpeedCurveEdit.nudged(flat, 1, faster = true).points[1].speedMilli)
        assertEquals(900, SpeedCurveEdit.nudged(flat, 1, faster = false).points[1].speedMilli)
    }

    /** A point added on a line keeps the curve's shape; ends and crowded spots refuse changes. */
    @Test
    fun pointsAddAndDelete() {
        val ramp = ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(100_000, 3_000)))
        val added = SpeedCurveEdit.withPointAt(ramp, 50_000)
        assertEquals(SpeedPoint(50_000, 2_000), added.points[1])
        assertEquals(1, SpeedCurveEdit.pointNear(added, 51_000))
        assertNull(SpeedCurveEdit.pointNear(added, 60_000))
        assertFalse(SpeedCurveEdit.canAdd(added, 51_000))
        assertFalse(SpeedCurveEdit.canDelete(added, 0))
        assertFalse(SpeedCurveEdit.canDelete(added, 2))
        assertEquals(ramp, SpeedCurveEdit.withoutPoint(added, 1))
        assertThrows<IllegalArgumentException> { SpeedCurveEdit.withoutPoint(added, 0) }
        var crowded = flat
        listOf(10_000, 35_000, 60_000, 85_000, 95_000).forEach { crowded = SpeedCurveEdit.withPointAt(crowded, it) }
        assertEquals(SpeedCurveEdit.MAX_EDITOR_POINTS, crowded.points.size)
        assertFalse(SpeedCurveEdit.canAdd(crowded, 40_000))
        assertTrue(SpeedCurveEdit.flattened(SpeedPreset.HERO.curve).points.all { it.speedMilli == 1_000 })
    }

    /** The scale is logarithmic, settles on 1x near it, and rounds to readable steps. */
    @Test
    fun scaleIsLogarithmic() {
        assertEquals(0f, SpeedScale.fractionOf(100, 100, 100_000))
        assertEquals(1f, SpeedScale.fractionOf(100_000, 100, 100_000))
        assertEquals(1f / 3f, SpeedScale.fractionOf(1_000, 100, 100_000), 1e-6f)
        assertEquals(1_000, SpeedScale.speedAt(0.34f, 100, 100_000))
        assertEquals(10_000, SpeedScale.speedAt(2f / 3f, 100, 100_000))
        assertEquals(100, SpeedScale.speedAt(0f, 100, 100_000))
        assertEquals(100_000, SpeedScale.speedAt(1f, 100, 100_000))
        assertEquals(2.5, SpeedScale.units(2_500))
    }
}

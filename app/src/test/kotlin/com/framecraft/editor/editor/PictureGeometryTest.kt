/*
 * Unit tests for the picture math behind masks and the eyedropper: picture boxes on screen, mask gestures and shape
 * switches, edge lines, and the picked key color and source time.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MAX_MASK_SIZE
import com.framecraft.editor.domain.model.MaskShape
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.engine.FramePixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies [PictureBox], [MaskGeometry], and [KeyColorMath]. */
class PictureGeometryTest {
    private val upright = PictureBox(Offset(800f, 450f), Size(1600f, 900f), 0f)

    private val quarterTurn = PictureBox(Offset(500f, 500f), Size(400f, 200f), 90f)

    /** Asserts [actual] lies within a hundredth of a pixel of [expected]. */
    private fun assertNear(
        expected: Offset,
        actual: Offset,
    ) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    /** Picture corners land on the box corners, and screen points map back to the same picture points. */
    @Test
    fun uprightBoxRoundTrips() {
        assertNear(Offset(0f, 0f), upright.toScreen(Offset(-1f, -1f)))
        assertNear(Offset(1600f, 900f), upright.toScreen(Offset(1f, 1f)))
        assertNear(Offset(0.5f, -0.5f), upright.toPicture(upright.toScreen(Offset(0.5f, -0.5f))))
        assertEquals(16f / 9f, upright.aspect, 0.0001f)
    }

    /** A box turned a quarter clockwise sends the picture's right edge downward and turns drags to match. */
    @Test
    fun turnedBoxFollowsRotation() {
        assertNear(Offset(500f, 700f), quarterTurn.toScreen(Offset(1f, 0f)))
        assertNear(Offset(1f, 0f), quarterTurn.toPicture(Offset(500f, 700f)))
        assertNear(Offset(1f, 0f), quarterTurn.toPictureDelta(Offset(0f, 200f)))
        assertTrue(quarterTurn.contains(Offset(500f, 690f)))
        assertFalse(quarterTurn.contains(Offset(690f, 500f)))
    }

    /** A gesture moves, scales, and turns the mask, keeping its center on the picture and its size in range. */
    @Test
    fun gesturesTransformWithinLimits() {
        val mask = ClipMask(MaskShape.CIRCLE, halfWidth = 0.5f, halfHeight = 0.4f, rotationDegrees = 350f)
        val moved = MaskGeometry.transformed(mask, upright, Offset(400f, -225f), 2f, 20f)
        assertEquals(0.5f, moved.centerX, 0.001f)
        assertEquals(-0.5f, moved.centerY, 0.001f)
        assertEquals(1f, moved.halfWidth, 0.001f)
        assertEquals(0.8f, moved.halfHeight, 0.001f)
        assertEquals(10f, moved.rotationDegrees, 0.001f)
        val clamped = MaskGeometry.transformed(mask, upright, Offset(10_000f, 10_000f), 10f, 0f)
        assertEquals(MaskGeometry.MAX_CENTER, clamped.centerX, 0.001f)
        assertEquals(MaskGeometry.MAX_CENTER, clamped.centerY, 0.001f)
        assertEquals(MAX_MASK_SIZE, clamped.halfWidth, 0.001f)
        val shrunk = MaskGeometry.transformed(mask, upright, Offset.Zero, 0.01f, 0f)
        assertEquals(MaskGeometry.MIN_HALF_SIZE, shrunk.halfWidth, 0.001f)
    }

    /** Switching shape starts from the new shape's defaults but keeps the center, turn, and inversion. */
    @Test
    fun switchingShapeKeepsPlacement() {
        val current = ClipMask(MaskShape.CIRCLE, centerX = 0.3f, centerY = -0.2f, rotationDegrees = 45f, invert = true)
        val switched = MaskGeometry.switched(current, MaskShape.RECTANGLE)
        val expected =
            ClipMask.defaultFor(MaskShape.RECTANGLE).copy(
                centerX = 0.3f,
                centerY = -0.2f,
                rotationDegrees = 45f,
                invert = true,
            )
        assertEquals(expected, switched)
        assertEquals(ClipMask.defaultFor(MaskShape.STAR), MaskGeometry.switched(null, MaskShape.STAR))
        assertNull(MaskGeometry.switched(current, null))
    }

    /** A linear mask draws one edge line and a mirror mask two; closed shapes draw outlines instead. */
    @Test
    fun edgeLinesMatchShape() {
        val linear = ClipMask.defaultFor(MaskShape.LINEAR)
        val line = MaskGeometry.linesOf(linear, upright).single()
        assertEquals(800f, line.first.x, 0.01f)
        assertEquals(800f, line.second.x, 0.01f)
        assertEquals(2, MaskGeometry.linesOf(ClipMask.defaultFor(MaskShape.MIRROR), upright).size)
        val circle = ClipMask.defaultFor(MaskShape.CIRCLE)
        assertTrue(MaskGeometry.linesOf(circle, upright).isEmpty())
        assertTrue(MaskGeometry.outlineOf(circle, upright).isNotEmpty())
        assertTrue(MaskGeometry.outlineOf(linear, upright).isEmpty())
    }

    /** The picked color averages the pixels around the tap, is opaque, and taps off the frame read its edge. */
    @Test
    fun pickedColorAveragesAroundTap() {
        val width = 10
        val green = 0xFF00B140.toInt()
        val pixels = IntArray(width * width) { index -> if (index % width < width / 2) green else 0x800000FF.toInt() }
        val frame = FramePixels(width, width, pixels)
        assertEquals(green, KeyColorMath.colorAt(frame, Offset(-0.8f, 0f)))
        assertEquals(0xFF0000FF.toInt(), KeyColorMath.colorAt(frame, Offset(5f, 5f)))
    }

    /** Without a speed change the source time follows the playhead, held to the clip's own frames. */
    @Test
    fun sourceTimeFollowsPlayhead() {
        val clip = Clip("c", "a", 1_000_000L, 500_000L, 2_000_000L)
        val rate = FrameRate(30, 1)
        assertEquals(1_000_000L, KeyColorMath.sourceMicrosAt(clip, 1_500_000L, rate))
        assertEquals(500_000L, KeyColorMath.sourceMicrosAt(clip, 0L, rate))
        assertEquals(2_499_999L, KeyColorMath.sourceMicrosAt(clip, 9_000_000L, rate))
    }
}

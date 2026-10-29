/*
 * Unit tests for the preview overlay math: fitted size, screen center, gesture changes, and rotation snapping.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.framecraft.editor.domain.model.MAX_OVERLAY_SCALE
import com.framecraft.editor.domain.model.OverlayTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies [OverlayGeometry] on a 1600 x 900 canvas. */
class OverlayGeometryTest {
    private val canvas = Size(1600f, 900f)

    /** A wide overlay fits the canvas width and a tall one its height, then both follow the scale. */
    @Test
    fun boxSizeFitsThenScales() {
        assertEquals(Size(1600f, 800f), OverlayGeometry.boxSize(canvas, 2f, 1f))
        assertEquals(Size(450f, 900f), OverlayGeometry.boxSize(canvas, 0.5f, 1f))
        assertEquals(Size(180f, 360f), OverlayGeometry.boxSize(canvas, 0.5f, 0.4f))
    }

    /** The normalized center maps to pixels with y downward. */
    @Test
    fun centerMapsToPixels() {
        assertEquals(Offset(800f, 450f), OverlayGeometry.center(canvas, OverlayTransform()))
        assertEquals(Offset(1200f, 0f), OverlayGeometry.center(canvas, OverlayTransform(centerX = 0.5f, centerY = -1f)))
    }

    /** Panning, zooming, and turning move the placement and stay within limits. */
    @Test
    fun gesturesClamp() {
        val moved = OverlayGeometry.transformed(OverlayTransform.DEFAULT, canvas, Offset(400f, -225f), 2f, 30f)
        assertEquals(OverlayTransform(0.5f, -0.5f, 0.8f, 30f, 1f), moved)
        val pushed = OverlayGeometry.transformed(moved, canvas, Offset(10_000f, 10_000f), 100f, -60f)
        assertEquals(OverlayTransform(1f, 1f, MAX_OVERLAY_SCALE, 330f, 1f), pushed)
        val shrunk = OverlayGeometry.transformed(moved, canvas, Offset.Zero, 0.01f, 0f)
        assertEquals(OverlayGeometry.MIN_SCALE, shrunk.scale)
    }

    /** Near a quarter turn the rotation snaps to it; elsewhere it is kept. */
    @Test
    fun rotationSnapsNearQuarterTurns() {
        assertEquals(90f, OverlayGeometry.snappedRotation(86f))
        assertEquals(0f, OverlayGeometry.snappedRotation(357f))
        assertEquals(45f, OverlayGeometry.snappedRotation(45f))
    }
}

/*
 * Unit tests for the canvas tool math: swapping fit and fill, and mapping between placements and handle transforms.
 */
package com.framecraft.editor.editor

import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.OverlayTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the pure helpers behind the Fit / Fill and Adjust tools. */
class CanvasToolsTest {
    /** Fit becomes fill and back, keeping the clip's position; the plain default is stored as no placement. */
    @Test
    fun toggledFitSwapsAndKeepsPosition() {
        assertEquals(CanvasPlacement(CanvasFit.FILL), toggledFit(null))
        assertNull(toggledFit(CanvasPlacement(CanvasFit.FILL)))
        val moved = CanvasPlacement(CanvasFit.FIT, centerX = 0.5f, scale = 2f)
        assertEquals(moved.copy(fit = CanvasFit.FILL), toggledFit(moved))
    }

    /** A placement maps to fully opaque handles and back without loss. */
    @Test
    fun placementRoundTripsThroughHandles() {
        val placement = CanvasPlacement(CanvasFit.FILL, 0.25f, -0.5f, 1.5f, 30f)
        val transform = transformOf(placement)
        assertEquals(OverlayTransform(0.25f, -0.5f, 1.5f, 30f, 1f), transform)
        assertEquals(placement, placementOf(CanvasFit.FILL, transform))
        assertNull(placementOf(CanvasFit.FIT, OverlayTransform()))
    }

    /** Handles show only for the clip they were turned on for, and a second toggle hides them. */
    @Test
    fun adjustFollowsTheClip() {
        val editing = CanvasEditing()
        editing.toggleAdjust("a")
        assertTrue(editing.isAdjusting("a"))
        assertFalse(editing.isAdjusting("b"))
        editing.toggleAdjust("a")
        assertFalse(editing.isAdjusting("a"))
        assertFalse(editing.isAdjusting(null))
    }
}

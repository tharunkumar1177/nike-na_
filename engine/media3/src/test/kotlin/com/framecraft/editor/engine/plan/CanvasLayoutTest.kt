/*
 * Unit tests for the compositor layout: input order, canvas sizes, and fit factors.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies [CanvasLayout] with plain sizes and hand-built plans. */
class CanvasLayoutTest {
    /** The original shape keeps the base size; chosen shapes keep its short side with even sides. */
    @Test
    fun canvasKeepsTheBaseShortSide() {
        val landscape = PixelSize(1920, 1080)
        assertEquals(landscape, CanvasLayout.canvasSize(landscape, null))
        assertEquals(PixelSize(1080, 1920), CanvasLayout.canvasSize(landscape, 9f / 16f))
        assertEquals(PixelSize(1080, 1080), CanvasLayout.canvasSize(landscape, 1f))
        assertEquals(PixelSize(1080, 1350), CanvasLayout.canvasSize(landscape, 4f / 5f))
        assertEquals(PixelSize(1440, 1080), CanvasLayout.canvasSize(landscape, 4f / 3f))
        assertEquals(PixelSize(720, 960), CanvasLayout.canvasSize(PixelSize(720, 1280), 3f / 4f))
    }

    /** Fit keeps the whole picture inside and fill covers the canvas. */
    @Test
    fun fitAndFillFactors() {
        val portrait = PixelSize(1080, 1920)
        val landscape = PixelSize(1920, 1080)
        assertEquals(0.5625f, CanvasLayout.fitFactor(CanvasFit.FIT, portrait, landscape), 1e-4f)
        assertEquals(1.7778f, CanvasLayout.fitFactor(CanvasFit.FILL, portrait, landscape), 1e-4f)
        assertEquals(1f, CanvasLayout.fitFactor(CanvasFit.FIT, landscape, landscape), 0f)
        assertEquals(2f, CanvasLayout.fitFactor(CanvasFit.FILL, PixelSize(400, 400), PixelSize(200, 100)), 0f)
    }

    /** Inputs run clock, overlays, base, then the background; sound adds none. */
    @Test
    fun inputsFollowRegistrationOrder() {
        val overlaySpans = listOf(OverlaySpan(0, 1_000_000, OverlayTransform()))
        val baseSpans = listOf(BaseSpan(0, 2_000_000, CanvasPlacement()))
        val gap = listOf(PlanSegment.Gap(2_000_000))
        val plan =
            PlaybackPlan(
                sequences =
                    listOf(
                        SequencePlan(gap, overlaySpans, TrackKind.OVERLAY),
                        SequencePlan(gap, baseSpans = baseSpans),
                        SequencePlan(gap, kind = TrackKind.AUDIO),
                    ),
                frameRate = FrameRate.FPS_30,
                composited = true,
                background = BackgroundPlan.Solid(0xFF000080.toInt()),
            )
        assertEquals(
            listOf(
                CompositorInput.Clock,
                CompositorInput.Overlay(overlaySpans),
                CompositorInput.Base(baseSpans),
                CompositorInput.Solid,
            ),
            CanvasLayout.inputsOf(plan),
        )
    }
}

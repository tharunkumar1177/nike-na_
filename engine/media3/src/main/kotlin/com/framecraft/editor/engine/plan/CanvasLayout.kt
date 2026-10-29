/*
 * Media3-independent compositor layout: what each composited picture input shows, the canvas size, and how inputs are
 * sized to it.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.TrackKind
import kotlin.math.roundToInt

/** A width and height in pixels. */
data class PixelSize(
    val width: Int,
    val height: Int,
)

/** What one composited picture input shows, in the order the compositor registers them. */
sealed interface CompositorInput {
    /** The transparent still that times output frames; never shown. */
    data object Clock : CompositorInput

    /** An overlay lane, shown during its [spans]. */
    data class Overlay(
        val spans: List<OverlaySpan>,
    ) : CompositorInput

    /** A base track, shown during its [spans] at each clip's placement. */
    data class Base(
        val spans: List<BaseSpan>,
    ) : CompositorInput

    /** A solid color still stretched over the whole canvas. */
    data object Solid : CompositorInput

    /** A blurred copy of the first base track, filling the canvas during its [spans]. */
    data class Blur(
        val spans: List<BaseSpan>,
    ) : CompositorInput
}

/** Layout rules shared by the composition builder and the compositor settings. */
object CanvasLayout {
    /**
     * Returns the compositor inputs of [plan] in registration order: the clock, then every picture sequence (overlays
     * from the top lane down, then the base), then the background when it is not black. Media3 draws earlier inputs
     * on top, so the background sits under everything. Sound sequences register no picture input.
     */
    fun inputsOf(plan: PlaybackPlan): List<CompositorInput> {
        val pictures =
            plan.sequences
                .filter { it.kind != TrackKind.AUDIO }
                .map { sequence ->
                    if (sequence.kind == TrackKind.OVERLAY) {
                        CompositorInput.Overlay(sequence.overlaySpans)
                    } else {
                        CompositorInput.Base(sequence.baseSpans)
                    }
                }
        val background =
            when (val chosen = plan.background) {
                null -> null
                is BackgroundPlan.Solid -> CompositorInput.Solid
                is BackgroundPlan.Blur -> CompositorInput.Blur(chosen.spans)
            }
        return listOf(CompositorInput.Clock) + pictures + listOfNotNull(background)
    }

    /**
     * Returns the canvas for a first base picture of [base]: [base] itself for the original shape (null [aspectRatio]),
     * otherwise the [aspectRatio] shape that keeps [base]'s short side, with even sides.
     */
    fun canvasSize(
        base: PixelSize,
        aspectRatio: Float?,
    ): PixelSize {
        val short = minOf(base.width, base.height)
        return when {
            aspectRatio == null -> base
            aspectRatio >= 1f -> PixelSize(even(short * aspectRatio), even(short.toFloat()))
            else -> PixelSize(even(short.toFloat()), even(short / aspectRatio))
        }
    }

    /**
     * Returns the factor that sizes [input] to [canvas] at scale 1: the whole picture inside for [CanvasFit.FIT], the
     * whole canvas covered for [CanvasFit.FILL]. Media3 draws an input at its pixel size relative to the canvas, so the
     * factor undoes that.
     */
    fun fitFactor(
        fit: CanvasFit,
        canvas: PixelSize,
        input: PixelSize,
    ): Float {
        val horizontal = canvas.width.toFloat() / input.width
        val vertical = canvas.height.toFloat() / input.height
        return if (fit == CanvasFit.FIT) minOf(horizontal, vertical) else maxOf(horizontal, vertical)
    }

    /** Rounds [value] to the nearest even integer, at least 2. */
    private fun even(value: Float): Int = maxOf(2, (value / 2).roundToInt() * 2)
}

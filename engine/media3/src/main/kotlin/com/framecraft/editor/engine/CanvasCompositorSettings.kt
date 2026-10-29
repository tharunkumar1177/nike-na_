/*
 * Tells the Media3 video compositor the canvas size and where each picture input sits on it at each moment.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.engine.plan.BaseSpan
import com.framecraft.editor.engine.plan.CanvasLayout
import com.framecraft.editor.engine.plan.CompositorInput
import com.framecraft.editor.engine.plan.PixelSize
import com.framecraft.editor.engine.plan.TransitionSpan

/** Media3 frame anchors run from -1 to 1. */
private const val ANCHOR_LIMIT = 1f

/** The blurred background covers the canvas, centered and upright. */
private val BLUR_PLACEMENT = CanvasPlacement(CanvasFit.FILL)

/**
 * Compositor settings for [inputs], one per registered picture input in order. The canvas takes the [aspectRatio]
 * shape around the first base input (null keeps that input's shape). Inputs show only while one of their spans
 * covers the frame time and are hidden otherwise, because Media3 fills sequence gaps with opaque black frames.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class CanvasCompositorSettings(
    private val inputs: List<CompositorInput>,
    private val aspectRatio: Float?,
) : VideoCompositorSettings {
    private val hidden: OverlaySettings = StaticOverlaySettings.Builder().setAlphaScale(0f).build()

    private val canvasInput: Int =
        inputs.indexOfFirst { it is CompositorInput.Base }.takeIf { it >= 0 } ?: inputs.lastIndex

    @Volatile
    private var inputSizes: List<Size> = emptyList()

    @Volatile
    private var canvas: Size? = null

    /**
     * Returns the canvas: the chosen shape around the first base input. The compositor asks for it before placing each
     * frame's inputs, so the sizes are kept for [getOverlaySettings].
     */
    override fun getOutputSize(inputSizes: List<Size>): Size {
        this.inputSizes = inputSizes
        val base = inputSizes.getOrElse(canvasInput) { inputSizes.first() }
        val size = CanvasLayout.canvasSize(PixelSize(base.width, base.height), aspectRatio)
        return Size(size.width, size.height).also { canvas = it }
    }

    /** Returns how input [inputId] is drawn at [presentationTimeUs], or hidden when nothing of it shows then. */
    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long,
    ): OverlaySettings {
        val input = inputs.getOrNull(inputId) ?: return hidden
        val settings =
            when (input) {
                CompositorInput.Clock -> {
                    null
                }

                is CompositorInput.Overlay -> {
                    input.spans
                        .firstOrNull { presentationTimeUs >= it.startMicros && presentationTimeUs < it.endMicros }
                        ?.let { overlaySettings(it.transform, fit(inputId, CanvasFit.FIT)) }
                }

                is CompositorInput.Base -> {
                    input.spans.at(presentationTimeUs)?.let { span ->
                        val progress = TransitionMotion.progress(span.startMicros, span.endMicros, presentationTimeUs)
                        val motion = span.outgoing?.let { TransitionMotion.outgoing(it, progress) } ?: Motion()
                        movedSettings(span.placement, inputId, motion)
                    }
                }

                CompositorInput.Solid -> {
                    stretched(inputId)
                }

                is CompositorInput.Blur -> {
                    input.spans.at(presentationTimeUs)?.let { movedSettings(BLUR_PLACEMENT, inputId, Motion()) }
                }

                is CompositorInput.Incoming -> {
                    input.spans.during(presentationTimeUs)?.let { span ->
                        val progress = TransitionMotion.progress(span.startMicros, span.endMicros, presentationTimeUs)
                        movedSettings(span.placement, inputId, TransitionMotion.incoming(span.kind, progress))
                    }
                }

                is CompositorInput.Backdrop -> {
                    input.spans.during(presentationTimeUs)?.let { stretched(inputId) }
                }
            }
        return settings ?: hidden
    }

    /** Returns the span covering [timeUs], if any. */
    private fun List<BaseSpan>.at(timeUs: Long): BaseSpan? =
        firstOrNull { timeUs >= it.startMicros && timeUs < it.endMicros }

    /** Returns the transition playing at [timeUs], if any. */
    private fun List<TransitionSpan>.during(timeUs: Long): TransitionSpan? =
        firstOrNull { timeUs >= it.startMicros && timeUs < it.endMicros }

    /** Returns the factor that sizes input [inputId] to the canvas by [fit]; it is 1 before any size is known. */
    private fun fit(
        inputId: Int,
        fit: CanvasFit,
    ): Float {
        val target = canvas
        val input = inputSizes.getOrNull(inputId)?.takeIf { it.width > 0 && it.height > 0 }
        return if (target == null || input == null) {
            1f
        } else {
            CanvasLayout.fitFactor(fit, PixelSize(target.width, target.height), PixelSize(input.width, input.height))
        }
    }

    /** Maps overlay [transform] to Media3 settings scaled by [fit]. */
    private fun overlaySettings(
        transform: OverlayTransform,
        fit: Float,
    ): OverlaySettings =
        placementSettings(
            centerX = transform.centerX,
            centerY = transform.centerY,
            rotationDegrees = transform.rotationDegrees,
            scale = transform.scale * fit,
            alpha = transform.opacity,
        )

    /** Maps base [placement] of input [inputId], moved by [motion], to Media3 settings. */
    private fun movedSettings(
        placement: CanvasPlacement,
        inputId: Int,
        motion: Motion,
    ): OverlaySettings {
        val scale = placement.scale * fit(inputId, placement.fit) * motion.zoom
        return placementSettings(
            centerX = placement.centerX,
            centerY = placement.centerY,
            rotationDegrees = placement.rotationDegrees,
            scale = scale,
            alpha = motion.alpha,
            shift = Shift(motion.shiftX, widthShare(inputId, scale)),
        )
    }

    /**
     * Returns half of input [inputId]'s drawn width at [scale] over half the canvas width, the distance in
     * normalized canvas units from the input's center to its side; it is [scale] before any size is known.
     */
    private fun widthShare(
        inputId: Int,
        scale: Float,
    ): Float {
        val target = canvas
        val input = inputSizes.getOrNull(inputId)?.takeIf { it.width > 0 }
        return if (target == null || input == null) scale else scale * input.width / target.width
    }

    /** Returns settings that stretch input [inputId] over the whole canvas, each axis on its own. */
    private fun stretched(inputId: Int): OverlaySettings {
        val target = canvas
        val input = inputSizes.getOrNull(inputId)?.takeIf { it.width > 0 && it.height > 0 }
        val scaleX = if (target == null || input == null) 1f else target.width.toFloat() / input.width
        val scaleY = if (target == null || input == null) 1f else target.height.toFloat() / input.height
        return StaticOverlaySettings.Builder().setScale(scaleX, scaleY).build()
    }
}

/**
 * A horizontal move of [x] normalized canvas units for an input whose side is [widthShare] units from its center.
 */
internal data class Shift(
    val x: Float,
    val widthShare: Float,
) {
    /** The unmoved shift. */
    companion object {
        /** No move at all. */
        val NONE = Shift(0f, 1f)
    }
}

/**
 * Maps a placement in project terms (center with y downward, clockwise [rotationDegrees]) to Media3 settings at
 * [scale] and [alpha], moved sideways by [shift]. Media3 y points up and its rotation runs counter-clockwise. Media3
 * anchors stay within the canvas, so a move past the edge anchors the input's own side there instead of its center;
 * other centers past the canvas edge are held at the edge.
 */
@OptIn(markerClass = [UnstableApi::class])
internal fun placementSettings(
    centerX: Float,
    centerY: Float,
    rotationDegrees: Float,
    scale: Float,
    alpha: Float,
    shift: Shift = Shift.NONE,
): OverlaySettings {
    val wanted = centerX + shift.x
    val backgroundX = wanted.coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT)
    val overlayX =
        if (shift.x == 0f || shift.widthShare <= 0f) {
            0f
        } else {
            ((backgroundX - wanted) / shift.widthShare).coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT)
        }
    return StaticOverlaySettings
        .Builder()
        .setScale(scale, scale)
        .setRotationDegrees(-rotationDegrees)
        .setAlphaScale(alpha)
        .setOverlayFrameAnchor(overlayX, 0f)
        .setBackgroundFrameAnchor(backgroundX, (-centerY).coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT))
        .build()
}

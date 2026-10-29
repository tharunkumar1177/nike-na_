/*
 * Tells the Media3 video compositor where each overlay sequence sits on the canvas at each moment and which input
 * sets the canvas.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.engine.plan.OverlaySpan

/** Media3 frame anchors run from -1 to 1. */
private const val ANCHOR_LIMIT = 1f

/**
 * Compositor settings built from [spansBySequence], one entry per composition sequence in order. A null entry (the
 * base video) fills the frame; any other sequence shows only while one of its spans covers the frame time, at that
 * span's placement, and is hidden otherwise, so an empty list never shows. Input [canvasInput] sets the output size.
 * An overlay at scale 1 fits inside the canvas whatever its pixel size.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class OverlayCompositorSettings(
    private val spansBySequence: List<List<OverlaySpan>?>,
    private val canvasInput: Int,
) : VideoCompositorSettings {
    private val fullFrame: OverlaySettings = StaticOverlaySettings.Builder().build()

    private val hidden: OverlaySettings = StaticOverlaySettings.Builder().setAlphaScale(0f).build()

    @Volatile
    private var inputSizes: List<Size> = emptyList()

    /**
     * Returns the size of the canvas input, so the base video sets the canvas as it does without overlays. The
     * compositor asks for it before placing each frame's inputs, so the sizes are kept for [getOverlaySettings].
     */
    override fun getOutputSize(inputSizes: List<Size>): Size {
        this.inputSizes = inputSizes
        return inputSizes.getOrElse(canvasInput) { inputSizes.first() }
    }

    /**
     * Returns the placement of input [inputId] at [presentationTimeUs]: full frame for the base, the covering span's
     * placement for other inputs, or hidden when no span covers it (Media3 fills sequence gaps with opaque black).
     */
    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long,
    ): OverlaySettings {
        val spans = spansBySequence.getOrNull(inputId) ?: return fullFrame
        val span = spans.firstOrNull { presentationTimeUs >= it.startMicros && presentationTimeUs < it.endMicros }
        return span?.let { settingsFor(it.transform, fitFactor(inputId)) } ?: hidden
    }

    /**
     * Returns the factor that makes input [inputId] fit inside the canvas at scale 1. Media3 draws an overlay at its
     * pixel size relative to the background, so the factor undoes that; it is 1 before any size is known.
     */
    private fun fitFactor(inputId: Int): Float {
        val sizes = inputSizes
        val canvas = sizes.getOrNull(canvasInput)
        val overlay = sizes.getOrNull(inputId)?.takeIf { it.width > 0 && it.height > 0 }
        return if (canvas == null || overlay == null) {
            1f
        } else {
            minOf(canvas.width.toFloat() / overlay.width, canvas.height.toFloat() / overlay.height)
        }
    }

    /** Maps [transform] to Media3 settings scaled by [fit]. */
    private fun settingsFor(
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
}

/**
 * Maps a placement in project terms (center with y downward, clockwise [rotationDegrees]) to Media3 settings at
 * [scale] and [alpha]. Media3 y points up and its rotation runs counter-clockwise; centers past the canvas edge are
 * held at the edge.
 */
@OptIn(markerClass = [UnstableApi::class])
internal fun placementSettings(
    centerX: Float,
    centerY: Float,
    rotationDegrees: Float,
    scale: Float,
    alpha: Float,
): OverlaySettings =
    StaticOverlaySettings
        .Builder()
        .setScale(scale, scale)
        .setRotationDegrees(-rotationDegrees)
        .setAlphaScale(alpha)
        .setOverlayFrameAnchor(0f, 0f)
        .setBackgroundFrameAnchor(
            centerX.coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT),
            (-centerY).coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT),
        ).build()

/*
 * Tells the Media3 video compositor where each overlay sequence sits on the canvas at each moment.
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
 * Compositor settings built from [spansBySequence], one list per composition sequence in order. Sequences without
 * spans (the base video) fill the frame; overlay sequences use the placement of the span covering the frame time.
 * The first input sets the output size. An overlay at scale 1 fits inside the canvas whatever its pixel size.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class OverlayCompositorSettings(
    private val spansBySequence: List<List<OverlaySpan>>,
) : VideoCompositorSettings {
    private val fullFrame: OverlaySettings = StaticOverlaySettings.Builder().build()

    @Volatile
    private var inputSizes: List<Size> = emptyList()

    /**
     * Returns the size of the first input, so the base video sets the canvas as it does without overlays. The
     * compositor asks for it before placing each frame's inputs, so the sizes are kept for [getOverlaySettings].
     */
    override fun getOutputSize(inputSizes: List<Size>): Size {
        this.inputSizes = inputSizes
        return inputSizes.first()
    }

    /** Returns the placement of input [inputId] at [presentationTimeUs], or full frame when none applies. */
    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long,
    ): OverlaySettings {
        val span =
            spansBySequence.getOrNull(inputId)?.firstOrNull {
                presentationTimeUs >= it.startMicros && presentationTimeUs < it.endMicros
            }
        return span?.let { settingsFor(it.transform, fitFactor(inputId)) } ?: fullFrame
    }

    /**
     * Returns the factor that makes input [inputId] fit inside the canvas at scale 1. Media3 draws an overlay at its
     * pixel size relative to the background, so the factor undoes that; it is 1 before any size is known.
     */
    private fun fitFactor(inputId: Int): Float {
        val sizes = inputSizes
        val canvas = sizes.firstOrNull()
        val overlay = sizes.getOrNull(inputId)
        return if (canvas == null || overlay == null || overlay.width <= 0 || overlay.height <= 0) {
            1f
        } else {
            minOf(canvas.width.toFloat() / overlay.width, canvas.height.toFloat() / overlay.height)
        }
    }

    /** Maps [transform] to Media3 settings scaled by [fit]: Media3 y points up and rotation runs counter-clockwise. */
    private fun settingsFor(
        transform: OverlayTransform,
        fit: Float,
    ): OverlaySettings =
        StaticOverlaySettings
            .Builder()
            .setScale(transform.scale * fit, transform.scale * fit)
            .setRotationDegrees(-transform.rotationDegrees)
            .setAlphaScale(transform.opacity)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(
                transform.centerX.coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT),
                (-transform.centerY).coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT),
            ).build()
}

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
 * The first input sets the output size.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class OverlayCompositorSettings(
    private val spansBySequence: List<List<OverlaySpan>>,
) : VideoCompositorSettings {
    private val fullFrame: OverlaySettings = StaticOverlaySettings.Builder().build()

    /** Returns the size of the first input, so the base video sets the canvas as it does without overlays. */
    override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes.first()

    /** Returns the placement of input [inputId] at [presentationTimeUs], or full frame when none applies. */
    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long,
    ): OverlaySettings {
        val span =
            spansBySequence.getOrNull(inputId)?.firstOrNull {
                presentationTimeUs >= it.startMicros && presentationTimeUs < it.endMicros
            }
        return span?.let { settingsFor(it.transform) } ?: fullFrame
    }

    /** Maps [transform] to Media3 settings: Media3 y points up and rotation runs counter-clockwise. */
    private fun settingsFor(transform: OverlayTransform): OverlaySettings =
        StaticOverlaySettings
            .Builder()
            .setScale(transform.scale, transform.scale)
            .setRotationDegrees(-transform.rotationDegrees)
            .setAlphaScale(transform.opacity)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(
                transform.centerX.coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT),
                (-transform.centerY).coerceIn(-ANCHOR_LIMIT, ANCHOR_LIMIT),
            ).build()
}

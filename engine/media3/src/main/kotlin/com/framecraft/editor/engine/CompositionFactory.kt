/*
 * Builds a Media3 Composition from a playback plan; shared by preview and export.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.engine.plan.PlanSegment
import com.framecraft.editor.engine.plan.PlaybackPlan
import kotlin.math.roundToInt

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Creates Media3 compositions. */
@OptIn(markerClass = [UnstableApi::class])
internal object CompositionFactory {
    /**
     * Returns a composition with one audio-and-video sequence per planned track, with overlay sequences placed by
     * [OverlayCompositorSettings]. Export passes [videoEffects] that apply to the whole output; the preview passes
     * none.
     */
    fun create(
        plan: PlaybackPlan,
        videoEffects: List<Effect> = emptyList(),
    ): Composition {
        val frameRate = (plan.frameRate.numerator.toDouble() / plan.frameRate.denominator).roundToInt()
        val sequences =
            plan.sequences.map { sequence ->
                val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
                sequence.segments.forEach { segment ->
                    when (segment) {
                        is PlanSegment.Gap -> builder.addGap(segment.durationMicros)
                        is PlanSegment.Media -> builder.addItem(editedItem(segment, frameRate))
                    }
                }
                builder.build()
            }
        val builder = Composition.Builder(sequences).setEffects(Effects(emptyList(), videoEffects))
        if (plan.sequences.size > 1) {
            builder.setVideoCompositorSettings(OverlayCompositorSettings(plan.sequences.map { it.overlaySpans }))
        }
        return builder.build()
    }

    /** Maps a media segment to an edited item, clipping videos and timing images. */
    private fun editedItem(
        segment: PlanSegment.Media,
        frameRate: Int,
    ): EditedMediaItem =
        when (segment.kind) {
            MediaKind.VIDEO -> {
                val clipping =
                    MediaItem.ClippingConfiguration
                        .Builder()
                        .setStartPositionUs(segment.sourceStartMicros)
                        .setEndPositionUs(segment.sourceStartMicros + segment.durationMicros)
                        .build()
                val item =
                    MediaItem
                        .Builder()
                        .setUri(segment.uri)
                        .setClippingConfiguration(clipping)
                        .build()
                EditedMediaItem.Builder(item).setDurationUs(segment.sourceDurationMicros).build()
            }

            MediaKind.IMAGE -> {
                val item =
                    MediaItem
                        .Builder()
                        .setUri(segment.uri)
                        .setImageDurationMs(segment.durationMicros / MICROS_PER_MILLI)
                        .build()
                EditedMediaItem
                    .Builder(item)
                    .setDurationUs(segment.durationMicros)
                    .setFrameRate(frameRate)
                    .build()
            }
        }
}

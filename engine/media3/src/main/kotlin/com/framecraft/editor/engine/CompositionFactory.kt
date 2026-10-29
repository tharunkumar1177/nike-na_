/*
 * Builds a Media3 Composition from a playback plan; shared by preview and export.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.engine.plan.PlanSegment
import com.framecraft.editor.engine.plan.PlaybackPlan
import com.framecraft.editor.engine.text.TextOverlay
import kotlin.math.roundToInt

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Track types of a sound-only sequence. */
private val AUDIO_TRACKS = setOf(C.TRACK_TYPE_AUDIO)

/** Track types of a picture sequence, which also carries sound. */
private val AUDIO_VIDEO_TRACKS = setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)

/** Creates Media3 compositions. */
@OptIn(markerClass = [UnstableApi::class])
internal object CompositionFactory {
    /**
     * Returns a composition with one sequence per planned track: audio-and-video for picture tracks and audio only
     * for sound tracks, with overlay sequences placed by [OverlayCompositorSettings]. Text is drawn over the composed
     * picture before [videoEffects], which export passes to apply to the whole output; the preview passes none.
     */
    fun create(
        plan: PlaybackPlan,
        videoEffects: List<Effect> = emptyList(),
    ): Composition {
        val frameRate = (plan.frameRate.numerator.toDouble() / plan.frameRate.denominator).roundToInt()
        val sequences =
            plan.sequences.map { sequence ->
                val audioOnly = sequence.kind == TrackKind.AUDIO
                val trackTypes = if (audioOnly) AUDIO_TRACKS else AUDIO_VIDEO_TRACKS
                val builder = EditedMediaItemSequence.Builder(trackTypes)
                sequence.segments.forEach { segment ->
                    when (segment) {
                        is PlanSegment.Gap -> builder.addGap(segment.durationMicros)
                        is PlanSegment.Media -> builder.addItem(editedItem(segment, frameRate, audioOnly))
                    }
                }
                builder.build()
            }
        val text: List<Effect> =
            if (plan.textSpans.isEmpty()) emptyList() else listOf(OverlayEffect(plan.textSpans.map(::TextOverlay)))
        val builder = Composition.Builder(sequences).setEffects(Effects(emptyList(), text + videoEffects))
        if (plan.sequences.any { it.kind == TrackKind.OVERLAY }) {
            builder.setVideoCompositorSettings(OverlayCompositorSettings(plan.sequences.map { it.overlaySpans }))
        }
        return builder.build()
    }

    /** Maps a media segment to an edited item; [audioOnly] items keep only their sound. */
    private fun editedItem(
        segment: PlanSegment.Media,
        frameRate: Int,
        audioOnly: Boolean,
    ): EditedMediaItem =
        when (segment.kind) {
            MediaKind.IMAGE -> imageItem(segment, frameRate)
            MediaKind.VIDEO, MediaKind.AUDIO -> clippedItem(segment, audioOnly || segment.kind == MediaKind.AUDIO)
            MediaKind.TEXT -> throw IllegalArgumentException("Text is drawn as an overlay, not played as media")
        }

    /**
     * Returns the clipped source of [segment] with its sound chain. A muted picture item drops its sound; a
     * [soundOnly] item drops its picture and keeps its sound, silenced by the gain envelope when muted.
     */
    private fun clippedItem(
        segment: PlanSegment.Media,
        soundOnly: Boolean,
    ): EditedMediaItem {
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
        val builder = EditedMediaItem.Builder(item).setDurationUs(segment.sourceDurationMicros)
        if (!soundOnly && segment.volumePercent == 0) {
            builder.setRemoveAudio(true)
        } else {
            builder.setEffects(Effects(AudioItemEffects.processorsFor(segment), emptyList()))
        }
        if (soundOnly) {
            builder.setRemoveVideo(true)
        }
        return builder.build()
    }

    /** Returns [segment] as a still image shown for its duration at [frameRate]. */
    private fun imageItem(
        segment: PlanSegment.Media,
        frameRate: Int,
    ): EditedMediaItem {
        val item =
            MediaItem
                .Builder()
                .setUri(segment.uri)
                .setImageDurationMs(segment.durationMicros / MICROS_PER_MILLI)
                .build()
        return EditedMediaItem
            .Builder(item)
            .setDurationUs(segment.durationMicros)
            .setFrameRate(frameRate)
            .build()
    }
}

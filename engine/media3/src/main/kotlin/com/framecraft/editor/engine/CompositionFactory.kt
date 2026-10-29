/*
 * Builds a Media3 Composition from a playback plan; shared by preview and export.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.engine.plan.OverlaySpan
import com.framecraft.editor.engine.plan.PlanSegment
import com.framecraft.editor.engine.plan.PlaybackPlan
import com.framecraft.editor.engine.plan.SequencePlan
import com.framecraft.editor.engine.text.TextOverlay
import kotlin.math.roundToInt

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Track types of a sound-only sequence. */
private val AUDIO_TRACKS = setOf(C.TRACK_TYPE_AUDIO)

/** Track types of a picture sequence, which also carries sound. */
private val AUDIO_VIDEO_TRACKS = setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)

/** A fully transparent 1 by 1 PNG, shown by the clock sequence. */
private const val TRANSPARENT_PIXEL_URI =
    "data:image/png;base64," +
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZ" +
        "cwAADsMAAA7DAcdvqGQAAAALSURBVBhXY2AAAgAABQABqtXIUQAAAABJRU5ErkJggg=="

/** Creates Media3 compositions. */
@OptIn(markerClass = [UnstableApi::class])
internal object CompositionFactory {
    /**
     * Returns a composition with one sequence per planned track: audio-and-video for picture tracks and audio only
     * for sound tracks. Text is drawn over the composed picture before [videoEffects], which export passes to apply
     * to the whole output; the preview passes none.
     *
     * With overlays, Media3 composites the inputs and times every output frame by the first sequence, which it also
     * draws on top. A hidden clock sequence of the project's frame rate goes first so output frames keep that rate
     * through overlay gaps and overlay videos, and [OverlayCompositorSettings] places the overlays and takes the
     * canvas from the first base sequence. HDR inputs are tone-mapped because the compositor draws only SDR.
     */
    fun create(
        plan: PlaybackPlan,
        videoEffects: List<Effect> = emptyList(),
    ): Composition {
        val frameRate = (plan.frameRate.numerator.toDouble() / plan.frameRate.denominator).roundToInt()
        val planned = plan.sequences.map { sequenceOf(it, frameRate) }
        val composited = plan.sequences.any { it.kind == TrackKind.OVERLAY }
        val sequences = if (composited) listOf(clockSequence(plan, frameRate)) + planned else planned
        val text: List<Effect> =
            if (plan.textSpans.isEmpty()) emptyList() else listOf(OverlayEffect(plan.textSpans.map(::TextOverlay)))
        val builder = Composition.Builder(sequences).setEffects(Effects(emptyList(), text + videoEffects))
        if (composited) {
            val spans = listOf(emptyList<OverlaySpan>()) + plan.sequences.map { it.placementSpans() }
            val canvas = 1 + plan.sequences.indexOfFirst { it.kind == TrackKind.VIDEO }.coerceAtLeast(0)
            builder
                .setVideoCompositorSettings(OverlayCompositorSettings(spans, canvas))
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        }
        return builder.build()
    }

    /** Returns null for a base sequence, which fills the frame, and the clip placements of any other sequence. */
    private fun SequencePlan.placementSpans(): List<OverlaySpan>? =
        if (kind == TrackKind.OVERLAY) overlaySpans else null

    /** Maps [sequence] to a Media3 sequence whose stills run at [frameRate]. */
    private fun sequenceOf(
        sequence: SequencePlan,
        frameRate: Int,
    ): EditedMediaItemSequence {
        val audioOnly = sequence.kind == TrackKind.AUDIO
        val builder = EditedMediaItemSequence.Builder(if (audioOnly) AUDIO_TRACKS else AUDIO_VIDEO_TRACKS)
        sequence.segments.forEach { segment ->
            when (segment) {
                is PlanSegment.Gap -> builder.addGap(segment.durationMicros)
                is PlanSegment.Media -> builder.addItem(editedItem(segment, frameRate, audioOnly))
            }
        }
        return builder.build()
    }

    /** Returns a transparent still at [frameRate] that lasts as long as the longest sequence of [plan]. */
    private fun clockSequence(
        plan: PlaybackPlan,
        frameRate: Int,
    ): EditedMediaItemSequence {
        val durationMicros = plan.sequences.maxOf { sequence -> sequence.segments.sumOf { it.durationMicros } }
        val item =
            MediaItem
                .Builder()
                .setUri(TRANSPARENT_PIXEL_URI)
                .setMimeType(MimeTypes.IMAGE_PNG)
                .setImageDurationMs(durationMicros / MICROS_PER_MILLI)
                .build()
        val still =
            EditedMediaItem
                .Builder(item)
                .setDurationUs(durationMicros)
                .setFrameRate(frameRate)
                .build()
        return EditedMediaItemSequence.Builder(AUDIO_VIDEO_TRACKS).addItem(still).build()
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

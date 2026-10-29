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
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.engine.plan.BackgroundPlan
import com.framecraft.editor.engine.plan.CanvasLayout
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

/** Fully transparent black, shown by the clock sequence. */
private const val TRANSPARENT_ARGB = 0

/** Height the blurred background is reduced to before blurring, so the blur looks the same at any source size. */
private const val BLUR_HEIGHT_PIXELS = 360

/** Gaussian blur strength of the background, in pixels at [BLUR_HEIGHT_PIXELS]. */
private const val BLUR_SIGMA = 12f

/** Creates Media3 compositions. */
@OptIn(markerClass = [UnstableApi::class])
internal object CompositionFactory {
    /**
     * Returns a composition with one sequence per planned track: audio-and-video for picture tracks and audio only
     * for sound tracks. Text is drawn over the composed picture before [videoEffects], which export passes to apply
     * to the whole output; the preview passes none.
     *
     * A composited plan goes through the Media3 compositor, which times every output frame by the first sequence and
     * draws earlier sequences on top. A hidden clock sequence of the project's frame rate goes first so output frames
     * keep that rate through gaps and overlay videos; the background, when not black, goes after the pictures and
     * before the sound. [CanvasCompositorSettings] sizes the canvas and places every input. HDR inputs are tone-mapped
     * because the compositor draws only SDR.
     */
    fun create(
        plan: PlaybackPlan,
        videoEffects: List<Effect> = emptyList(),
    ): Composition {
        val frameRate = (plan.frameRate.numerator.toDouble() / plan.frameRate.denominator).roundToInt()
        val (sound, pictures) = plan.sequences.partition { it.kind == TrackKind.AUDIO }
        val durationMicros = plan.sequences.maxOfOrNull { sequence -> sequence.segments.sumOf { it.durationMicros } }
        val picture = pictures.map { sequenceOf(it, frameRate) }
        val pictureSequences =
            if (plan.composited && durationMicros != null) {
                val clock = stillSequence(SolidPng.dataUri(TRANSPARENT_ARGB), durationMicros, frameRate)
                val background = plan.background?.let { backgroundSequence(it, durationMicros, frameRate) }
                listOf(clock) + picture + listOfNotNull(background)
            } else {
                picture
            }
        val sequences = pictureSequences + sound.map { sequenceOf(it, frameRate) }
        val text: List<Effect> =
            if (plan.textSpans.isEmpty()) emptyList() else listOf(OverlayEffect(plan.textSpans.map(::TextOverlay)))
        val builder = Composition.Builder(sequences).setEffects(Effects(emptyList(), text + videoEffects))
        if (plan.composited && durationMicros != null) {
            builder
                .setVideoCompositorSettings(CanvasCompositorSettings(CanvasLayout.inputsOf(plan), plan.aspectRatio))
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        }
        return builder.build()
    }

    /** Returns the sequence that draws [background] for [durationMicros] at [frameRate]. */
    private fun backgroundSequence(
        background: BackgroundPlan,
        durationMicros: Long,
        frameRate: Int,
    ): EditedMediaItemSequence =
        when (background) {
            is BackgroundPlan.Solid -> {
                stillSequence(SolidPng.dataUri(background.argb), durationMicros, frameRate)
            }

            is BackgroundPlan.Blur -> {
                val builder = EditedMediaItemSequence.Builder(AUDIO_VIDEO_TRACKS)
                background.segments.forEach { segment ->
                    when (segment) {
                        is PlanSegment.Gap -> builder.addGap(segment.durationMicros)
                        is PlanSegment.Media -> builder.addItem(blurredItem(segment, frameRate))
                    }
                }
                builder.build()
            }
        }

    /** Returns the picture of [segment], silent, reduced, and blurred. */
    private fun blurredItem(
        segment: PlanSegment.Media,
        frameRate: Int,
    ): EditedMediaItem {
        val picture =
            if (segment.kind == MediaKind.IMAGE) {
                imageItem(segment, frameRate)
            } else {
                clippedItem(segment, soundOnly = false)
            }
        val blur = listOf(Presentation.createForHeight(BLUR_HEIGHT_PIXELS), GaussianBlur(BLUR_SIGMA))
        return picture
            .buildUpon()
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), blur))
            .build()
    }

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

    /** Returns a sequence of one PNG still at [uri] shown for [durationMicros] at [frameRate]. */
    private fun stillSequence(
        uri: String,
        durationMicros: Long,
        frameRate: Int,
    ): EditedMediaItemSequence {
        val item =
            MediaItem
                .Builder()
                .setUri(uri)
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

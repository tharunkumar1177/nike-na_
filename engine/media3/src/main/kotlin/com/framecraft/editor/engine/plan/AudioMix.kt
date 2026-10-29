/*
 * Mixer and ducking for the playback plan: each track's volume scales its clips, and clips that duck lower while an
 * audible voiceover plays.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.MAX_DUCK_PERCENT
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT

/** How long a clip takes to dip under a voiceover and to come back up. */
const val DUCK_RAMP_MICROS: Long = 250_000L

/** Voiceovers closer than this stay ducked between them, so the ramps never cross. */
const val DUCK_MERGE_GAP_MICROS: Long = 2 * DUCK_RAMP_MICROS

/**
 * A clip lowered to [floorGain] of its level from [startMicros] until [endMicros] (exclusive), in the clip's own time.
 * The level ramps down over [DUCK_RAMP_MICROS] before the window and back up over as long after it; window ends may
 * lie outside the clip, which then starts or ends ducked.
 */
data class DuckWindow(
    val startMicros: Long,
    val endMicros: Long,
    val floorGain: Float,
)

/** A timeline span from [startMicros] until [endMicros] (exclusive). */
private data class Span(
    val startMicros: Long,
    val endMicros: Long,
)

/** Applies track volumes and voiceover ducking to planned sequences. */
object AudioMix {
    /** Returns [sequence] with the sound of every media segment scaled by its track's [volumePercent]. */
    fun atTrackVolume(
        sequence: SequencePlan,
        volumePercent: Int,
    ): SequencePlan =
        if (volumePercent == UNITY_VOLUME_PERCENT) {
            sequence
        } else {
            val scaled =
                sequence.segments.map { segment ->
                    if (segment is PlanSegment.Media) {
                        segment.copy(volumePercent = segment.volumePercent * volumePercent / UNITY_VOLUME_PERCENT)
                    } else {
                        segment
                    }
                }
            sequence.copy(segments = scaled)
        }

    /**
     * Returns [sequences] with a duck window on every media segment that ducks, wherever an audible voiceover on an
     * audio sequence plays near it. Voiceovers themselves never duck.
     */
    fun ducked(sequences: List<SequencePlan>): List<SequencePlan> {
        val voiceovers = merged(voiceoverSpans(sequences))
        return if (voiceovers.isEmpty()) {
            sequences
        } else {
            sequences.map { sequence ->
                var cursor = 0L
                val segments =
                    sequence.segments.map { segment ->
                        val start = cursor
                        cursor += segment.durationMicros
                        if (segment is PlanSegment.Media) withDucking(segment, start, voiceovers) else segment
                    }
                sequence.copy(segments = segments)
            }
        }
    }

    /** Returns the timeline span of every audible voiceover on the audio sequences of [sequences]. */
    private fun voiceoverSpans(sequences: List<SequencePlan>): List<Span> =
        sequences
            .filter { it.kind == TrackKind.AUDIO }
            .flatMap { sequence ->
                var cursor = 0L
                sequence.segments.mapNotNull { segment ->
                    val start = cursor
                    cursor += segment.durationMicros
                    val media = segment as? PlanSegment.Media
                    Span(start, cursor).takeIf { media != null && media.voiceover && media.volumePercent > 0 }
                }
            }

    /** Returns [spans] sorted, with spans that overlap or lie within [DUCK_MERGE_GAP_MICROS] joined into one. */
    private fun merged(spans: List<Span>): List<Span> =
        spans.sortedBy { it.startMicros }.fold(emptyList()) { joined, span ->
            val last = joined.lastOrNull()
            if (last != null && span.startMicros - last.endMicros <= DUCK_MERGE_GAP_MICROS) {
                joined.dropLast(1) + Span(last.startMicros, maxOf(last.endMicros, span.endMicros))
            } else {
                joined + span
            }
        }

    /**
     * Returns [media], which starts at [startMicros] on the timeline, with a duck window for each of [voiceovers]
     * whose ramps reach it, or unchanged when it does not duck.
     */
    private fun withDucking(
        media: PlanSegment.Media,
        startMicros: Long,
        voiceovers: List<Span>,
    ): PlanSegment.Media {
        val end = startMicros + media.durationMicros
        val floor = (MAX_DUCK_PERCENT - media.autoDuckPercent).toFloat() / MAX_DUCK_PERCENT
        val windows =
            voiceovers
                .filter { it.startMicros - DUCK_RAMP_MICROS < end && startMicros < it.endMicros + DUCK_RAMP_MICROS }
                .map { DuckWindow(it.startMicros - startMicros, it.endMicros - startMicros, floor) }
        val ducks = media.autoDuckPercent > 0 && !media.voiceover
        return if (ducks && windows.isNotEmpty()) media.copy(duckWindows = windows) else media
    }
}

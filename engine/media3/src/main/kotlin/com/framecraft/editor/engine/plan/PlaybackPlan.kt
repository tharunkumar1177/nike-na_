/*
 * Media3-independent playback plan: the ordered segments (media and gaps) each track contributes.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate

/** One contiguous piece of a sequence. */
sealed interface PlanSegment {
    /** How long the segment occupies the timeline. */
    val durationMicros: Long

    /** Plays [durationMicros] of [uri] starting at [sourceStartMicros]; [sourceDurationMicros] is the full source. */
    data class Media(
        val uri: String,
        val kind: MediaKind,
        val sourceDurationMicros: Long,
        val sourceStartMicros: Long,
        override val durationMicros: Long,
    ) : PlanSegment

    /** Empty timeline span: black frames and silence on the base sequence, see-through on overlay sequences. */
    data class Gap(
        override val durationMicros: Long,
    ) : PlanSegment
}

/** Where an overlay clip sits on the canvas from [startMicros] until [endMicros] (exclusive), in timeline time. */
data class OverlaySpan(
    val startMicros: Long,
    val endMicros: Long,
    val transform: OverlayTransform,
)

/** Segments of one track in timeline order, starting at time zero; overlay tracks also carry their [overlaySpans]. */
data class SequencePlan(
    val segments: List<PlanSegment>,
    val overlaySpans: List<OverlaySpan> = emptyList(),
)

/** Everything the engine needs to build a composition from a project. */
data class PlaybackPlan(
    val sequences: List<SequencePlan>,
    val frameRate: FrameRate,
)

/** Converts project snapshots into playback plans. */
object PlaybackPlanner {
    /**
     * Plans every non-empty base video track of [project] and then every non-empty overlay track, filling implicit
     * gaps with [PlanSegment.Gap]. Base tracks come first so the first sequence sets the canvas.
     */
    fun plan(project: Project): PlaybackPlan {
        val planned = project.tracks.filter { it.clips.isNotEmpty() }
        val base = planned.filter { it.kind == TrackKind.VIDEO }.map { SequencePlan(segmentsOf(project, it)) }
        val overlays =
            planned
                .filter { it.kind == TrackKind.OVERLAY }
                .map { SequencePlan(segmentsOf(project, it), spansOf(it)) }
        return PlaybackPlan(sequences = base + overlays, frameRate = project.frameRate)
    }

    /** Returns the media and gap segments of [track] from time zero. */
    private fun segmentsOf(
        project: Project,
        track: Track,
    ): List<PlanSegment> {
        var cursor = 0L
        val segments = mutableListOf<PlanSegment>()
        track.clips.forEach { clip ->
            if (clip.startMicros > cursor) {
                segments += PlanSegment.Gap(clip.startMicros - cursor)
            }
            val asset = project.assetOf(clip)
            segments +=
                PlanSegment.Media(
                    uri = asset.uri,
                    kind = asset.kind,
                    sourceDurationMicros = asset.durationMicros ?: clip.durationMicros,
                    sourceStartMicros = clip.trimStartMicros,
                    durationMicros = clip.durationMicros,
                )
            cursor = clip.endMicros
        }
        return segments
    }

    /** Returns the canvas placement of every clip on [track], defaulting to full frame. */
    private fun spansOf(track: Track): List<OverlaySpan> =
        track.clips.map { clip ->
            OverlaySpan(clip.startMicros, clip.endMicros, clip.overlayTransform ?: OverlayTransform())
        }
}

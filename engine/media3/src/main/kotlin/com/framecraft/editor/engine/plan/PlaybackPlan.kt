/*
 * Media3-independent playback plan: the ordered segments (media and gaps) each track contributes.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
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

    /** Empty timeline span rendered as black frames and silence. */
    data class Gap(
        override val durationMicros: Long,
    ) : PlanSegment
}

/** Segments of one track in timeline order, starting at time zero. */
data class SequencePlan(
    val segments: List<PlanSegment>,
)

/** Everything the engine needs to build a composition from a project. */
data class PlaybackPlan(
    val sequences: List<SequencePlan>,
    val frameRate: FrameRate,
)

/** Converts project snapshots into playback plans. */
object PlaybackPlanner {
    /** Plans every non-empty video track of [project], filling implicit gaps with [PlanSegment.Gap]. */
    fun plan(project: Project): PlaybackPlan {
        val sequences =
            project.tracks
                .filter { it.kind == TrackKind.VIDEO && it.clips.isNotEmpty() }
                .map { track ->
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
                    SequencePlan(segments)
                }
        return PlaybackPlan(sequences = sequences, frameRate = project.frameRate)
    }
}

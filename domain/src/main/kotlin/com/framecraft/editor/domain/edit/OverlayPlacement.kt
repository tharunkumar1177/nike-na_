/*
 * Decides where a newly added overlay goes (at the playhead, attached to the base clip there) and where a dragged
 * overlay lands in time.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.AddOverlayClip
import com.framecraft.editor.domain.command.MoveOverlay
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import kotlin.math.abs

/** A time drag of an overlay: the [command] that saves it and the snap target it locked to, if any. */
data class OverlayMove(
    val command: MoveOverlay,
    val snappedMicros: Long?,
)

/** Places new overlays and moves existing ones in time. */
object OverlayPlacement {
    /**
     * Returns where overlay [clip] lands when dragged by [deltaMicros]: its start or end snaps to the nearest of
     * [targets] within [thresholdMicros], otherwise its start lands on a frame boundary. It attaches to the base
     * clip under its new start, or detaches over a gap.
     */
    fun drag(
        project: Project,
        clip: Clip,
        deltaMicros: Long,
        targets: List<Long>,
        thresholdMicros: Long,
    ): OverlayMove {
        val raw = (clip.startMicros + deltaMicros).coerceAtLeast(0L)
        val duration = clip.durationMicros
        val byStart = SnapPoints.nearest(targets, raw, thresholdMicros)?.let { it to it }
        val byEnd = SnapPoints.nearest(targets, raw + duration, thresholdMicros)?.let { (it - duration) to it }
        val snap = listOfNotNull(byStart, byEnd).filter { it.first >= 0 }.minByOrNull { abs(it.first - raw) }
        val start = snap?.first ?: project.frameRate.snapToNearestFrame(raw)
        val parent = baseTrack(project)?.clips?.firstOrNull { start >= it.startMicros && start < it.endMicros }
        val anchor = parent?.let { ClipAnchor(it.id, start - it.startMicros) }
        return OverlayMove(MoveOverlay(clip.id, anchor, start), snap?.second)
    }

    /**
     * Returns the command that adds [asset] as an overlay at [playheadMicros], or null when the project has no base
     * clip to attach it to. The overlay attaches to the base clip under the playhead (or the nearest one), starts on
     * a frame boundary inside that clip, and ends no later than the clip or the next overlay on the lane.
     */
    fun propose(
        project: Project,
        playheadMicros: Long,
        asset: MediaAsset,
        idGenerator: IdGenerator,
    ): AddOverlayClip? {
        val base = baseTrack(project) ?: return null
        val parent = parentAt(base, playheadMicros)
        val lane = overlayLane(project)
        val frame = project.frameRate.startOfFrame(1)
        val latestStart = maxOf(parent.startMicros, parent.endMicros - frame)
        val start = project.frameRate.snapToNearestFrame(playheadMicros).coerceIn(parent.startMicros, latestStart)
        val nextOverlay = lane?.clips?.firstOrNull { it.startMicros > start }?.startMicros ?: Long.MAX_VALUE
        val wanted = asset.durationMicros ?: ProjectFactory.DEFAULT_IMAGE_DURATION_MICROS
        val clip =
            Clip(
                id = idGenerator.next(),
                assetId = asset.id,
                startMicros = start,
                trimStartMicros = 0L,
                durationMicros = minOf(wanted, parent.endMicros - start, nextOverlay - start),
                anchor = ClipAnchor(parent.id, start - parent.startMicros),
                overlayTransform = OverlayTransform.DEFAULT,
            )
        return AddOverlayClip(lane?.id ?: idGenerator.next(), clip, asset)
    }

    /** Returns the first base video track that has clips, or null. */
    fun baseTrack(project: Project): Track? =
        project.tracks.firstOrNull { it.kind == TrackKind.VIDEO && it.clips.isNotEmpty() }

    /** Returns the overlay lane the editor uses, or null if the project has none yet. */
    fun overlayLane(project: Project): Track? = project.tracks.firstOrNull { it.kind == TrackKind.OVERLAY }

    /** Returns the clip of [base] under [timeMicros], or the clip whose nearest edge is closest to it. */
    fun parentAt(
        base: Track,
        timeMicros: Long,
    ): Clip =
        base.clips.firstOrNull { timeMicros >= it.startMicros && timeMicros < it.endMicros }
            ?: base.clips.minBy { minOf(abs(timeMicros - it.startMicros), abs(timeMicros - it.endMicros)) }
}

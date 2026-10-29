/*
 * Turns a trim-handle drag into clamped, snapped clip values that match what TrimClip accepts.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.TrimClip
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.time.FrameRate

/** Which end of a clip a trim handle moves. */
enum class TrimEdge {
    /** The clip's first frame. */
    START,

    /** The clip's last frame. */
    END,
}

/**
 * New clip values for a trim in progress. [snappedMicros] is the snap target the moving edge landed on, or null
 * when it landed on a plain frame boundary.
 */
data class TrimProposal(
    val startMicros: Long,
    val trimStartMicros: Long,
    val durationMicros: Long,
    val snappedMicros: Long?,
)

/**
 * Maps drag distances on the [edge] handle of [clip] to proposals. On a magnetic track the clip's start never moves,
 * so dragging either handle changes where the clip ends; on other tracks the start handle moves the start.
 */
class TrimController private constructor(
    private val track: Track,
    private val clip: Clip,
    private val sourceDurationMicros: Long?,
    private val frameRate: FrameRate,
    private val edge: TrimEdge,
) {
    /** The shortest a clip may become: one frame. */
    val minimumDurationMicros: Long = frameRate.startOfFrame(1)

    /** True when the moving edge is the clip's start; otherwise it is the clip's end. */
    private val movesStart = edge == TrimEdge.START && !track.magnetic

    /** True for image clips, which have no source length or offset to trim. */
    private val isImage = sourceDurationMicros == null

    /**
     * Returns the proposal for a drag of [deltaMicros] from where the gesture began. The moving edge snaps to the
     * closest of [targets] within [thresholdMicros], otherwise to the nearest frame boundary, and is then clamped.
     */
    fun propose(
        deltaMicros: Long,
        targets: List<Long>,
        thresholdMicros: Long,
    ): TrimProposal {
        val signedDelta = if (edge == TrimEdge.START && track.magnetic) -deltaMicros else deltaMicros
        val rawEdge = originalEdge() + signedDelta
        val target = SnapPoints.nearest(targets, rawEdge, thresholdMicros)
        val proposal = clampedAt(target ?: frameRate.snapToNearestFrame(rawEdge.coerceAtLeast(0L)))
        return proposal.copy(snappedMicros = target?.takeIf { it == edgeOf(proposal) })
    }

    /** Returns the time of the moving edge in [proposal]. */
    fun edgeOf(proposal: TrimProposal): Long =
        if (movesStart) proposal.startMicros else proposal.startMicros + proposal.durationMicros

    /** Returns the command that applies [proposal]. */
    fun commandFor(proposal: TrimProposal): TrimClip =
        TrimClip(clip.id, proposal.startMicros, proposal.trimStartMicros, proposal.durationMicros)

    /** Returns the moving edge's time before the drag. */
    private fun originalEdge(): Long = if (movesStart) clip.startMicros else clip.endMicros

    /** Returns the proposal with the moving edge at [edgeMicros], clamped to the source, neighbors, and one frame. */
    private fun clampedAt(edgeMicros: Long): TrimProposal =
        when {
            movesStart -> startAt(edgeMicros)
            edge == TrimEdge.END -> endAt(edgeMicros)
            else -> pinnedStartEndAt(edgeMicros)
        }

    /** Moves the start of a clip on a free track, keeping its end. */
    private fun startAt(edgeMicros: Long): TrimProposal {
        val previousEnd = track.clips.lastOrNull { it.endMicros <= clip.startMicros }?.endMicros ?: 0L
        val sourceStart = if (isImage) 0L else clip.startMicros - clip.trimStartMicros
        val lowest = maxOf(previousEnd, sourceStart)
        val start = edgeMicros.coerceIn(lowest, maxOf(lowest, clip.endMicros - minimumDurationMicros))
        val trimStart = if (isImage) clip.trimStartMicros else clip.trimStartMicros + start - clip.startMicros
        return TrimProposal(start, trimStart, clip.endMicros - start, snappedMicros = null)
    }

    /** Moves the end of a clip, keeping its start and source offset. */
    private fun endAt(edgeMicros: Long): TrimProposal {
        val sourceLimit = sourceDurationMicros?.let { it - clip.trimStartMicros } ?: Long.MAX_VALUE
        val next = if (track.magnetic) null else track.clips.firstOrNull { it.startMicros >= clip.endMicros }
        val neighborLimit = next?.let { it.startMicros - clip.startMicros } ?: Long.MAX_VALUE
        return TrimProposal(
            startMicros = clip.startMicros,
            trimStartMicros = clip.trimStartMicros,
            durationMicros = durationEndingAt(edgeMicros, minOf(sourceLimit, neighborLimit)),
            snappedMicros = null,
        )
    }

    /** Trims the start of a clip on a magnetic track: the start stays, the source offset and the end move. */
    private fun pinnedStartEndAt(edgeMicros: Long): TrimProposal {
        val maximum = if (isImage) Long.MAX_VALUE else clip.durationMicros + clip.trimStartMicros
        val duration = durationEndingAt(edgeMicros, maximum)
        val trimStart = if (isImage) clip.trimStartMicros else clip.trimStartMicros + clip.durationMicros - duration
        return TrimProposal(clip.startMicros, trimStart, duration, snappedMicros = null)
    }

    /** Returns the duration that ends the clip at [edgeMicros], kept between one frame and [maximumMicros]. */
    private fun durationEndingAt(
        edgeMicros: Long,
        maximumMicros: Long,
    ): Long {
        val upper = maxOf(minimumDurationMicros, maximumMicros)
        return (edgeMicros - clip.startMicros).coerceIn(minimumDurationMicros, upper)
    }

    /** Construction from model objects. */
    companion object {
        /** Returns a controller for the [edge] handle of [clip] on [track], which reads [asset] at [frameRate]. */
        fun forClip(
            track: Track,
            clip: Clip,
            asset: MediaAsset,
            frameRate: FrameRate,
            edge: TrimEdge,
        ): TrimController = TrimController(track, clip, asset.durationMicros, frameRate, edge)
    }
}

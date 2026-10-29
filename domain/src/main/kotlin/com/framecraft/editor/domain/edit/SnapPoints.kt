/*
 * Snap targets for timeline drags: the playhead and the edges of clips that stay put during the drag.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Track
import kotlin.math.abs

/** Finds the times a dragged edge may snap to. */
object SnapPoints {
    /**
     * Returns [playheadMicros] and the edges of clips on [track] that do not move while [dragged] changes:
     * every other clip, except later clips on a magnetic track, which shift with the edit.
     */
    fun of(
        track: Track,
        playheadMicros: Long,
        dragged: Clip,
    ): List<Long> {
        val fixed =
            track.clips.filter { clip ->
                clip.id != dragged.id && !(track.magnetic && clip.startMicros > dragged.startMicros)
            }
        val edges = fixed.flatMap { listOf(it.startMicros, it.endMicros) }
        return (edges + playheadMicros).distinct().sorted()
    }

    /** Returns the target closest to [timeMicros] if it is within [thresholdMicros], or null. */
    fun nearest(
        targets: List<Long>,
        timeMicros: Long,
        thresholdMicros: Long,
    ): Long? {
        val closest = targets.minByOrNull { abs(it - timeMicros) }
        return closest?.takeIf { abs(it - timeMicros) <= thresholdMicros }
    }
}

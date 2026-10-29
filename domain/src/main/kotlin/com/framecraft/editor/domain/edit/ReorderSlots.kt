/*
 * Drop positions for reordering a clip on a magnetic track.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.model.Track
import kotlin.math.abs

/** Finds where a clip being reordered can land. */
object ReorderSlots {
    /**
     * Returns the starts a clip [clipId] may move to on magnetic [track], in the time of the track with that clip
     * taken out: zero and the end of every remaining clip. These are the starts a move command expects.
     */
    fun of(
        track: Track,
        clipId: String,
    ): List<Long> =
        track.clips
            .filter { it.id != clipId }
            .runningFold(0L) { end, clip -> end + clip.durationMicros }

    /** Returns the slot in [slots] closest to [timeMicros]; [slots] must not be empty. */
    fun nearest(
        slots: List<Long>,
        timeMicros: Long,
    ): Long = slots.minBy { abs(it - timeMicros) }
}

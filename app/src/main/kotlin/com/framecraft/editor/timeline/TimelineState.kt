/*
 * UI state of the timeline strip: zoom, the time under the playhead while scrubbing, and whether a scrub is active.
 */
package com.framecraft.editor.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Mutable timeline view state; while not scrubbing, the playhead follows playback instead of [centerMicros]. */
@Stable
class TimelineState(
    initialDpPerSecond: Float,
) {
    /** Time under the playhead during a scrub. */
    var centerMicros by mutableLongStateOf(0L)

    /** Current zoom in dp per second. */
    var dpPerSecond by mutableFloatStateOf(TimelineGeometry.clampZoom(initialDpPerSecond))

    /** True while the user's finger is on the timeline. */
    var scrubbing by mutableStateOf(false)
        private set

    /** Starts a scrub from [fromMicros]. */
    fun beginScrub(fromMicros: Long) {
        centerMicros = fromMicros
        scrubbing = true
    }

    /** Ends the scrub so the playhead follows playback again. */
    fun endScrub() {
        scrubbing = false
    }
}

/** Remembers a [TimelineState] per project id. */
@Composable
fun rememberTimelineState(projectId: String): TimelineState =
    remember(projectId) { TimelineState(TimelineGeometry.DEFAULT_DP_PER_SECOND) }

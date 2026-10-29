/*
 * UI state of the timeline strip: zoom, snapping, the time under the playhead while touched, and drag previews.
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
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project

/** A clip lifted for reordering, drawn on row [rowIndex] with its left edge at [leftPx] under the finger. */
data class FloatingClip(
    val clip: Clip,
    val rowIndex: Int,
    val leftPx: Float,
)

/**
 * What a drag in progress would make of [base]: the timeline draws [project] instead, plus the lifted [floating]
 * clip and a guideline at [snapMicros]. [settling] previews wait for the saved edit to replace [base].
 */
data class TimelinePreview(
    val base: Project,
    val project: Project,
    val floating: FloatingClip? = null,
    val snapMicros: Long? = null,
    val settling: Boolean = false,
)

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

    /** True when dragged edges snap to the playhead and clip edges as well as to frames. */
    var snapEnabled by mutableStateOf(true)

    /** The zoom to return to after zoom to playhead, or null when zoom to playhead is off. */
    var zoomBeforePlayhead by mutableStateOf<Float?>(null)

    /** The drag in progress or waiting to be saved, or null. */
    var preview by mutableStateOf<TimelinePreview?>(null)

    /** Starts a scrub from [fromMicros]. */
    fun beginScrub(fromMicros: Long) {
        centerMicros = fromMicros
        scrubbing = true
    }

    /** Ends the scrub so the playhead follows playback again. */
    fun endScrub() {
        scrubbing = false
    }

    /** Sets the zoom from a pinch or zoom to fit, which turns zoom to playhead off. */
    fun zoomTo(dpPerSecond: Float) {
        this.dpPerSecond = TimelineGeometry.clampZoom(dpPerSecond)
        zoomBeforePlayhead = null
    }

    /** Zooms all the way in around the playhead, or back to the previous zoom when already zoomed in. */
    fun toggleZoomToPlayhead() {
        val previous = zoomBeforePlayhead
        if (previous == null) {
            zoomBeforePlayhead = dpPerSecond
            dpPerSecond = TimelineGeometry.MAX_DP_PER_SECOND
        } else {
            zoomTo(previous)
        }
    }
}

/** Remembers a [TimelineState] per project id. */
@Composable
fun rememberTimelineState(projectId: String): TimelineState =
    remember(projectId) { TimelineState(TimelineGeometry.DEFAULT_DP_PER_SECOND) }

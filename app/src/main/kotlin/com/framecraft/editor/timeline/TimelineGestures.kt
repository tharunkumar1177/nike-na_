/*
 * Timeline touch handling: trim handles, long-press reordering, scrubbing, pinch zoom, and tap selection.
 */
package com.framecraft.editor.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.MoveClip
import com.framecraft.editor.domain.command.TrimClip
import com.framecraft.editor.domain.edit.ReorderSlots
import com.framecraft.editor.domain.edit.SnapPoints
import com.framecraft.editor.domain.edit.TrimController
import com.framecraft.editor.domain.edit.TrimEdge
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import kotlin.math.abs
import kotlin.math.roundToLong

/** What timeline edits report: tap selection [onSelect], one saved edit per drag [onCommit], and [onHaptic]. */
class TimelineEditCallbacks(
    val onSelect: (String?) -> Unit,
    val onCommit: (EditCommand) -> Unit,
    val onHaptic: (HapticFeedbackType) -> Unit,
)

/** A clip on row [trackIndex] of the timeline. */
internal class ClipTarget(
    val trackIndex: Int,
    val track: Track,
    val clip: Clip,
)

/** How a touch on a clip resolved before the long-press timeout. */
private enum class Hold {
    LONG_PRESS,
    LIFTED,
    MOVED,
}

/** The state and callbacks one gesture works with; [selectedClipId] reads the current selection. */
internal class TimelineGestureContext(
    val state: TimelineState,
    val project: Project,
    val edits: TimelineEditCallbacks,
    val selectedClipId: () -> String?,
) {
    /** Shows what [command] would make of the project, with an optional guideline at [snapMicros]. */
    fun preview(
        command: EditCommand,
        snapMicros: Long? = null,
        floating: FloatingClip? = null,
    ) {
        val applied = command.applyTo(project) as? CommandOutcome.Applied
        state.preview = applied?.let { TimelinePreview(project, it.project, floating, snapMicros) }
    }

    /** Ends a drag: saves [command], keeping its preview until the saved project arrives, or drops the preview. */
    fun finish(command: EditCommand?) {
        if (command == null) {
            state.preview = null
        } else {
            state.preview = state.preview?.copy(floating = null, snapMicros = null, settling = true)
            edits.onCommit(command)
        }
    }

    /** Returns the selected clip and its row, or null when nothing on the timeline is selected. */
    fun selectedTarget(): ClipTarget? {
        val id = selectedClipId() ?: return null
        val row = project.tracks.indexOfFirst { track -> track.clips.any { it.id == id } }
        return project.tracks.getOrNull(row)?.let { track -> ClipTarget(row, track, track.clips.first { it.id == id }) }
    }
}

/**
 * Handles one gesture at a time. A touch in a trim handle of the selected clip trims it; holding a clip on a
 * magnetic track lifts it for reordering; otherwise one finger scrubs, two fingers zoom around the playhead, and a
 * tap selects the clip under it or clears the selection.
 */
internal suspend fun PointerInputScope.timelineGestures(
    context: TimelineGestureContext,
    positionMicros: () -> Long,
    scrub: ScrubCallbacks,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        context.state.beginScrub(positionMicros())
        scrub.onStart()
        val trim = trimHandleAt(context, down.position)
        if (trim != null) {
            trimDrag(context, trim.first, trim.second, down)
        } else {
            holdOrScrub(context, down, scrub)
        }
        context.state.endScrub()
        scrub.onEnd()
    }
}

/**
 * Handles a touch that is not on a trim handle: a touch on an overlay selects or drags it; otherwise waits to see
 * whether the touch is a hold, a tap, or a scrub.
 */
private suspend fun AwaitPointerEventScope.holdOrScrub(
    context: TimelineGestureContext,
    down: PointerInputChange,
    scrub: ScrubCallbacks,
) {
    val hit = clipAt(context, down.position)
    val target = hit?.takeIf { it.track.magnetic }
    val hold = if (target == null) Hold.MOVED else awaitHold(down)
    when {
        hit != null && hit.track.kind == TrackKind.OVERLAY -> {
            overlayDrag(context, hit, down)
        }

        hold == Hold.LONG_PRESS && target != null -> {
            reorderDrag(context, target, down)
        }

        hold == Hold.LIFTED -> {
            context.edits.onSelect(target?.clip?.id)
        }

        else -> {
            val tapped = scrubLoop(context, scrub)
            if (tapped && target == null) context.edits.onSelect(hit?.clip?.id)
        }
    }
}

/** Returns how the touch [down] resolved: still held after the long-press timeout, lifted, or moved. */
private suspend fun AwaitPointerEventScope.awaitHold(down: PointerInputChange): Hold =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var hold: Hold? = null
        while (hold == null) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            hold =
                when {
                    change == null || event.changes.size > 1 -> Hold.MOVED
                    !change.pressed -> Hold.LIFTED
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> Hold.MOVED
                    else -> null
                }
        }
        hold
    } ?: Hold.LONG_PRESS

/** Scrubs with one finger and zooms with two until all fingers lift; returns true if the touch was a tap. */
private suspend fun AwaitPointerEventScope.scrubLoop(
    context: TimelineGestureContext,
    scrub: ScrubCallbacks,
): Boolean {
    val state = context.state
    val project = context.project
    var lastSeek = state.centerMicros
    var travelPx = 0f
    var multiTouch = false
    do {
        val event = awaitPointerEvent()
        if (event.changes.size > 1) {
            multiTouch = true
            val zoom = event.calculateZoom()
            if (zoom != 1f) state.zoomTo(state.dpPerSecond * zoom)
        } else {
            val pan = event.calculatePan().x
            travelPx += abs(pan)
            val delta = (-pan / state.dpPerSecond.dp.toPx() * MICROS_PER_SECOND).roundToLong()
            state.centerMicros = (state.centerMicros + delta).coerceIn(0L, project.durationMicros)
            val snapped = project.frameRate.snapToNearestFrame(state.centerMicros)
            if (snapped != lastSeek) {
                lastSeek = snapped
                scrub.onSeek(snapped)
            }
        }
        event.changes.forEach { if (it.positionChanged()) it.consume() }
    } while (event.changes.any { it.pressed })
    return !multiTouch && travelPx <= viewConfiguration.touchSlop
}

/** Drags the [edge] handle of [target], previewing each move and saving one trim when the finger lifts. */
private suspend fun AwaitPointerEventScope.trimDrag(
    context: TimelineGestureContext,
    target: ClipTarget,
    edge: TrimEdge,
    down: PointerInputChange,
) {
    val state = context.state
    val clip = target.clip
    val asset = context.project.assetOf(clip)
    val controller = TrimController.forClip(target.track, clip, asset, context.project.frameRate, edge)
    val pxPerSecond = state.dpPerSecond.dp.toPx()
    val targets = if (state.snapEnabled) SnapPoints.of(target.track, state.centerMicros, clip) else emptyList()
    val threshold = (TimelineMetrics(this).snapThreshold / pxPerSecond * MICROS_PER_SECOND).roundToLong()
    val unchanged = TrimClip(clip.id, clip.startMicros, clip.trimStartMicros, clip.durationMicros)
    var command = unchanged
    var lastSnap: Long? = null
    var atMinimum = false
    awaitDrag(down) { position ->
        val deltaMicros = ((position.x - down.position.x) / pxPerSecond * MICROS_PER_SECOND).roundToLong()
        val proposal = controller.propose(deltaMicros, targets, threshold)
        val snapped = proposal.snappedMicros
        if (snapped != null && snapped != lastSnap) context.edits.onHaptic(HapticFeedbackType.TextHandleMove)
        lastSnap = snapped
        val minimum = proposal.durationMicros == controller.minimumDurationMicros
        if (minimum && !atMinimum) context.edits.onHaptic(HapticFeedbackType.LongPress)
        atMinimum = minimum
        command = controller.commandFor(proposal)
        context.preview(command, snapMicros = snapped)
    }
    context.finish(command.takeUnless { it == unchanged })
}

/** Lifts [target] and moves it between slots of its magnetic track, saving one move when the finger lifts. */
private suspend fun AwaitPointerEventScope.reorderDrag(
    context: TimelineGestureContext,
    target: ClipTarget,
    down: PointerInputChange,
) {
    val state = context.state
    val clip = target.clip
    context.edits.onHaptic(HapticFeedbackType.LongPress)
    context.edits.onSelect(clip.id)
    val pxPerSecond = state.dpPerSecond.dp.toPx()
    val slots = ReorderSlots.of(target.track, clip.id)
    val grabMicros = timeAt(state, down.position.x) - clip.startMicros
    var slot = clip.startMicros

    /** Moves the lifted clip under the finger at [position] and previews it in the nearest slot. */
    fun follow(position: Offset) {
        val next = ReorderSlots.nearest(slots, timeAt(state, position.x) - grabMicros)
        if (next != slot) context.edits.onHaptic(HapticFeedbackType.TextHandleMove)
        slot = next
        val leftPx = position.x - grabMicros * pxPerSecond / MICROS_PER_SECOND
        context.preview(MoveClip(clip.id, slot), floating = FloatingClip(clip, target.trackIndex, leftPx))
    }
    follow(down.position)
    awaitDrag(down, ::follow)
    context.finish(MoveClip(clip.id, slot).takeIf { slot != clip.startMicros })
}

/** Reports each new position of the pointer [down] to [onMove] until every finger lifts; others are ignored. */
internal suspend fun AwaitPointerEventScope.awaitDrag(
    down: PointerInputChange,
    onMove: (Offset) -> Unit,
) {
    do {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change != null && change.positionChanged()) onMove(change.position)
        event.changes.forEach { if (it.positionChanged()) it.consume() }
    } while (event.changes.any { it.pressed })
}

/** Returns the timeline time under horizontal position [x]. */
private fun AwaitPointerEventScope.timeAt(
    state: TimelineState,
    x: Float,
): Long = TimelineGeometry.xToTime(x, state.centerMicros, state.dpPerSecond.dp.toPx(), size.width.toFloat())

/** Returns the clip drawn under [position], or null for the ruler, gaps, and empty space. */
private fun AwaitPointerEventScope.clipAt(
    context: TimelineGestureContext,
    position: Offset,
): ClipTarget? {
    val metrics = TimelineMetrics(this)
    val row = TimelineGeometry.rowAt(position.y, metrics.rulerHeight, metrics.rowHeight, metrics.rowGap)
    val tracks = context.project.tracks
    return row?.let { index ->
        tracks.getOrNull(index)?.let { track ->
            TimelineGeometry.clipAt(track, timeAt(context.state, position.x))?.let { ClipTarget(index, track, it) }
        }
    }
}

/** Returns the selected clip and the trim handle whose touch zone contains [position], or null. */
private fun AwaitPointerEventScope.trimHandleAt(
    context: TimelineGestureContext,
    position: Offset,
): Pair<ClipTarget, TrimEdge>? {
    val metrics = TimelineMetrics(this)
    val row = TimelineGeometry.rowAt(position.y, metrics.rulerHeight, metrics.rowHeight, metrics.rowGap)
    val state = context.state
    val pxPerSecond = state.dpPerSecond.dp.toPx()
    val width = size.width.toFloat()
    return context.selectedTarget()?.takeIf { it.trackIndex == row }?.let { target ->
        val left = TimelineGeometry.timeToX(target.clip.startMicros, state.centerMicros, pxPerSecond, width)
        val right = TimelineGeometry.timeToX(target.clip.endMicros, state.centerMicros, pxPerSecond, width)
        TimelineGeometry.trimEdgeAt(position.x, left, right, metrics.handleZone)?.let { edge -> target to edge }
    }
}

/*
 * Timeline handling for overlay clips: tap to select, drag to move in time with snapping and re-attachment, and
 * TalkBack actions that nudge the selected overlay by one frame.
 */
package com.framecraft.editor.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.MoveClip
import com.framecraft.editor.domain.command.MoveOverlay
import com.framecraft.editor.domain.edit.OverlayPlacement
import com.framecraft.editor.domain.edit.SnapPoints
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Handles a touch on overlay [target]. Lifting without moving selects it; moving past the touch slop selects it and
 * drags it in time. The start or end snaps to the playhead and clip edges, the overlay attaches to the base clip
 * under its new start or detaches over a gap, and one move is saved when the finger lifts.
 */
internal suspend fun AwaitPointerEventScope.overlayDrag(
    context: TimelineGestureContext,
    target: ClipTarget,
    down: PointerInputChange,
) {
    val state = context.state
    val project = context.project
    val clip = target.clip
    val pxPerSecond = state.dpPerSecond.dp.toPx()
    val base = OverlayPlacement.baseTrack(project)
    val targets =
        if (state.snapEnabled) SnapPoints.ofOverlay(base, target.track, state.centerMicros, clip) else emptyList()
    val threshold = (TimelineMetrics(this).snapThreshold / pxPerSecond * MICROS_PER_SECOND).roundToLong()
    var command: MoveOverlay? = null
    var lastSnap: Long? = null
    var dragging = false
    awaitDrag(down) { position ->
        val dx = position.x - down.position.x
        if (!dragging && abs(dx) > viewConfiguration.touchSlop) {
            dragging = true
            context.edits.onSelect(clip.id)
        }
        if (dragging) {
            val deltaMicros = (dx / pxPerSecond * MICROS_PER_SECOND).roundToLong()
            val move = OverlayPlacement.drag(project, clip, deltaMicros, targets, threshold)
            val snapped = move.snappedMicros
            if (snapped != null && snapped != lastSnap) context.edits.onHaptic(HapticFeedbackType.TextHandleMove)
            lastSnap = snapped
            command = move.command
            context.preview(move.command, snapMicros = snapped)
        }
    }
    if (!dragging) context.edits.onSelect(clip.id)
    context.finish(command?.takeUnless { it.startMicros == clip.startMicros && it.anchor == clip.anchor })
}

/**
 * Returns TalkBack actions that move the selected clip [selectedClipId] of [project] one frame earlier or later
 * through [onEdit], or none when the selection is neither an overlay nor text, or is a caption, whose timing follows
 * its speech. Earlier is left out at time zero.
 */
@Composable
internal fun overlayNudges(
    project: Project,
    selectedClipId: String?,
    onEdit: (EditCommand) -> Unit,
): List<CustomAccessibilityAction> {
    val earlier = stringResource(R.string.timeline_nudge_earlier)
    val later = stringResource(R.string.timeline_nudge_later)
    val clip =
        project.tracks
            .filter { it.kind.isPlaced }
            .firstNotNullOfOrNull { track -> track.clips.firstOrNull { it.id == selectedClipId } }
            ?.takeIf { it.text?.caption == null }
    return clip?.let { overlay ->
        /** Returns an action labelled [label] that moves the overlay by [frames] frames. */
        fun nudge(
            label: String,
            frames: Long,
        ): CustomAccessibilityAction =
            CustomAccessibilityAction(label) {
                onEdit(MoveClip(overlay.id, project.frameRate.stepFrames(overlay.startMicros, frames)))
                true
            }
        listOfNotNull(nudge(earlier, -1L).takeIf { overlay.startMicros > 0L }, nudge(later, 1L))
    } ?: emptyList()
}

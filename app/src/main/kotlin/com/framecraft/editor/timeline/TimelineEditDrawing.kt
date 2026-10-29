/*
 * Canvas drawing for timeline edits: trim handles on the selected clip, a clip lifted for reordering, and snapping.
 */
package com.framecraft.editor.timeline

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.framecraft.editor.domain.time.MICROS_PER_SECOND

/** Opacity of a clip lifted for reordering. */
private const val FLOATING_ALPHA = 0.8f

/** Draws the start and end grips inside the outline of the selected clip [clipId]; nothing for unknown ids. */
fun DrawScope.drawTrimHandles(
    frame: TimelineFrame,
    clipId: String?,
) {
    val index = frame.project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
    val track = frame.project.tracks.getOrNull(index) ?: return
    val clip = track.clips.first { it.id == clipId }
    val metrics = frame.metrics
    val inset = metrics.clipInset + metrics.selectionStroke
    val top = metrics.rowTop(index) + (metrics.rowHeight - metrics.handleHeight) / 2f
    val size = Size(metrics.handleWidth, metrics.handleHeight)
    val corner = CornerRadius(metrics.handleWidth / 2f)
    val color = frame.style.colors.selection
    drawRoundRect(color, Offset(x(frame, clip.startMicros) + inset, top), size, corner)
    drawRoundRect(color, Offset(x(frame, clip.endMicros) - inset - metrics.handleWidth, top), size, corner)
}

/** Draws [floating] under the finger and a line where it will land; nothing when [floating] is null. */
fun DrawScope.drawFloatingClip(
    frame: TimelineFrame,
    floating: FloatingClip?,
) {
    if (floating == null) return
    val metrics = frame.metrics
    val top = metrics.rowTop(floating.rowIndex)
    val color = frame.style.colors.selection
    val row = frame.project.tracks.getOrNull(floating.rowIndex)
    val landing = row?.clips?.firstOrNull { it.id == floating.clip.id }
    if (landing != null) {
        val slotX = x(frame, landing.startMicros)
        drawLine(color, Offset(slotX, top), Offset(slotX, top + metrics.rowHeight), metrics.selectionStroke)
    }
    val width = floating.clip.durationMicros * frame.pxPerSecond / MICROS_PER_SECOND
    val size = Size(width, metrics.rowHeight)
    val corner = CornerRadius(metrics.clipCorner)
    drawRoundRect(frame.style.colors.clip, Offset(floating.leftPx, top), size, corner, alpha = FLOATING_ALPHA)
    drawRoundRect(color, Offset(floating.leftPx, top), size, corner, style = Stroke(metrics.selectionStroke))
}

/** Draws a full-height guideline at [snapMicros], the target a dragged edge snapped to; nothing when null. */
fun DrawScope.drawSnapGuide(
    frame: TimelineFrame,
    snapMicros: Long?,
) {
    if (snapMicros == null) return
    val guideX = x(frame, snapMicros)
    val color = frame.style.colors.selection
    drawLine(color, Offset(guideX, 0f), Offset(guideX, size.height), frame.metrics.guideWidth)
}

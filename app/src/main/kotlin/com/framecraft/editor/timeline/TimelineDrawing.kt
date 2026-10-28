/*
 * Canvas drawing for the timeline: ruler, clip rows with thumbnails, and the fixed center playhead.
 */
package com.framecraft.editor.timeline

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import com.framecraft.editor.domain.time.Timecode
import com.framecraft.editor.engine.ThumbnailSource
import kotlin.math.roundToInt

/** Height of one track row. */
val TimelineRowHeight = 64.dp

/** Opacity of the scrim behind clip duration labels. */
private const val LABEL_SCRIM_ALPHA = 0.4f

/** Colors used to draw the timeline. */
data class TimelineColors(
    val ruler: Color,
    val clip: Color,
    val clipLabel: Color,
    val playhead: Color,
)

/** Colors, text, and icons used to draw the timeline. */
class TimelineStyle(
    val colors: TimelineColors,
    val textMeasurer: TextMeasurer,
    val labelStyle: TextStyle,
    val brokenImage: Painter,
)

/** Pixel sizes derived from the design's dp values for the current density. */
class TimelineMetrics(
    scope: DrawScope,
) {
    /** Height of the time ruler. */
    val rulerHeight = with(scope) { 32.dp.toPx() }

    /** Height of one track row. */
    val rowHeight = with(scope) { TimelineRowHeight.toPx() }

    /** Vertical gap between rows and below the ruler. */
    val rowGap = with(scope) { 8.dp.toPx() }

    /** Corner radius of clip blocks. */
    val clipCorner = with(scope) { 8.dp.toPx() }

    /** Horizontal inset that keeps adjacent clips visually separate. */
    val clipInset = with(scope) { 1.dp.toPx() }

    /** Minimum distance between ruler ticks. */
    val minTickSpacing = with(scope) { 64.dp.toPx() }

    /** Padding around labels. */
    val labelPadding = with(scope) { 4.dp.toPx() }

    /** Width of the playhead line. */
    val playheadWidth = with(scope) { 2.dp.toPx() }

    /** Radius of the playhead knob in the ruler. */
    val playheadKnobRadius = with(scope) { 6.dp.toPx() }

    /** Stroke width of ruler ticks. */
    val tickWidth = with(scope) { 1.dp.toPx() }

    /** Length of labelled ruler ticks; unlabelled ticks are half as long. */
    val tickLength = with(scope) { 12.dp.toPx() }

    /** Size of the icon shown when a thumbnail failed. */
    val iconSize = with(scope) { 24.dp.toPx() }

    /** Returns the top edge of track row [index]. */
    fun rowTop(index: Int): Float = rulerHeight + rowGap + index * (rowHeight + rowGap)
}

/** Everything needed to draw one frame of the timeline. */
class TimelineFrame(
    val project: Project,
    val centerMicros: Long,
    val pxPerSecond: Float,
    val metrics: TimelineMetrics,
    val style: TimelineStyle,
    val thumbnails: ThumbnailSource,
)

/** Returns the x position of [micros] in this frame. */
private fun DrawScope.x(
    frame: TimelineFrame,
    micros: Long,
): Float = TimelineGeometry.timeToX(micros, frame.centerMicros, frame.pxPerSecond, size.width)

/** Draws ruler ticks and whole-second labels across the visible range. */
fun DrawScope.drawRuler(frame: TimelineFrame) {
    val metrics = frame.metrics
    val style = frame.style
    val interval =
        TimelineGeometry.tickIntervalMicros(frame.pxPerSecond, metrics.minTickSpacing, frame.project.frameRate)
    val range = TimelineGeometry.visibleRange(frame.centerMicros, frame.pxPerSecond, size.width)
    var tick = range.first / interval * interval
    while (tick <= range.last) {
        val labelled = interval >= MICROS_PER_SECOND || tick % MICROS_PER_SECOND == 0L
        val length = if (labelled) metrics.tickLength else metrics.tickLength / 2f
        val tickX = x(frame, tick)
        drawLine(
            color = style.colors.ruler,
            start = Offset(tickX, metrics.rulerHeight - length),
            end = Offset(tickX, metrics.rulerHeight),
            strokeWidth = metrics.tickWidth,
        )
        if (labelled) {
            drawText(
                textMeasurer = style.textMeasurer,
                text = Timecode.format(tick),
                topLeft = Offset(tickX + metrics.labelPadding, metrics.labelPadding),
                style = style.labelStyle.copy(color = style.colors.ruler),
            )
        }
        tick += interval
    }
}

/** Draws the visible clips of [track] in the row starting at [top]. */
fun DrawScope.drawTrack(
    frame: TimelineFrame,
    track: Track,
    top: Float,
) {
    val metrics = frame.metrics
    val range = TimelineGeometry.visibleRange(frame.centerMicros, frame.pxPerSecond, size.width)
    track.clips
        .filter { it.endMicros >= range.first && it.startMicros <= range.last }
        .forEach { clip ->
            val left = x(frame, clip.startMicros) + metrics.clipInset
            val width = x(frame, clip.endMicros) - metrics.clipInset - left
            if (width > 0f) {
                val block = RoundRect(left, top, left + width, top + metrics.rowHeight, CornerRadius(metrics.clipCorner))
                clipPath(Path().apply { addRoundRect(block) }) {
                    drawRect(color = frame.style.colors.clip, topLeft = Offset(left, top), size = block.size())
                    drawThumbnails(frame, clip, top)
                }
                drawClipLabel(frame, TimelineGeometry.formatClipDuration(clip.durationMicros), left, width, top)
            }
        }
}

/** Returns the width and height of this rectangle. */
private fun RoundRect.size(): Size = Size(width, height)

/** Draws loaded thumbnails of [clip]; shows a broken-image icon when any visible thumbnail failed. */
private fun DrawScope.drawThumbnails(
    frame: TimelineFrame,
    clip: Clip,
    top: Float,
) {
    val metrics = frame.metrics
    val viewport = TimelineViewport(frame.centerMicros, frame.pxPerSecond, size.width, metrics.rowHeight)
    val tiles = clipThumbnails(frame.project, clip, viewport)
    tiles.forEach { (tile, key, fullWidth) ->
        val bitmap = frame.thumbnails.get(key) ?: return@forEach
        val sourceWidth = (bitmap.width * tile.widthPx / fullWidth).roundToInt().coerceIn(1, bitmap.width)
        drawImage(
            image = bitmap.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sourceWidth, bitmap.height),
            dstOffset = IntOffset(tile.leftPx.roundToInt(), top.roundToInt()),
            dstSize = IntSize(tile.widthPx.roundToInt(), metrics.rowHeight.roundToInt()),
        )
    }
    val failedTile = tiles.firstOrNull { frame.thumbnails.hasFailed(it.key) }?.tile
    if (failedTile != null) {
        val iconLeft = failedTile.leftPx + (failedTile.widthPx - metrics.iconSize) / 2f
        val iconTop = top + (metrics.rowHeight - metrics.iconSize) / 2f
        translate(iconLeft, iconTop) {
            with(frame.style.brokenImage) {
                draw(Size(metrics.iconSize, metrics.iconSize), colorFilter = ColorFilter.tint(frame.style.colors.ruler))
            }
        }
    }
}

/** Draws [text] on a scrim at the bottom-right corner of the clip spanning [clipLeft] to [clipLeft] + [clipWidth]. */
private fun DrawScope.drawClipLabel(
    frame: TimelineFrame,
    text: String,
    clipLeft: Float,
    clipWidth: Float,
    top: Float,
) {
    val metrics = frame.metrics
    val style = frame.style
    val layout = style.textMeasurer.measure(text, style.labelStyle)
    val labelWidth = layout.size.width + metrics.labelPadding * 2
    val labelHeight = layout.size.height + metrics.labelPadding
    val left = clipLeft + clipWidth - labelWidth - metrics.labelPadding
    val labelTop = top + metrics.rowHeight - labelHeight - metrics.labelPadding
    if (left > clipLeft + metrics.labelPadding && labelTop > top) {
        drawRoundRect(
            color = Color.Black.copy(alpha = LABEL_SCRIM_ALPHA),
            topLeft = Offset(left, labelTop),
            size = Size(labelWidth, labelHeight),
            cornerRadius = CornerRadius(metrics.labelPadding),
        )
        drawText(
            textLayoutResult = layout,
            color = style.colors.clipLabel,
            topLeft = Offset(left + metrics.labelPadding, labelTop + metrics.labelPadding / 2f),
        )
    }
}

/** Draws the fixed playhead line and its knob in the ruler. */
fun DrawScope.drawPlayhead(frame: TimelineFrame) {
    val metrics = frame.metrics
    val centerX = size.width / 2f
    drawLine(
        color = frame.style.colors.playhead,
        start = Offset(centerX, 0f),
        end = Offset(centerX, size.height),
        strokeWidth = metrics.playheadWidth,
    )
    drawCircle(
        color = frame.style.colors.playhead,
        radius = metrics.playheadKnobRadius,
        center = Offset(centerX, metrics.rulerHeight - metrics.playheadKnobRadius),
    )
}

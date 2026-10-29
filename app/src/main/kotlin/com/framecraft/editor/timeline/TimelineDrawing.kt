/*
 * Canvas drawing for the timeline: ruler, clip rows with thumbnails or waveforms, and the fixed center playhead.
 */
package com.framecraft.editor.timeline

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import com.framecraft.editor.domain.time.Timecode
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
    val selection: Color,
)

/**
 * How overlay clips look: their fill [clip], and the [link] badge for attached overlays or the [linkOff] badge in
 * [linkOffTint] for free ones.
 */
class OverlayStyle(
    val clip: Color,
    val link: Painter,
    val linkOff: Painter,
    val linkOffTint: Color,
)

/** Colors, text, and icons used to draw the timeline. */
class TimelineStyle(
    val colors: TimelineColors,
    val textMeasurer: TextMeasurer,
    val labelStyle: TextStyle,
    val brokenImage: Painter,
    val overlay: OverlayStyle,
    val audio: AudioStyle,
    val text: TextLaneStyle,
)

/** Pixel sizes derived from the design's dp values for the current density. */
class TimelineMetrics(
    scope: Density,
) {
    /** Stroke width of the selected clip's outline. */
    val selectionStroke = with(scope) { 2.dp.toPx() }

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

    /** Width of a trim handle grip. */
    val handleWidth = with(scope) { 8.dp.toPx() }

    /** Height of a trim handle grip. */
    val handleHeight = with(scope) { 32.dp.toPx() }

    /** Width of the touch zone centered on each edge of the selected clip. */
    val handleZone = with(scope) { 48.dp.toPx() }

    /** Distance within which a dragged edge snaps to a target. */
    val snapThreshold = with(scope) { 12.dp.toPx() }

    /** Width of the snap guideline. */
    val guideWidth = with(scope) { 1.dp.toPx() }

    /** Size of the attached or free badge on overlay clips. */
    val badgeSize = with(scope) { 16.dp.toPx() }

    /** Width of one waveform bar on audio clips. */
    val waveBarWidth = with(scope) { 2.dp.toPx() }

    /** Gap between waveform bars. */
    val waveBarGap = with(scope) { 2.dp.toPx() }

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
    val sources: TimelineSources,
)

/** Returns the x position of [micros] in this frame. */
internal fun DrawScope.x(
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

/** Draws the visible clips of [track] in the row starting at [top]; the clip [selectedClipId] stands out. */
fun DrawScope.drawTrack(
    frame: TimelineFrame,
    track: Track,
    top: Float,
    selectedClipId: String?,
) {
    val metrics = frame.metrics
    val range = TimelineGeometry.visibleRange(frame.centerMicros, frame.pxPerSecond, size.width)
    val isAudio = track.kind == TrackKind.AUDIO
    val isText = track.kind == TrackKind.TEXT
    val fill = if (track.kind == TrackKind.OVERLAY) frame.style.overlay.clip else frame.style.colors.clip
    track.clips
        .filter { it.endMicros >= range.first && it.startMicros <= range.last }
        .forEach { clip ->
            val left = x(frame, clip.startMicros) + metrics.clipInset
            val width = x(frame, clip.endMicros) - metrics.clipInset - left
            if (width > 0f) {
                val block =
                    RoundRect(
                        left = left,
                        top = top,
                        right = left + width,
                        bottom = top + metrics.rowHeight,
                        cornerRadius = CornerRadius(metrics.clipCorner),
                    )
                clipPath(Path().apply { addRoundRect(block) }) {
                    val bounds = Rect(left, top, block.right, block.bottom)
                    when {
                        isAudio -> {
                            drawAudioClip(frame, clip, bounds, selected = clip.id == selectedClipId)
                        }

                        isText -> {
                            drawTextClip(frame, clip, bounds)
                        }

                        else -> {
                            drawRect(color = fill, topLeft = Offset(left, top), size = block.size())
                            drawThumbnails(frame, clip, top)
                        }
                    }
                }
                drawClipLabel(frame, TimelineGeometry.formatClipDuration(clip.durationMicros), left, width, top)
                val linkable = track.kind.isPlaced || (isAudio && clip.anchor != null)
                if (linkable) drawLinkBadge(frame, clip, left, width, top)
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
        val bitmap = frame.sources.thumbnails.get(key) ?: return@forEach
        val sourceWidth = (bitmap.width * tile.widthPx / fullWidth).roundToInt().coerceIn(1, bitmap.width)
        drawImage(
            image = bitmap.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sourceWidth, bitmap.height),
            dstOffset = IntOffset(tile.leftPx.roundToInt(), top.roundToInt()),
            dstSize = IntSize(tile.widthPx.roundToInt(), metrics.rowHeight.roundToInt()),
        )
    }
    val failedTile = tiles.firstOrNull { frame.sources.thumbnails.hasFailed(it.key) }?.tile
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

/**
 * Draws a link badge on a scrim at the bottom-left corner of [clip], spanning [clipLeft] to [clipLeft] + [clipWidth]:
 * linked when it is attached to a base clip, broken when it is free. Too narrow a clip gets none.
 */
private fun DrawScope.drawLinkBadge(
    frame: TimelineFrame,
    clip: Clip,
    clipLeft: Float,
    clipWidth: Float,
    top: Float,
) {
    val metrics = frame.metrics
    val padding = metrics.labelPadding
    val badge = metrics.badgeSize
    if (clipWidth < badge + padding * 2) return
    val left = clipLeft + padding
    val badgeTop = top + metrics.rowHeight - badge - padding
    drawCircle(
        color = Color.Black.copy(alpha = LABEL_SCRIM_ALPHA),
        radius = badge / 2f + padding / 2f,
        center = Offset(left + badge / 2f, badgeTop + badge / 2f),
    )
    val overlay = frame.style.overlay
    val attached = clip.anchor != null
    val icon = if (attached) overlay.link else overlay.linkOff
    val tint = if (attached) frame.style.colors.clipLabel else overlay.linkOffTint
    translate(left, badgeTop) {
        with(icon) { draw(Size(badge, badge), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** Outlines the clip [clipId] when it is on a track; nothing is drawn for null or unknown ids. */
fun DrawScope.drawSelection(
    frame: TimelineFrame,
    clipId: String?,
) {
    val index = frame.project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
    val track = frame.project.tracks.getOrNull(index) ?: return
    val clip = track.clips.first { it.id == clipId }
    val metrics = frame.metrics
    val half = metrics.selectionStroke / 2f
    val left = x(frame, clip.startMicros) + metrics.clipInset + half
    val right = x(frame, clip.endMicros) - metrics.clipInset - half
    drawRoundRect(
        color = frame.style.colors.selection,
        topLeft = Offset(left, metrics.rowTop(index) + half),
        size = Size((right - left).coerceAtLeast(0f), metrics.rowHeight - metrics.selectionStroke),
        cornerRadius = CornerRadius(metrics.clipCorner),
        style = Stroke(width = metrics.selectionStroke),
    )
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

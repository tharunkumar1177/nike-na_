/*
 * Pure timeline geometry: time/pixel conversion around a fixed center playhead, zoom limits, and ruler ticks.
 */
package com.framecraft.editor.timeline

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/** A clip's horizontal extent on screen and the source time at its left edge. */
data class ClipSpan(
    val leftPx: Float,
    val widthPx: Float,
    val sourceStartMicros: Long,
)

/** One thumbnail slot inside a clip: its screen extent and the source time it shows. */
data class ThumbnailTile(
    val leftPx: Float,
    val widthPx: Float,
    val sourceMicros: Long,
)

/** Converts between timeline time and horizontal pixel positions. */
object TimelineGeometry {
    /** Thumbnail source times are rounded down to this step so scrolling reuses cached frames. */
    const val THUMBNAIL_STEP_MICROS: Long = 250_000L

    /** Most zoomed-out scale, in dp per second. */
    const val MIN_DP_PER_SECOND: Float = 4f

    /** Most zoomed-in scale, in dp per second (about 8dp per frame at 30 fps). */
    const val MAX_DP_PER_SECOND: Float = 240f

    /** Scale used when a project opens, in dp per second. */
    const val DEFAULT_DP_PER_SECOND: Float = 48f

    /** Share of the viewport a fitted timeline occupies. */
    private const val FIT_FILL = 0.9f

    /** Candidate tick intervals coarser than one frame, in microseconds. */
    private val TICK_LADDER =
        longArrayOf(
            100_000L,
            500_000L,
            1_000_000L,
            2_000_000L,
            5_000_000L,
            10_000_000L,
            30_000_000L,
            60_000_000L,
            300_000_000L,
        )

    /** Returns the x position of [micros] when [centerMicros] sits under the playhead at the viewport center. */
    fun timeToX(
        micros: Long,
        centerMicros: Long,
        pxPerSecond: Float,
        viewportWidthPx: Float,
    ): Float = viewportWidthPx / 2f + (micros - centerMicros) * pxPerSecond / MICROS_PER_SECOND

    /** Returns the time under x position [x]; the inverse of [timeToX]. */
    fun xToTime(
        x: Float,
        centerMicros: Long,
        pxPerSecond: Float,
        viewportWidthPx: Float,
    ): Long = centerMicros + ((x - viewportWidthPx / 2f) / pxPerSecond * MICROS_PER_SECOND).roundToLong()

    /** Returns the time span visible in the viewport, clamped at zero. */
    fun visibleRange(
        centerMicros: Long,
        pxPerSecond: Float,
        viewportWidthPx: Float,
    ): LongRange {
        val halfSpan = (viewportWidthPx / 2f / pxPerSecond * MICROS_PER_SECOND).roundToLong()
        return (centerMicros - halfSpan).coerceAtLeast(0L)..(centerMicros + halfSpan)
    }

    /** Returns [dpPerSecond] limited to the supported zoom range. */
    fun clampZoom(dpPerSecond: Float): Float = dpPerSecond.coerceIn(MIN_DP_PER_SECOND, MAX_DP_PER_SECOND)

    /** Returns the zoom at which [durationMicros] fills most of a [viewportWidthDp] wide viewport. */
    fun fitZoom(
        durationMicros: Long,
        viewportWidthDp: Float,
    ): Float {
        if (durationMicros <= 0L) return DEFAULT_DP_PER_SECOND
        val seconds = durationMicros.toFloat() / MICROS_PER_SECOND
        return clampZoom(viewportWidthDp * FIT_FILL / seconds)
    }

    /**
     * Returns the smallest tick interval whose ticks are at least [minSpacingPx] apart,
     * starting from one frame of [frameRate].
     */
    fun tickIntervalMicros(
        pxPerSecond: Float,
        minSpacingPx: Float,
        frameRate: FrameRate,
    ): Long {
        val candidates = longArrayOf(frameRate.startOfFrame(1)) + TICK_LADDER
        return candidates.firstOrNull { it * pxPerSecond / MICROS_PER_SECOND >= minSpacingPx } ?: TICK_LADDER.last()
    }

    /**
     * Returns the [tileWidthPx]-wide thumbnail slots of [span] that intersect a viewport [viewportWidthPx] wide,
     * each showing the source time under its left edge.
     */
    fun thumbnailTiles(
        span: ClipSpan,
        tileWidthPx: Float,
        pxPerSecond: Float,
        viewportWidthPx: Float,
    ): List<ThumbnailTile> {
        val visibleFrom = (-span.leftPx).coerceAtLeast(0f)
        val visibleTo = (viewportWidthPx - span.leftPx).coerceAtMost(span.widthPx)
        if (tileWidthPx <= 0f || visibleTo <= visibleFrom) return emptyList()
        val first = (visibleFrom / tileWidthPx).toInt()
        val last = ((visibleTo - 1f) / tileWidthPx).toInt().coerceAtLeast(first)
        return (first..last).map { index ->
            val localLeft = index * tileWidthPx
            val offsetMicros = (localLeft / pxPerSecond * MICROS_PER_SECOND).toLong()
            val source = span.sourceStartMicros + offsetMicros
            ThumbnailTile(
                leftPx = span.leftPx + localLeft,
                widthPx = minOf(tileWidthPx, span.widthPx - localLeft),
                sourceMicros = source / THUMBNAIL_STEP_MICROS * THUMBNAIL_STEP_MICROS,
            )
        }
    }

    /**
     * Returns the index of the track row containing [y], or null when [y] is on the ruler, a gap, or below
     * the rows. Rows are [rowPx] tall, separated by [gapPx], and start one gap below a [rulerPx] tall ruler.
     */
    fun rowAt(
        y: Float,
        rulerPx: Float,
        rowPx: Float,
        gapPx: Float,
    ): Int? {
        val offset = y - rulerPx - gapPx
        val pitch = rowPx + gapPx
        val index = floor(offset / pitch).toInt()
        return index.takeIf { offset >= 0f && offset - index * pitch < rowPx }
    }

    /** Returns the clip of [track] showing at [micros], or null in a gap. */
    fun clipAt(
        track: Track,
        micros: Long,
    ): Clip? = track.clips.firstOrNull { micros >= it.startMicros && micros < it.endMicros }

    /** Formats a clip duration with one decimal, for example "2.5s". */
    fun formatClipDuration(durationMicros: Long): String =
        String.format(Locale.ROOT, "%.1fs", durationMicros.toDouble() / MICROS_PER_SECOND)
}

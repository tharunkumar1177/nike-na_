/*
 * Unit tests for timeline geometry: conversions, visible range, zoom limits, and tick selection.
 */
package com.framecraft.editor.timeline

import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the pure timeline math used by the timeline strip. */
class TimelineGeometryTest {
    /** The center time maps to the viewport center and conversions round-trip. */
    @Test
    fun timeAndXRoundTrip() {
        val x = TimelineGeometry.timeToX(3_000_000L, 2_000_000L, 100f, 1_000f)
        assertEquals(600f, x, 0.001f)
        assertEquals(3_000_000L, TimelineGeometry.xToTime(x, 2_000_000L, 100f, 1_000f))
        assertEquals(500f, TimelineGeometry.timeToX(2_000_000L, 2_000_000L, 100f, 1_000f), 0.001f)
    }

    /** The visible range is centered on the playhead and never negative. */
    @Test
    fun visibleRangeClampsAtZero() {
        assertEquals(0L..6_000_000L, TimelineGeometry.visibleRange(1_000_000L, 100f, 1_000f))
        assertEquals(5_000_000L..15_000_000L, TimelineGeometry.visibleRange(10_000_000L, 100f, 1_000f))
    }

    /** Zoom stays within limits and fitting uses most of the viewport. */
    @Test
    fun zoomLimitsAndFit() {
        assertEquals(TimelineGeometry.MIN_DP_PER_SECOND, TimelineGeometry.clampZoom(0.1f))
        assertEquals(TimelineGeometry.MAX_DP_PER_SECOND, TimelineGeometry.clampZoom(10_000f))
        assertEquals(36f, TimelineGeometry.fitZoom(10_000_000L, 400f), 0.001f)
        assertEquals(TimelineGeometry.DEFAULT_DP_PER_SECOND, TimelineGeometry.fitZoom(0L, 400f))
    }

    /** Ticks start at one frame when zoomed in and grow coarser when zoomed out. */
    @Test
    fun tickIntervalAdapts() {
        val rate = FrameRate.FPS_30
        assertEquals(rate.startOfFrame(1), TimelineGeometry.tickIntervalMicros(2_400f, 64f, rate))
        assertEquals(1_000_000L, TimelineGeometry.tickIntervalMicros(64f, 64f, rate))
        assertEquals(300_000_000L, TimelineGeometry.tickIntervalMicros(0.01f, 64f, rate))
    }

    /** Only visible tiles are produced, the last is cut to the clip, and source times are stepped. */
    @Test
    fun thumbnailTilesCoverVisiblePart() {
        val tiles = TimelineGeometry.thumbnailTiles(ClipSpan(-150f, 350f, 1_000_000L), 100f, 100f, 1_000f)
        assertEquals(listOf(-50f, 50f, 150f), tiles.map { it.leftPx })
        assertEquals(listOf(100f, 100f, 50f), tiles.map { it.widthPx })
        assertEquals(listOf(2_000_000L, 3_000_000L, 4_000_000L), tiles.map { it.sourceMicros })
        assertEquals(0, TimelineGeometry.thumbnailTiles(ClipSpan(1_200f, 100f, 0L), 100f, 100f, 1_000f).size)
    }

    /** Clip durations show one decimal place. */
    @Test
    fun formatsClipDuration() {
        assertEquals("2.5s", TimelineGeometry.formatClipDuration(2_500_000L))
    }
}

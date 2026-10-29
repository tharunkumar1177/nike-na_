/*
 * Unit tests for timeline geometry: conversions, visible range, zoom limits, ticks, and trim handles.
 */
package com.framecraft.editor.timeline

import com.framecraft.editor.domain.edit.TrimEdge
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.model.SpeedPreset
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** A clip at double speed shows the source twice as far in under each tile. */
    @Test
    fun thumbnailTilesFollowSpeed() {
        val map = SpeedMap.of(ClipSpeed.Constant(2_000), 6_000_000L, FrameRate.FPS_30)
        val tiles = TimelineGeometry.thumbnailTiles(ClipSpan(0f, 300f, 1_000_000L, map), 100f, 100f, 1_000f)
        assertEquals(listOf(1_000_000L, 3_000_000L, 5_000_000L), tiles.map { it.sourceMicros })
    }

    /** Speeds read as short text, with a range for curves and nothing at normal speed. */
    @Test
    fun speedLabels() {
        assertEquals("2.0x", SpeedLabel.of(2_000))
        assertEquals("0.25x", SpeedLabel.of(250))
        assertEquals("1.5x", SpeedLabel.of(1_500))
        assertEquals("10x", SpeedLabel.of(10_000))
        assertNull(SpeedLabel.badgeOf(null))
        assertEquals("0.4–4.0x", SpeedLabel.badgeOf(SpeedPreset.HERO.curve))
    }

    /** Rows are found below the ruler, and gaps between rows hit nothing. */
    @Test
    fun rowAtSkipsRulerAndGaps() {
        assertNull(TimelineGeometry.rowAt(10f, 32f, 64f, 8f))
        assertEquals(0, TimelineGeometry.rowAt(40f, 32f, 64f, 8f))
        assertEquals(0, TimelineGeometry.rowAt(103.9f, 32f, 64f, 8f))
        assertNull(TimelineGeometry.rowAt(108f, 32f, 64f, 8f))
        assertEquals(1, TimelineGeometry.rowAt(112f, 32f, 64f, 8f))
    }

    /** Clip extents are half-open, so the boundary belongs to the next clip. */
    @Test
    fun clipAtUsesHalfOpenExtents() {
        val track =
            Track(
                id = "track",
                kind = TrackKind.VIDEO,
                magnetic = false,
                clips =
                    listOf(
                        Clip("a", "asset", 0L, 0L, 1_000_000L),
                        Clip("b", "asset", 2_000_000L, 0L, 1_000_000L),
                    ),
            )
        assertEquals("a", TimelineGeometry.clipAt(track, 0L)?.id)
        assertNull(TimelineGeometry.clipAt(track, 1_000_000L))
        assertEquals("b", TimelineGeometry.clipAt(track, 2_000_000L)?.id)
    }

    /** Clip durations show one decimal place. */
    @Test
    fun formatsClipDuration() {
        assertEquals("2.5s", TimelineGeometry.formatClipDuration(2_500_000L))
    }

    /** Trim handle zones are centered on the clip edges, and the closer edge wins on short clips. */
    @Test
    fun trimEdgeAtFindsClosestHandle() {
        assertEquals(TrimEdge.START, TimelineGeometry.trimEdgeAt(90f, 100f, 400f, 48f))
        assertEquals(TrimEdge.END, TimelineGeometry.trimEdgeAt(420f, 100f, 400f, 48f))
        assertNull(TimelineGeometry.trimEdgeAt(250f, 100f, 400f, 48f))
        assertNull(TimelineGeometry.trimEdgeAt(70f, 100f, 400f, 48f))
        assertEquals(TrimEdge.START, TimelineGeometry.trimEdgeAt(105f, 100f, 120f, 48f))
        assertEquals(TrimEdge.END, TimelineGeometry.trimEdgeAt(115f, 100f, 120f, 48f))
    }
}

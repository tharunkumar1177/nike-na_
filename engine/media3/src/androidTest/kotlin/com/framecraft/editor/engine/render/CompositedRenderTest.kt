/*
 * Device tests that export composited projects through the production path and check where each layer lands in the
 * encoded frames.
 */
package com.framecraft.editor.engine.render

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Length of every test project. */
private const val PROJECT_MICROS = 2_000_000L

/** Times at which every export is sampled. */
private val SAMPLE_TIMES_MILLIS = listOf(500L, 1_000L, 1_500L)

/** Size of the base still, which sets the original-shape canvas and the 720p export. */
private const val BASE_WIDTH = 1280
private const val BASE_HEIGHT = 720

/** Size of the overlay still: half the base on each side, so at scale 0.5 it fills one canvas quadrant. */
private const val OVERLAY_WIDTH = 640
private const val OVERLAY_HEIGHT = 360

/** Base color: a mid blue clear of the limits that video color conversion clips. */
private const val BASE_ARGB = 0xFF3050B0.toInt()

/** Overlay color: a warm ochre far from the base in every channel. */
private const val OVERLAY_ARGB = 0xFFD0A040.toInt()

/** Pixels kept clear of each quadrant's edges, where chroma subsampling blurs neighboring colors. */
private const val EDGE_INSET = 48

/** Opacity of the half-transparent overlay. */
private const val HALF_OPACITY = 0.5f

/** Overlay center, in half-canvas units: the middle of the top-right quadrant. */
private const val QUADRANT_CENTER = 0.5f

/** Overlay scale relative to its fitted size, so it covers a quarter of the canvas. */
private const val QUADRANT_SCALE = 0.5f

/** Verifies composited rendering of overlays in exported video. */
@RunWith(AndroidJUnit4::class)
class CompositedRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val harness = RenderHarness(context)

    /** An opaque overlay placed in the top-right quadrant covers exactly that quadrant, over the base. */
    @Test
    fun overlayPlacementDrawsOverlayAtItsRect() {
        val video = harness.export(overlayProject("overlay-placement", opacity = 1f))
        harness.framesAt(video, SAMPLE_TIMES_MILLIS).forEachIndexed { index, frame ->
            val time = SAMPLE_TIMES_MILLIS[index]
            assertEquals("frame width at $time ms", video.size.width, frame.width)
            assertEquals("frame height at $time ms", video.size.height, frame.height)
            RegionAssert.assertMeanColor(frame, quadrant(1, 0), OVERLAY_ARGB, "overlay quadrant at $time ms")
            RegionAssert.assertMeanColor(frame, quadrant(0, 0), BASE_ARGB, "base left of overlay at $time ms")
            RegionAssert.assertMeanColor(frame, quadrant(1, 1), BASE_ARGB, "base below overlay at $time ms")
            RegionAssert.assertMeanColor(frame, quadrant(0, 1), BASE_ARGB, "base opposite overlay at $time ms")
        }
    }

    /** A half-transparent overlay blends evenly with the base under it and leaves the rest of the base alone. */
    @Test
    fun overlayOpacityBlendsOverlayWithBase() {
        val video = harness.export(overlayProject("overlay-opacity", opacity = HALF_OPACITY))
        val blended = RegionAssert.halfBlend(OVERLAY_ARGB, BASE_ARGB)
        harness.framesAt(video, SAMPLE_TIMES_MILLIS).forEachIndexed { index, frame ->
            val time = SAMPLE_TIMES_MILLIS[index]
            RegionAssert.assertMeanColor(frame, quadrant(1, 0), blended, "half-opaque overlay at $time ms")
            RegionAssert.assertMeanColor(frame, quadrant(0, 1), BASE_ARGB, "base opposite overlay at $time ms")
        }
    }

    /**
     * Returns project [id]: a base still filling the canvas and an overlay still at [opacity] in the top-right
     * quadrant, both for the whole project.
     */
    private fun overlayProject(
        id: String,
        opacity: Float,
    ): Project {
        val base = harness.solidImage("$id-base", BASE_WIDTH, BASE_HEIGHT, BASE_ARGB)
        val overlay = harness.solidImage("$id-overlay", OVERLAY_WIDTH, OVERLAY_HEIGHT, OVERLAY_ARGB)
        val placement =
            OverlayTransform(
                centerX = QUADRANT_CENTER,
                centerY = -QUADRANT_CENTER,
                scale = QUADRANT_SCALE,
                opacity = opacity,
            )
        return Project(
            id = id,
            name = id,
            frameRate = FrameRate.FPS_30,
            assets = listOf(base, overlay),
            tracks =
                listOf(
                    Track(
                        id = "$id-base-track",
                        kind = TrackKind.VIDEO,
                        magnetic = true,
                        clips = listOf(Clip("$id-base-clip", base.id, 0, 0, PROJECT_MICROS)),
                    ),
                    Track(
                        id = "$id-overlay-track",
                        kind = TrackKind.OVERLAY,
                        magnetic = false,
                        clips =
                            listOf(
                                Clip(
                                    id = "$id-overlay-clip",
                                    assetId = overlay.id,
                                    startMicros = 0,
                                    trimStartMicros = 0,
                                    durationMicros = PROJECT_MICROS,
                                    overlayTransform = placement,
                                ),
                            ),
                    ),
                ),
        )
    }

    /** Returns the inset interior of the canvas quadrant at [column] and [row], each 0 or 1, of a 720p frame. */
    private fun quadrant(
        column: Int,
        row: Int,
    ): Rect {
        val width = BASE_WIDTH / 2
        val height = BASE_HEIGHT / 2
        return Rect(
            column * width + EDGE_INSET,
            row * height + EDGE_INSET,
            (column + 1) * width - EDGE_INSET,
            (row + 1) * height - EDGE_INSET,
        )
    }
}

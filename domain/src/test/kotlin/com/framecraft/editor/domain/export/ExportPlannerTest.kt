/*
 * Unit tests for export option math: offered resolutions and frame rates, output size, bitrate, and estimates.
 */
package com.framecraft.editor.domain.export

import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies [ExportPlanner] against real projects. */
class ExportPlannerTest {
    private var counter = 0

    /** Returns a ten-second video asset of [width] by [height] pixels with [rotation]. */
    private fun video(
        width: Int,
        height: Int,
        rotation: Int = 0,
    ) = MediaAsset(
        id = "video-${counter++}",
        uri = "content://media/video",
        kind = MediaKind.VIDEO,
        durationMicros = 10 * SECOND,
        width = width,
        height = height,
        rotationDegrees = rotation,
        hasAudio = true,
        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
    )

    /** Returns a project playing [assets] back to back at [frameRate]. */
    private fun project(
        vararg assets: MediaAsset,
        frameRate: FrameRate = FrameRate.FPS_30,
    ): Project = ProjectFactory.create("Export", assets.toList(), { "id-${counter++}" }, frameRate)

    /** A 4K source offers every preset and defaults to the largest at the project rate. */
    @Test
    fun uhdSourceOffersAllResolutions() {
        val project = project(video(3840, 2160))
        val options = ExportPlanner.optionsFor(project)
        assertEquals(ExportResolution.entries, options.resolutions)
        val defaults = ExportPlanner.defaultsFor(project)
        assertEquals(ExportSettings(ExportResolution.UHD_4K, FrameRate.FPS_30, ExportQuality.STANDARD), defaults)
        assertEquals(ExportSize(3840, 2160), ExportPlanner.outputSize(project, defaults.resolution))
    }

    /** Resolutions stop at the largest source; a source below 720p still offers 720p. */
    @Test
    fun resolutionsCappedAtLargestSource() {
        val hdOnly = listOf(ExportResolution.HD_720)
        assertEquals(hdOnly, ExportPlanner.optionsFor(project(video(1280, 720))).resolutions)
        assertEquals(hdOnly, ExportPlanner.optionsFor(project(video(854, 480))).resolutions)
        val mixed = project(video(1280, 720), video(1920, 1080))
        assertEquals(
            listOf(ExportResolution.HD_720, ExportResolution.FULL_HD_1080),
            ExportPlanner.optionsFor(mixed).resolutions,
        )
    }

    /** A quarter-turn source exports portrait, with the preset on the short side. */
    @Test
    fun rotatedSourceExportsPortrait() {
        val project = project(video(1920, 1080, rotation = 90))
        assertEquals(ExportSize(1080, 1920), ExportPlanner.outputSize(project, ExportResolution.FULL_HD_1080))
        assertEquals(ExportSize(720, 1280), ExportPlanner.outputSize(project, ExportResolution.HD_720))
    }

    /** Other aspects scale the long side and keep both sides even. */
    @Test
    fun outputSizeKeepsAspectAndEvenSides() {
        val classic = project(video(1440, 1080))
        assertEquals(ExportSize(960, 720), ExportPlanner.outputSize(classic, ExportResolution.HD_720))
        val odd = ExportPlanner.outputSize(project(video(1000, 562)), ExportResolution.HD_720)
        assertEquals(720, odd.height)
        assertTrue(odd.width % 2 == 0)
        assertTrue(odd.width in 1280..1284)
    }

    /** Frame rates are the project rate plus standard rates strictly below it, fastest first. */
    @Test
    fun frameRatesNeverExceedProjectRate() {
        val source = video(1920, 1080)
        assertEquals(
            listOf(FrameRate.FPS_29_97, FrameRate.FPS_25, FrameRate.FPS_24),
            ExportPlanner.optionsFor(project(source, frameRate = FrameRate.FPS_29_97)).frameRates,
        )
        assertEquals(
            listOf(FrameRate.FPS_60, FrameRate.FPS_30, FrameRate.FPS_25, FrameRate.FPS_24),
            ExportPlanner.optionsFor(project(source, frameRate = FrameRate.FPS_60)).frameRates,
        )
        val cinema = project(source, frameRate = FrameRate.FPS_24)
        assertEquals(listOf(FrameRate.FPS_24), ExportPlanner.optionsFor(cinema).frameRates)
    }

    /** Bitrate grows with quality and stays within the clamp range. */
    @Test
    fun bitrateFollowsQualityAndClamps() {
        val fullHd = ExportSize(1920, 1080)
        assertEquals(6_220_800, ExportPlanner.targetBitrateBps(fullHd, FrameRate.FPS_30, ExportQuality.STANDARD))
        assertEquals(11_197_440, ExportPlanner.targetBitrateBps(fullHd, FrameRate.FPS_30, ExportQuality.HIGH))
        assertEquals(
            MIN_VIDEO_BITRATE_BPS,
            ExportPlanner.targetBitrateBps(ExportSize(160, 90), FrameRate.FPS_24, ExportQuality.STANDARD),
        )
        assertEquals(
            MAX_VIDEO_BITRATE_BPS,
            ExportPlanner.targetBitrateBps(ExportSize(8000, 8000), FrameRate.FPS_60, ExportQuality.HIGH),
        )
    }

    /** The estimate adds audio to video bits over the duration and grows with quality. */
    @Test
    fun estimateCountsVideoAndAudio() {
        val project = project(video(1920, 1080))
        val standard = ExportSettings(ExportResolution.FULL_HD_1080, FrameRate.FPS_30, ExportQuality.STANDARD)
        assertEquals(7_936_000L, ExportPlanner.estimateBytes(project, standard, 10 * SECOND))
        val high = standard.copy(quality = ExportQuality.HIGH)
        assertTrue(ExportPlanner.estimateBytes(project, high, 10 * SECOND) > 7_936_000L)
    }

    /** Saved choices the project no longer offers fall back to defaults; missing settings use the defaults. */
    @Test
    fun savedSettingsAreReconciled() {
        val project = project(video(1920, 1080))
        val saved = ExportSettings(ExportResolution.UHD_4K, FrameRate.FPS_60, ExportQuality.HIGH)
        assertEquals(
            ExportSettings(ExportResolution.FULL_HD_1080, FrameRate.FPS_30, ExportQuality.HIGH),
            ExportPlanner.settingsFor(project, saved),
        )
        assertEquals(ExportPlanner.defaultsFor(project), ExportPlanner.settingsFor(project, null))
        val valid = ExportSettings(ExportResolution.HD_720, FrameRate.FPS_24, ExportQuality.STANDARD)
        assertEquals(valid, ExportPlanner.settingsFor(project, valid))
    }
}

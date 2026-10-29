/*
 * Export option math: which resolutions and frame rates a project can offer, output size, bitrate, and file size.
 */
package com.framecraft.editor.domain.export

import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import kotlin.math.roundToInt

/** Canvas used when a project has no clip yet: landscape 1080p. */
private val DEFAULT_CANVAS = ExportSize(width = 1920, height = 1080)

/** Standard rates offered below the project rate, fastest first. */
private val STANDARD_RATES = listOf(FrameRate.FPS_60, FrameRate.FPS_30, FrameRate.FPS_25, FrameRate.FPS_24)

/** Audio bitrate of every export (AAC stereo), in bits per second. */
const val EXPORT_AUDIO_BITRATE_BPS: Int = 128_000

/** Lowest video bitrate chosen for any export, in bits per second. */
const val MIN_VIDEO_BITRATE_BPS: Int = 1_000_000

/** Highest video bitrate chosen for any export, in bits per second. */
const val MAX_VIDEO_BITRATE_BPS: Int = 100_000_000

/** Bits per pixel per frame for [ExportQuality.STANDARD], as thousandths. */
private const val STANDARD_BPP_MILLIS = 100L

/** Bits per pixel per frame for [ExportQuality.HIGH], as thousandths. */
private const val HIGH_BPP_MILLIS = 180L

/** Thousandths in one unit. */
private const val MILLIS_PER_UNIT = 1_000L

/** Bits in one byte. */
private const val BITS_PER_BYTE = 8L

/** A half turn in degrees; rotations that are multiples of it keep width and height. */
private const val HALF_TURN_DEGREES = 180

/** Returns the tracks of [project] that show source pictures; text is drawn at the canvas size, so it never sets it. */
private fun pictureTracks(project: Project): List<Track> =
    project.tracks.filter { it.kind == TrackKind.VIDEO || it.kind == TrackKind.OVERLAY }

/**
 * Derives export choices from a project. The canvas follows the first clip's displayed size, as the preview
 * does. Resolutions never exceed the largest source, and frame rates never exceed the project rate, because
 * frames can be dropped but not invented.
 */
object ExportPlanner {
    /** Returns the resolutions and frame rates [project] can be exported at. */
    fun optionsFor(project: Project): ExportOptions {
        val largest = largestSourceShortSide(project)
        val resolutions = ExportResolution.entries.filter { it.shortSide <= largest }
        val rate = project.frameRate
        val slower = STANDARD_RATES.filter { isSlower(it, rate) }
        return ExportOptions(
            resolutions = resolutions.ifEmpty { listOf(ExportResolution.HD_720) },
            frameRates = listOf(rate) + slower,
        )
    }

    /** Returns the default settings: the largest offered resolution, the project rate, and standard quality. */
    fun defaultsFor(project: Project): ExportSettings {
        val options = optionsFor(project)
        return ExportSettings(options.resolutions.last(), options.frameRates.first(), ExportQuality.STANDARD)
    }

    /** Returns [saved] with any choice the project no longer offers replaced by its default, or the defaults. */
    fun settingsFor(
        project: Project,
        saved: ExportSettings?,
    ): ExportSettings {
        val defaults = defaultsFor(project)
        val options = optionsFor(project)
        return saved?.let {
            ExportSettings(
                resolution = it.resolution.takeIf { r -> r in options.resolutions } ?: defaults.resolution,
                frameRate = it.frameRate.takeIf { f -> f in options.frameRates } ?: defaults.frameRate,
                quality = it.quality,
            )
        } ?: defaults
    }

    /** Returns the output size: the canvas aspect scaled so its short side matches [resolution]. */
    fun outputSize(
        project: Project,
        resolution: ExportResolution,
    ): ExportSize {
        val canvas = canvasOf(project)
        val short = resolution.shortSide
        val aspect = maxOf(canvas.width, canvas.height).toDouble() / minOf(canvas.width, canvas.height)
        val long = even(short * aspect)
        return if (canvas.width >= canvas.height) ExportSize(long, short) else ExportSize(short, long)
    }

    /** Returns the video bitrate for [size] at [frameRate] and [quality], clamped to a sensible range. */
    fun targetBitrateBps(
        size: ExportSize,
        frameRate: FrameRate,
        quality: ExportQuality,
    ): Int {
        val bppMillis = if (quality == ExportQuality.HIGH) HIGH_BPP_MILLIS else STANDARD_BPP_MILLIS
        val pixelsPerSecond = size.width.toLong() * size.height * frameRate.numerator / frameRate.denominator
        val bits = pixelsPerSecond * bppMillis / MILLIS_PER_UNIT
        return bits.coerceIn(MIN_VIDEO_BITRATE_BPS.toLong(), MAX_VIDEO_BITRATE_BPS.toLong()).toInt()
    }

    /** Returns the expected file size in bytes of a [durationMicros] export of [project] with [settings]. */
    fun estimateBytes(
        project: Project,
        settings: ExportSettings,
        durationMicros: Long,
    ): Long {
        val size = outputSize(project, settings.resolution)
        val videoBps = targetBitrateBps(size, settings.frameRate, settings.quality).toLong()
        val bitsPerSecond = videoBps + EXPORT_AUDIO_BITRATE_BPS
        return bitsPerSecond * durationMicros / MICROS_PER_SECOND / BITS_PER_BYTE
    }

    /**
     * Returns the displayed size of the first base clip's source (overlays only when there is no base clip), or the
     * default canvas when there is no picture clip. Sound and text tracks never set the canvas.
     */
    private fun canvasOf(project: Project): ExportSize {
        val baseFirst = pictureTracks(project).sortedBy { it.kind != TrackKind.VIDEO }
        val first = baseFirst.firstNotNullOfOrNull { it.clips.firstOrNull() }
        return first?.let { displayedSize(project.assetOf(it)) } ?: DEFAULT_CANVAS
    }

    /** Returns the longest short side among pictures used by clips, or the default canvas's when there is none. */
    private fun largestSourceShortSide(project: Project): Int {
        val used = pictureTracks(project).flatMap { track -> track.clips.map { project.assetOf(it) } }
        return used.maxOfOrNull { asset -> displayedSize(asset).let { minOf(it.width, it.height) } }
            ?: minOf(DEFAULT_CANVAS.width, DEFAULT_CANVAS.height)
    }

    /** Returns [asset]'s size as shown, with width and height swapped for quarter-turn rotations. */
    private fun displayedSize(asset: MediaAsset): ExportSize =
        if (asset.rotationDegrees % HALF_TURN_DEGREES == 0) {
            ExportSize(asset.width, asset.height)
        } else {
            ExportSize(asset.height, asset.width)
        }

    /** Returns true if [candidate] is strictly slower than [reference], compared exactly. */
    private fun isSlower(
        candidate: FrameRate,
        reference: FrameRate,
    ): Boolean {
        val left = candidate.numerator.toLong() * reference.denominator
        val right = reference.numerator.toLong() * candidate.denominator
        return left < right
    }

    /** Rounds [value] to the nearest even integer. */
    private fun even(value: Double): Int = (value / 2).roundToInt() * 2
}

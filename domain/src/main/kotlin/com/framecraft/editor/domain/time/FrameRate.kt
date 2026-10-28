/*
 * Rational frame rate and the frame/microsecond conversions shared by the timeline UI and the media engine.
 */
package com.framecraft.editor.domain.time

import kotlinx.serialization.Serializable

/** Number of microseconds in one second. */
const val MICROS_PER_SECOND: Long = 1_000_000L

/**
 * A frame rate expressed as an exact ratio of [numerator] frames per [denominator] seconds,
 * so rates such as 30000/1001 (29.97 fps) convert without floating-point drift.
 */
@Serializable
data class FrameRate(
    val numerator: Int,
    val denominator: Int,
) {
    init {
        require(numerator > 0) { "Frame rate numerator must be positive, was $numerator" }
        require(denominator > 0) { "Frame rate denominator must be positive, was $denominator" }
    }

    /** Returns the zero-based index of the frame that is showing at [micros]. */
    fun frameAt(micros: Long): Long {
        require(micros >= 0) { "Time must be non-negative, was $micros" }
        return micros * numerator / (denominator * MICROS_PER_SECOND)
    }

    /** Returns the earliest microsecond at which frame [frame] is showing. */
    fun startOfFrame(frame: Long): Long {
        require(frame >= 0) { "Frame index must be non-negative, was $frame" }
        val scaled = frame * denominator * MICROS_PER_SECOND
        return ceilDiv(scaled, numerator.toLong())
    }

    /** Returns the start of the frame boundary closest to [micros]; ties round to the later boundary. */
    fun snapToNearestFrame(micros: Long): Long {
        val frame = frameAt(micros)
        val start = startOfFrame(frame)
        val next = startOfFrame(frame + 1)
        return if (micros - start < next - micros) start else next
    }

    /** Returns [micros] moved by [frames] whole frames from its frame boundary, clamped at zero. */
    fun stepFrames(
        micros: Long,
        frames: Long,
    ): Long {
        val target = frameAt(micros) + frames
        return if (target <= 0) 0L else startOfFrame(target)
    }

    /** Frame rates offered when creating a project. */
    companion object {
        /** Cinema rate, 24 fps. */
        val FPS_24 = FrameRate(24, 1)

        /** PAL rate, 25 fps. */
        val FPS_25 = FrameRate(25, 1)

        /** NTSC rate, 29.97 fps. */
        val FPS_29_97 = FrameRate(30_000, 1_001)

        /** Standard mobile rate, 30 fps. */
        val FPS_30 = FrameRate(30, 1)

        /** High frame rate, 60 fps. */
        val FPS_60 = FrameRate(60, 1)
    }
}

/** Divides non-negative [dividend] by positive [divisor], rounding up. */
private fun ceilDiv(
    dividend: Long,
    divisor: Long,
): Long = (dividend + divisor - 1) / divisor

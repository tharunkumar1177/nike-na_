/*
 * Unit tests for FrameRate frame/microsecond conversions, including drop-frame NTSC rates.
 */
package com.framecraft.editor.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Verifies exact rational conversions between microseconds and frames. */
class FrameRateTest {
    /** At 30 fps, one second holds exactly 30 frames. */
    @Test
    fun frameAtWholeRate() {
        assertEquals(30L, FrameRate.FPS_30.frameAt(MICROS_PER_SECOND))
        assertEquals(29L, FrameRate.FPS_30.frameAt(MICROS_PER_SECOND - 1))
    }

    /** Every frame start maps back to the same frame index for an NTSC rate. */
    @Test
    fun startOfFrameRoundTripsAtNtsc() {
        val rate = FrameRate.FPS_29_97
        for (frame in 0L..10_000L) {
            assertEquals(frame, rate.frameAt(rate.startOfFrame(frame)))
        }
    }

    /** Snapping chooses the closer of the two surrounding frame boundaries. */
    @Test
    fun snapToNearestFrame() {
        val rate = FrameRate.FPS_25
        assertEquals(0L, rate.snapToNearestFrame(19_999))
        assertEquals(40_000L, rate.snapToNearestFrame(20_000))
        assertEquals(40_000L, rate.snapToNearestFrame(40_001))
    }

    /** Frame stepping moves by whole frames and never goes below zero. */
    @Test
    fun stepFramesClampsAtZero() {
        val rate = FrameRate.FPS_30
        assertEquals(rate.startOfFrame(11), rate.stepFrames(rate.startOfFrame(10), 1))
        assertEquals(rate.startOfFrame(9), rate.stepFrames(rate.startOfFrame(10), -1))
        assertEquals(0L, rate.stepFrames(rate.startOfFrame(2), -5))
    }

    /** Non-positive rate components are rejected. */
    @Test
    fun rejectsInvalidRates() {
        assertThrows<IllegalArgumentException> { FrameRate(0, 1) }
        assertThrows<IllegalArgumentException> { FrameRate(30, 0) }
    }

    /** Negative times are rejected. */
    @Test
    fun rejectsNegativeTime() {
        assertThrows<IllegalArgumentException> { FrameRate.FPS_30.frameAt(-1) }
    }
}

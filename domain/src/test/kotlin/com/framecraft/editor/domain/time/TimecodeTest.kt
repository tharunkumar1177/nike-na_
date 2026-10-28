/*
 * Unit tests for timecode formatting.
 */
package com.framecraft.editor.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Verifies M:SS and H:MM:SS formatting. */
class TimecodeTest {
    /** Short times use minutes and zero-padded seconds. */
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:05", Timecode.format(5_400_000L))
        assertEquals("1:23", Timecode.format(83 * MICROS_PER_SECOND))
    }

    /** Times of an hour or more include hours. */
    @Test
    fun formatsHours() {
        assertEquals("1:02:05", Timecode.format(3_725 * MICROS_PER_SECOND))
    }

    /** Progress uses hours for both values when the duration reaches an hour. */
    @Test
    fun progressMatchesDurationFormat() {
        assertEquals("0:05 / 1:23", Timecode.formatProgress(5 * MICROS_PER_SECOND, 83 * MICROS_PER_SECOND))
        assertEquals("0:00:05 / 1:23:45", Timecode.formatProgress(5 * MICROS_PER_SECOND, 5_025 * MICROS_PER_SECOND))
    }
}

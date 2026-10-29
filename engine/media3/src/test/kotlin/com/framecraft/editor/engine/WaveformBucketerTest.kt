/*
 * Unit tests for turning decoded samples into a normalized RMS loudness envelope.
 */
package com.framecraft.editor.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies [WaveformBucketer] and [Waveform]. */
class WaveformBucketerTest {
    /** Each 50 ms bucket holds the RMS of its samples, scaled so the loudest bucket is 1. */
    @Test
    fun bucketsRmsAndNormalizesToPeak() {
        val bucketer = WaveformBucketer(sampleRate = 1_000, channelCount = 2)
        repeat(100) { bucketer.add(0.5f) }
        repeat(100) { bucketer.add(if (it % 2 == 0) 0.25f else -0.25f) }
        val waveform = bucketer.finish()
        assertEquals(WAVEFORM_BUCKET_MICROS, waveform.bucketMicros)
        assertArrayEquals(floatArrayOf(1f, 0.5f), waveform.levels, 1e-6f)
    }

    /** A trailing partial bucket is kept, and silence stays at zero instead of dividing by zero. */
    @Test
    fun keepsPartialBucketAndSilence() {
        val bucketer = WaveformBucketer(sampleRate = 1_000, channelCount = 1)
        repeat(60) { bucketer.add(0f) }
        assertArrayEquals(floatArrayOf(0f, 0f), bucketer.finish().levels, 0f)
    }

    /** Levels are looked up by source time and read as zero outside the source. */
    @Test
    fun levelAtMapsSourceTime() {
        val waveform = Waveform(WAVEFORM_BUCKET_MICROS, floatArrayOf(0.2f, 1f))
        assertEquals(0.2f, waveform.levelAt(10_000L), 0f)
        assertEquals(1f, waveform.levelAt(60_000L), 0f)
        assertEquals(0f, waveform.levelAt(500_000L), 0f)
    }
}

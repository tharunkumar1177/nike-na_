/*
 * Unit tests for the sound gain math: volume and fade envelopes and clamped 16-bit scaling.
 */
package com.framecraft.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies [GainEnvelope] and [clampedSample]. */
class AudioGainTest {
    /** Unity volume without fades leaves samples alone. */
    @Test
    fun unityEnvelopeIsIdentity() {
        val envelope = GainEnvelope(100, 0L, 0L, 4 * SECOND)
        assertTrue(envelope.isUnity)
        assertEquals(1f, envelope.gainAt(2 * SECOND), 0f)
        assertEquals(12_345.toShort(), clampedSample(12_345, 1f))
    }

    /** Fades ramp linearly from silence at the clip start and back to silence at its end, scaled by volume. */
    @Test
    fun fadesRampLinearlyUnderVolume() {
        val envelope = GainEnvelope(50, SECOND, 2 * SECOND, 6 * SECOND)
        assertFalse(envelope.isUnity)
        assertEquals(0f, envelope.gainAt(0L), 1e-6f)
        assertEquals(0.25f, envelope.gainAt(SECOND / 2), 1e-6f)
        assertEquals(0.5f, envelope.gainAt(3 * SECOND), 1e-6f)
        assertEquals(0.25f, envelope.gainAt(5 * SECOND), 1e-6f)
        assertEquals(0f, envelope.gainAt(6 * SECOND), 1e-6f)
    }

    /** Doubling loud samples clips at the 16-bit limits instead of wrapping to the opposite sign. */
    @Test
    fun loudSamplesClampInsteadOfWrapping() {
        assertEquals(Short.MAX_VALUE, clampedSample(30_000, 2f))
        assertEquals(Short.MIN_VALUE, clampedSample(-30_000, 2f))
        assertEquals(20_000.toShort(), clampedSample(10_000, 2f))
        assertEquals(0.toShort(), clampedSample(-30_000, 0f))
    }
}

/*
 * Unit tests that Media3 plays a sped clip for exactly the duration the domain gives it.
 */
package com.framecraft.editor.engine

import androidx.media3.common.C
import androidx.media3.common.util.SpeedProviderUtil
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.model.SpeedPreset
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compares [SpeedProviders] against [SpeedMap] across speeds, spans, and frame rates. */
class SpeedProvidersTest {
    private val frameRates = listOf(FrameRate(24, 1), FrameRate.FPS_30, FrameRate(60, 1))

    private val speeds: List<ClipSpeed> =
        listOf(100, 500, 1_000, 1_500, 3_333, 100_000).map { ClipSpeed.Constant(it) } +
            SpeedPreset.entries.map { it.curve }

    private val spans = listOf(33_333L, 1_000_001L, 10_000_000L)

    /** Media3's duration after speed equals the domain's for every speed, span, and frame rate. */
    @Test
    fun durationsAgree() {
        frameRates.forEach { frameRate ->
            speeds.forEach { speed ->
                spans.forEach { span ->
                    val provider = SpeedProviders.of(speed, span, frameRate)
                    val expected = SpeedMap.of(speed, span, frameRate).durationMicros
                    assertEquals(
                        "$speed over $span at $frameRate",
                        expected,
                        SpeedProviderUtil.getDurationAfterSpeedProviderApplied(provider, span),
                    )
                }
            }
        }
    }

    /** Change times move forward, stay inside the span, and end with no further change. */
    @Test
    fun changeTimesMoveForward() {
        val provider = SpeedProviders.of(SpeedPreset.MONTAGE.curve, 10_000_000L, FrameRate.FPS_30)
        var time = 0L
        var steps = 0
        while (true) {
            val next = provider.getNextSpeedChangeTimeUs(time)
            if (next == C.TIME_UNSET) break
            assertTrue(next > time && next < 10_000_000L)
            assertTrue(provider.getSpeed(time) > 0f)
            time = next
            steps++
        }
        assertTrue(steps >= 300 - 1)
        val constant = SpeedProviders.of(ClipSpeed.Constant(2_000), 1_000L, FrameRate.FPS_30)
        assertEquals(C.TIME_UNSET, constant.getNextSpeedChangeTimeUs(0L))
    }
}

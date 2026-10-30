/*
 * Unit tests for the CPU mirror of the chroma key shader: what it keys out, how softly, and how it removes spill.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.ChromaKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tolerance for comparing colors and alpha. */
private const val DELTA = 1e-3f

/** Opaque pure green. */
private const val GREEN = 0xFF00FF00.toInt()

/** Opaque pure blue. */
private const val BLUE = 0xFF0000FF.toInt()

/** Verifies chroma key math against a green screen unless a test says otherwise. */
class ChromaKeyMathTest {
    private val green = ChromaKey(GREEN)

    /** The key color and its close neighbors key out; skin, gray, and red stay. */
    @Test
    fun keysGreenKeepsOthers() {
        assertEquals(0f, ChromaKeyMath.alphaOf(green, floatArrayOf(0f, 1f, 0f)), DELTA)
        assertEquals(0f, ChromaKeyMath.alphaOf(green, floatArrayOf(0.1f, 0.9f, 0.1f)), DELTA)
        assertEquals(1f, ChromaKeyMath.alphaOf(green, floatArrayOf(0.9f, 0.7f, 0.6f)), DELTA)
        assertEquals(1f, ChromaKeyMath.alphaOf(green, floatArrayOf(0.5f, 0.5f, 0.5f)), DELTA)
        assertEquals(1f, ChromaKeyMath.alphaOf(green, floatArrayOf(1f, 0f, 0f)), DELTA)
    }

    /** Softness ramps alpha between fully keyed and fully kept instead of a hard cut. */
    @Test
    fun softnessRamps() {
        val soft = green.copy(softnessPercent = 100)
        val uniforms = ChromaKeyMath.uniformsOf(soft)
        assertEquals(uniforms.tolerance, uniforms.ramp, DELTA)
        val partial = ChromaKeyMath.alphaOf(soft, floatArrayOf(0.1f, 0.9f, 0.1f))
        assertTrue("partial $partial", partial > 0f && partial < 1f)
    }

    /** Uniforms put the tolerance on the shared scale and find the key's dominant channel. */
    @Test
    fun uniformsScaleAndDominant() {
        val uniforms = ChromaKeyMath.uniformsOf(green)
        assertEquals(MAX_KEY_DISTANCE / 2f, uniforms.tolerance, DELTA)
        assertEquals(1, uniforms.dominantChannel)
        assertEquals(0.5f, uniforms.spill, DELTA)
        assertEquals(2, ChromaKeyMath.uniformsOf(ChromaKey(BLUE)).dominantChannel)
        assertArrayEquals(floatArrayOf(-0.331264f, -0.418688f), uniforms.keyChroma, DELTA)
    }

    /** Spill pulls green down toward the larger other channel by the spill share; full spill caps it there. */
    @Test
    fun spillPullsDominantChannel() {
        val full = green.copy(spillPercent = 100)
        val tinted = floatArrayOf(0.6f, 0.9f, 0.4f)
        assertArrayEquals(floatArrayOf(0.6f, 0.6f, 0.4f), ChromaKeyMath.despilled(full, tinted), DELTA)
        assertEquals(0.75f, ChromaKeyMath.despilled(green, tinted)[1], DELTA)
        assertArrayEquals(tinted, ChromaKeyMath.despilled(green.copy(spillPercent = 0), tinted), DELTA)
        val red = floatArrayOf(0.9f, 0.2f, 0.1f)
        assertArrayEquals(red, ChromaKeyMath.despilled(full, red), DELTA)
    }

    /** Channels unpack from ARGB. */
    @Test
    fun channelsUnpack() {
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), ChromaKeyMath.channelsOf(0xFFFF0000.toInt()), DELTA)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), ChromaKeyMath.channelsOf(BLUE), DELTA)
    }
}

/*
 * Unit tests for drawing looks: the color tables of the catalog filters and the adjustment effects.
 */
package com.framecraft.editor.engine

import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbMatrix
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.LookFilterCatalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tolerance for comparing matrix values. */
private const val DELTA = 1e-5f

/** Verifies color tables and effect lists. */
class LookEffectsTest {
    /** Returns the 8-bit red, green, and blue of packed [color]. */
    private fun channels(color: Int) = Triple(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF)

    /** Every catalog filter has a transform, and no unknown id does. */
    @Test
    fun catalogHasTransforms() {
        LookFilterCatalog.all.forEach { assertNotNull(it.id, LookFilterTransforms.transformFor(it.id)) }
        assertNull(LookFilterTransforms.transformFor("glow"))
        assertNull(LookLuts.cubeOf("glow", 100))
    }

    /** At intensity 0 a table is the identity: each lattice point keeps its own color, fully opaque. */
    @Test
    fun zeroIntensityIsIdentity() {
        val cube = requireNotNull(LookLuts.cubeOf("noir", 0))
        assertEquals(CUBE_SIZE, cube.size)
        assertEquals(Triple(0, 0, 0), channels(cube[0][0][0]))
        assertEquals(Triple(255, 255, 255), channels(cube[32][32][32]))
        assertEquals(Triple(255, 0, 128), channels(cube[32][0][16]))
        assertTrue(cube.all { plane -> plane.all { row -> row.all { it ushr 24 == 0xFF } } })
    }

    /** At full intensity a mono filter makes every lattice point gray; halfway lands between. */
    @Test
    fun intensityBlends() {
        val full = requireNotNull(LookLuts.cubeOf("silver", 100))
        val (r, g, b) = channels(full[32][0][0])
        assertEquals(r, g)
        assertEquals(g, b)
        val half = channels(requireNotNull(LookLuts.cubeOf("silver", 50))[32][0][0])
        assertEquals((255 + r) / 2.0, half.first.toDouble(), 1.0)
    }

    /** A table is built once per filter and intensity and then reused. */
    @Test
    fun tablesAreCached() {
        assertSame(LookLuts.cubeOf("honey", 70), LookLuts.cubeOf("honey", 70))
    }

    /** No look and a neutral look need no effect; saturation alone is one HSL adjustment. */
    @Test
    fun effectsSkipWhatIsUnset() {
        assertTrue(LookEffects.of(null).isEmpty())
        assertTrue(LookEffects.of(ClipLook()).isEmpty())
        val saturated = LookEffects.of(ClipLook(saturation = 30)).single() as HslAdjustment
        assertEquals(30f, saturated.saturationAdjustment, DELTA)
        assertNull(LookEffects.matrixOf(ClipLook(saturation = 30)))
    }

    /** Matrix adjustments come before saturation, in one matrix. */
    @Test
    fun matrixComesBeforeSaturation() {
        val effects = LookEffects.of(ClipLook(brightness = 10, saturation = -20))
        assertEquals(2, effects.size)
        assertTrue(effects[0] is RgbMatrix)
        assertTrue(effects[1] is HslAdjustment)
    }

    /** Brightness offsets, contrast scales around middle gray, exposure doubles, and white balance moves channels. */
    @Test
    fun matrixFollowsAdjustments() {
        val brighter = requireNotNull(LookEffects.matrixOf(ClipLook(brightness = 100)))
        val expected = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0.5f, 0.5f, 0.5f, 1f)
        assertArrayEquals(expected, brighter, DELTA)
        val contrasty = requireNotNull(LookEffects.matrixOf(ClipLook(contrast = 50)))
        assertEquals(2f, contrasty[0], DELTA)
        assertEquals(-0.5f, contrasty[12], DELTA)
        val darker = requireNotNull(LookEffects.matrixOf(ClipLook(exposure = -50)))
        assertEquals(0.5f, darker[5], DELTA)
        assertEquals(0f, darker[12], DELTA)
        val warm = requireNotNull(LookEffects.matrixOf(ClipLook(temperature = 100, tint = 50)))
        assertEquals(1.2f, warm[0], DELTA)
        assertEquals(0.9f, warm[5], DELTA)
        assertEquals(0.8f, warm[10], DELTA)
    }
}

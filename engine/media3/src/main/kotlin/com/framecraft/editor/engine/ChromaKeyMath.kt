/*
 * The math of chroma keys, shared by the key shader's uniforms and a CPU mirror of the shader for tests.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.domain.model.MAX_KEY_PERCENT
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Chroma distance keyed out at full tolerance, in BT.601 CbCr units. */
internal const val MAX_KEY_DISTANCE = 0.4f

/** Narrowest edge ramp, in CbCr units, so a softness of 0 never divides by zero. */
internal const val MIN_KEY_RAMP = 0.0001f

/** Largest value of an 8-bit channel. */
private const val CHANNEL_MAX = 255f

/** Bit offset of the red channel in an ARGB color. */
private const val RED_SHIFT = 16

/** Bit offset of the green channel in an ARGB color. */
private const val GREEN_SHIFT = 8

/** Mask of one 8-bit channel. */
private const val CHANNEL_MASK = 0xFF

/** BT.601 weights of red, green, and blue in Cb. */
private val CB_WEIGHTS = floatArrayOf(-0.168736f, -0.331264f, 0.5f)

/** BT.601 weights of red, green, and blue in Cr. */
private val CR_WEIGHTS = floatArrayOf(0.5f, -0.418688f, -0.081312f)

/** The key shader's values for one key. */
internal class ChromaKeyUniforms(
    val keyChroma: FloatArray,
    val tolerance: Float,
    val ramp: Float,
    val spill: Float,
    val dominantChannel: Int,
)

/** Chroma key math. */
internal object ChromaKeyMath {
    /** Returns the uniforms that draw [key]. */
    fun uniformsOf(key: ChromaKey): ChromaKeyUniforms {
        val rgb = channelsOf(key.keyColorArgb)
        val tolerance = MAX_KEY_DISTANCE * key.tolerancePercent / MAX_KEY_PERCENT
        return ChromaKeyUniforms(
            keyChroma = chromaOf(rgb),
            tolerance = tolerance,
            ramp = max(tolerance * key.softnessPercent / MAX_KEY_PERCENT, MIN_KEY_RAMP),
            spill = key.spillPercent.toFloat() / MAX_KEY_PERCENT,
            dominantChannel = rgb.indices.maxBy { rgb[it] },
        )
    }

    /** Returns the red, green, and blue of [argb] from 0 to 1. */
    fun channelsOf(argb: Int): FloatArray =
        floatArrayOf(
            (argb shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
            (argb shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
            (argb and CHANNEL_MASK) / CHANNEL_MAX,
        )

    /** Returns the BT.601 Cb and Cr of [rgb]. */
    fun chromaOf(rgb: FloatArray): FloatArray =
        floatArrayOf(
            CB_WEIGHTS.indices.sumOf { (CB_WEIGHTS[it] * rgb[it]).toDouble() }.toFloat(),
            CR_WEIGHTS.indices.sumOf { (CR_WEIGHTS[it] * rgb[it]).toDouble() }.toFloat(),
        )

    /** Returns how much of [rgb] [key] leaves visible: 0 keyed out, 1 kept. It matches the key shader. */
    fun alphaOf(
        key: ChromaKey,
        rgb: FloatArray,
    ): Float {
        val uniforms = uniformsOf(key)
        val chroma = chromaOf(rgb)
        val distance = hypot(chroma[0] - uniforms.keyChroma[0], chroma[1] - uniforms.keyChroma[1])
        return ((distance - (uniforms.tolerance - uniforms.ramp)) / uniforms.ramp).coerceIn(0f, 1f)
    }

    /**
     * Returns [rgb] with the key color's dominant channel pulled down toward the larger of the other two by the key's
     * spill share, which removes the key's cast from edges and reflections. It matches the key shader.
     */
    fun despilled(
        key: ChromaKey,
        rgb: FloatArray,
    ): FloatArray {
        val uniforms = uniformsOf(key)
        val dominant = uniforms.dominantChannel
        val others = rgb.indices.filter { it != dominant }.maxOf { rgb[it] }
        val limited = min(rgb[dominant], others)
        return rgb.copyOf().also { it[dominant] = rgb[dominant] + (limited - rgb[dominant]) * uniforms.spill }
    }
}

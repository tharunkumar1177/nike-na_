/*
 * Color tables for the catalog filters: each filter blended in at an intensity and sampled on a lattice, cached.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.MAX_FILTER_INTENSITY
import kotlin.math.roundToInt

/** Lattice points along each axis of a color table. */
internal const val CUBE_SIZE = 33

/** Color tables kept at once; each holds about 140 KB. */
private const val MAX_CACHED = 8

/** Largest 8-bit channel value. */
private const val CHANNEL_MAX = 255

/** Opaque alpha in the top byte of a packed color. */
private const val OPAQUE = 0xFF shl 24

/** Bit shift of red in a packed color. */
private const val RED_SHIFT = 16

/** Bit shift of green in a packed color. */
private const val GREEN_SHIFT = 8

/** Load factor of the cache map. */
private const val LOAD_FACTOR = 0.75f

/** Builds and caches the color table of each filter at each intensity. */
internal object LookLuts {
    private val cache =
        object : LinkedHashMap<Pair<String, Int>, Array<Array<IntArray>>>(MAX_CACHED, LOAD_FACTOR, true) {
            /** Drops the least recently used table once more than [MAX_CACHED] are kept. */
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<String, Int>, Array<Array<IntArray>>>?,
            ): Boolean = size > MAX_CACHED
        }

    /**
     * Returns the table of filter [filterId] blended in at [intensityPercent], indexed red, green, blue, with opaque
     * packed colors, or null when this build does not know the filter. Callers must not change the table.
     */
    @Synchronized
    fun cubeOf(
        filterId: String,
        intensityPercent: Int,
    ): Array<Array<IntArray>>? {
        val transform = LookFilterTransforms.transformFor(filterId) ?: return null
        val strength = intensityPercent.toFloat() / MAX_FILTER_INTENSITY
        return cache.getOrPut(filterId to intensityPercent) { sample(transform, strength) }
    }

    /** Returns the lattice of each color moved [strength] of the way toward [transform] of it. */
    private fun sample(
        transform: ColorTransform,
        strength: Float,
    ): Array<Array<IntArray>> {
        val step = 1f / (CUBE_SIZE - 1)
        return Array(CUBE_SIZE) { red ->
            Array(CUBE_SIZE) { green ->
                IntArray(CUBE_SIZE) { blue ->
                    val color = Rgb(red * step, green * step, blue * step)
                    val target = transform(color)
                    pack(
                        Rgb(
                            color.r + (target.r - color.r) * strength,
                            color.g + (target.g - color.g) * strength,
                            color.b + (target.b - color.b) * strength,
                        ),
                    )
                }
            }
        }
    }

    /** Returns [color] held to 0 to 1 and packed as an opaque 8-bit color. */
    private fun pack(color: Rgb): Int {
        val red = (color.r.coerceIn(0f, 1f) * CHANNEL_MAX).roundToInt()
        val green = (color.g.coerceIn(0f, 1f) * CHANNEL_MAX).roundToInt()
        val blue = (color.b.coerceIn(0f, 1f) * CHANNEL_MAX).roundToInt()
        return OPAQUE or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }
}

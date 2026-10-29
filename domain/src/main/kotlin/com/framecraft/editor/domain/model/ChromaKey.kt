/*
 * A chroma key on a picture clip: one color that turns transparent, with how widely and softly it keys.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Highest tolerance, softness, and spill suppression, in percent. */
const val MAX_KEY_PERCENT: Int = 100

/** Tolerance of a newly picked key color, in percent. */
const val DEFAULT_KEY_TOLERANCE: Int = 50

/** Edge softness of a newly picked key color, in percent. */
const val DEFAULT_KEY_SOFTNESS: Int = 20

/** Spill suppression of a newly picked key color, in percent. */
const val DEFAULT_KEY_SPILL: Int = 50

/** Alpha bits of a fully opaque ARGB color. */
private const val OPAQUE_KEY_ALPHA: Int = 0xFF

/** Bit offset of the alpha channel in an ARGB color. */
private const val KEY_ALPHA_SHIFT: Int = 24

/**
 * Keys out the opaque color [keyColorArgb]: colors within [tolerancePercent] of it turn transparent, the next
 * [softnessPercent] of that range fades back in, and [spillPercent] pulls the key color out of the edges that remain.
 * A tolerance of 0 keys nothing; commands store such a key as none.
 */
@Serializable
data class ChromaKey(
    val keyColorArgb: Int,
    val tolerancePercent: Int = DEFAULT_KEY_TOLERANCE,
    val softnessPercent: Int = DEFAULT_KEY_SOFTNESS,
    val spillPercent: Int = DEFAULT_KEY_SPILL,
) {
    init {
        require(keyColorArgb ushr KEY_ALPHA_SHIFT == OPAQUE_KEY_ALPHA) {
            "Key color must be opaque, was ${keyColorArgb.toUInt()}"
        }
        require(tolerancePercent in 0..MAX_KEY_PERCENT) { "Key tolerance out of range: $tolerancePercent" }
        require(softnessPercent in 0..MAX_KEY_PERCENT) { "Key softness out of range: $softnessPercent" }
        require(spillPercent in 0..MAX_KEY_PERCENT) { "Key spill out of range: $spillPercent" }
    }

    /** True when nothing is keyed out. */
    val isNeutral: Boolean
        get() = tolerancePercent == 0
}

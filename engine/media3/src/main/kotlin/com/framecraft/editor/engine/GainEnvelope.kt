/*
 * How loud a clip plays at each moment: its volume shaped by linear fades at both ends.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT

/**
 * Gain over a clip lasting [durationMicros]: [volumePercent] of the source level, rising linearly from silence over
 * [fadeInMicros] and falling linearly to silence over the last [fadeOutMicros]. Positions are measured from the
 * clip start.
 */
internal data class GainEnvelope(
    val volumePercent: Int,
    val fadeInMicros: Long,
    val fadeOutMicros: Long,
    val durationMicros: Long,
) {
    /** True when the envelope leaves every sample unchanged. */
    val isUnity: Boolean
        get() = volumePercent == UNITY_VOLUME_PERCENT && fadeInMicros == 0L && fadeOutMicros == 0L

    /** Returns the gain factor at [positionMicros] from the clip start. */
    fun gainAt(positionMicros: Long): Float {
        val volume = volumePercent.toFloat() / UNITY_VOLUME_PERCENT
        val fadeIn =
            if (fadeInMicros > 0 && positionMicros < fadeInMicros) {
                positionMicros.coerceAtLeast(0L).toFloat() / fadeInMicros
            } else {
                1f
            }
        val fadeOutStart = durationMicros - fadeOutMicros
        val fadeOut =
            if (fadeOutMicros > 0 && positionMicros >= fadeOutStart) {
                ((durationMicros - positionMicros).toFloat() / fadeOutMicros).coerceIn(0f, 1f)
            } else {
                1f
            }
        return volume * fadeIn * fadeOut
    }
}

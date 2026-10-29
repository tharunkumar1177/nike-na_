/*
 * How loud a clip plays at each moment: its volume shaped by linear fades at both ends and dips under voiceovers.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT
import com.framecraft.editor.engine.plan.DUCK_RAMP_MICROS
import com.framecraft.editor.engine.plan.DuckWindow

/**
 * Gain over a clip lasting [durationMicros]: [volumePercent] of the source level, rising linearly from silence over
 * [fadeInMicros], falling linearly to silence over the last [fadeOutMicros], and lowered during each of
 * [duckWindows] with linear ramps of [DUCK_RAMP_MICROS] on either side. Positions are measured from the clip start.
 */
internal data class GainEnvelope(
    val volumePercent: Int,
    val fadeInMicros: Long,
    val fadeOutMicros: Long,
    val durationMicros: Long,
    val duckWindows: List<DuckWindow> = emptyList(),
) {
    /** True when the envelope leaves every sample unchanged. */
    val isUnity: Boolean
        get() =
            volumePercent == UNITY_VOLUME_PERCENT && fadeInMicros == 0L && fadeOutMicros == 0L && duckWindows.isEmpty()

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
        val duck = duckWindows.minOfOrNull { duckAt(it, positionMicros) } ?: 1f
        return volume * fadeIn * fadeOut * duck
    }

    /** Returns how far [window] lowers the clip at [positionMicros]: 1 outside it and its ramps, its floor inside. */
    private fun duckAt(
        window: DuckWindow,
        positionMicros: Long,
    ): Float {
        val depth = 1f - window.floorGain
        val rampStart = window.startMicros - DUCK_RAMP_MICROS
        val rampEnd = window.endMicros + DUCK_RAMP_MICROS
        return when {
            positionMicros < rampStart || positionMicros >= rampEnd -> 1f
            positionMicros < window.startMicros -> 1f - depth * (positionMicros - rampStart) / DUCK_RAMP_MICROS
            positionMicros < window.endMicros -> window.floorGain
            else -> window.floorGain + depth * (positionMicros - window.endMicros) / DUCK_RAMP_MICROS
        }
    }
}

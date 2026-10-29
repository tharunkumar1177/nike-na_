/*
 * Media3 speed providers built from the domain's speed steps, so the player plays each clip for exactly the duration
 * the timeline shows.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.time.FrameRate

/** Creates speed providers for sped clips. */
@OptIn(markerClass = [UnstableApi::class])
internal object SpeedProviders {
    /** Returns the provider that plays [sourceSpanMicros] of clipped source at [speed] in a project at [frameRate]. */
    fun of(
        speed: ClipSpeed,
        sourceSpanMicros: Long,
        frameRate: FrameRate,
    ): SpeedProvider = StepSpeedProvider(SpeedMap.of(speed, sourceSpanMicros, frameRate))

    /** Plays the steps of [map], whose times are offsets into the clipped source. */
    private class StepSpeedProvider(
        private val map: SpeedMap,
    ) : SpeedProvider {
        /** Returns the speed of the step playing [timeUs]. */
        override fun getSpeed(timeUs: Long): Float = map.speedAt(timeUs.coerceAtLeast(0L))

        /** Returns where the step after the one playing [timeUs] begins, or [C.TIME_UNSET] after the last. */
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
            map.nextChangeAfter(timeUs.coerceAtLeast(0L)) ?: C.TIME_UNSET
    }
}

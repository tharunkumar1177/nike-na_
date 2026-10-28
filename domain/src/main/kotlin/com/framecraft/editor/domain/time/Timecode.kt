/*
 * Human-readable timecode formatting (M:SS, or H:MM:SS at an hour or more).
 */
package com.framecraft.editor.domain.time

import java.util.Locale

/** Seconds in a minute. */
private const val SECONDS_PER_MINUTE = 60L

/** Seconds in an hour. */
private const val SECONDS_PER_HOUR = 3_600L

/** Microseconds in an hour. */
const val MICROS_PER_HOUR: Long = SECONDS_PER_HOUR * MICROS_PER_SECOND

/** Whole hours, minutes, and seconds of a duration, truncated toward zero. */
data class TimeParts(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
)

/** Formats microsecond times for display. */
object Timecode {
    /** Splits [micros] into whole hours, minutes, and seconds. */
    fun parts(micros: Long): TimeParts {
        require(micros >= 0) { "Time must be non-negative, was $micros" }
        val totalSeconds = micros / MICROS_PER_SECOND
        return TimeParts(
            hours = totalSeconds / SECONDS_PER_HOUR,
            minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            seconds = totalSeconds % SECONDS_PER_MINUTE,
        )
    }

    /** Formats [micros] as M:SS, or H:MM:SS when [includeHours] is true. */
    fun format(
        micros: Long,
        includeHours: Boolean = micros >= MICROS_PER_HOUR,
    ): String {
        val (hours, minutes, seconds) = parts(micros)
        return if (includeHours) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", hours * SECONDS_PER_MINUTE + minutes, seconds)
        }
    }

    /** Formats "position / duration", using hours for both when [durationMicros] reaches an hour. */
    fun formatProgress(
        positionMicros: Long,
        durationMicros: Long,
    ): String {
        val includeHours = durationMicros >= MICROS_PER_HOUR
        return "${format(positionMicros, includeHours)} / ${format(durationMicros, includeHours)}"
    }
}

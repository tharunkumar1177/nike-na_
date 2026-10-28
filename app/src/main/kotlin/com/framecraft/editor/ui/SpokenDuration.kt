/*
 * Spoken forms of durations for TalkBack, for example "1 minute 23 seconds".
 */
package com.framecraft.editor.ui

import android.content.res.Resources
import com.framecraft.editor.R
import com.framecraft.editor.domain.time.Timecode

/** Returns [micros] as words, omitting zero units except when the whole duration is zero. */
fun spokenDuration(
    resources: Resources,
    micros: Long,
): String {
    val parts = Timecode.parts(micros)
    return buildList {
        if (parts.hours > 0) {
            add(resources.getQuantityString(R.plurals.duration_hours, parts.hours.toInt(), parts.hours))
        }
        if (parts.minutes > 0) {
            add(resources.getQuantityString(R.plurals.duration_minutes, parts.minutes.toInt(), parts.minutes))
        }
        if (parts.seconds > 0 || isEmpty()) {
            add(resources.getQuantityString(R.plurals.duration_seconds, parts.seconds.toInt(), parts.seconds))
        }
    }.joinToString(" ")
}

/*
 * Validates raw MediaMetadataRetriever strings for a sound file.
 */
package com.framecraft.editor.media

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Parses retriever metadata strings of audio files. */
object AudioMetadataParser {
    /** Returns the duration in microseconds when the file declares sound and a positive duration; otherwise null. */
    fun parseDurationMicros(
        durationMs: String?,
        hasAudio: String?,
    ): Long? =
        durationMs
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 && hasAudio?.trim() == "yes" }
            ?.let { it * MICROS_PER_MILLI }
}

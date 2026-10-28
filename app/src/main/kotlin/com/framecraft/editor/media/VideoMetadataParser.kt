/*
 * Validates raw MediaMetadataRetriever strings into typed video metadata.
 */
package com.framecraft.editor.media

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Rotations accepted from container metadata. */
private val SUPPORTED_ROTATIONS = setOf(0, 90, 180, 270)

/** Video properties needed to create an asset. */
data class VideoMetadata(
    val durationMicros: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val hasAudio: Boolean,
)

/** Parses retriever metadata strings. */
object VideoMetadataParser {
    /** Returns metadata when duration and dimensions are present and positive; otherwise null. */
    fun parse(
        durationMs: String?,
        width: String?,
        height: String?,
        rotation: String?,
        hasAudio: String?,
    ): VideoMetadata? {
        val duration = durationMs?.trim()?.toLongOrNull()?.takeIf { it > 0 }
        val parsedWidth = width?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val parsedHeight = height?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        if (duration == null || parsedWidth == null || parsedHeight == null) return null
        val parsedRotation = rotation?.trim()?.toIntOrNull()?.takeIf { it in SUPPORTED_ROTATIONS } ?: 0
        return VideoMetadata(
            durationMicros = duration * MICROS_PER_MILLI,
            width = parsedWidth,
            height = parsedHeight,
            rotationDegrees = parsedRotation,
            hasAudio = hasAudio?.trim() == "yes",
        )
    }
}

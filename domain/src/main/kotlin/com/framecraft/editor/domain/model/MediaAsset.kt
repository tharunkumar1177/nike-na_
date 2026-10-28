/*
 * Imported source media referenced by clips, with the fingerprint used to detect missing or replaced files.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Kind of source media. */
@Serializable
enum class MediaKind {
    VIDEO,
    IMAGE,
}

/** Identifying properties of a source file, used to detect that it went missing or was replaced. */
@Serializable
data class SourceFingerprint(
    val sizeBytes: Long?,
    val lastModifiedMillis: Long?,
)

/** Rotations a source may declare, in degrees clockwise. */
private val VALID_ROTATIONS = setOf(0, 90, 180, 270)

/** A half turn in degrees; rotations that are multiples of it keep width and height. */
private const val HALF_TURN_DEGREES = 180

/**
 * A source file imported into a project. Videos carry their full duration; images have none
 * because a clip decides how long an image shows.
 */
@Serializable
data class MediaAsset(
    val id: String,
    val uri: String,
    val kind: MediaKind,
    val durationMicros: Long?,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val hasAudio: Boolean,
    val fingerprint: SourceFingerprint,
) {
    init {
        require(id.isNotBlank()) { "Asset id must not be blank" }
        require(width > 0 && height > 0) { "Asset $id must have positive dimensions, was ${width}x$height" }
        require(rotationDegrees in VALID_ROTATIONS) { "Asset $id has invalid rotation $rotationDegrees" }
        when (kind) {
            MediaKind.VIDEO -> {
                require(durationMicros != null && durationMicros > 0) { "Video asset $id needs a positive duration" }
            }

            MediaKind.IMAGE -> {
                require(durationMicros == null) { "Image asset $id must not declare a duration" }
                require(!hasAudio) { "Image asset $id cannot have audio" }
            }
        }
    }

    /** Width divided by height as displayed, after applying rotation. */
    val displayAspectRatio: Float
        get() = if (rotationDegrees % HALF_TURN_DEGREES == 0) width.toFloat() / height else height.toFloat() / width
}

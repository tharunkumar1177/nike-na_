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
    AUDIO,

    /** The shared stand-in for text clips, which have no source file. */
    TEXT,
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

/** Width and height every audio asset declares, since sound has no picture. */
const val AUDIO_ASSET_SIDE: Int = 1

/**
 * A source file imported into a project. Videos and audio carry their full duration; images have none
 * because a clip decides how long an image shows. Audio assets are 1x1 and unrotated; the text asset is 1x1,
 * unrotated, silent, and has no duration.
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

            MediaKind.AUDIO -> {
                require(durationMicros != null && durationMicros > 0) { "Audio asset $id needs a positive duration" }
                require(width == AUDIO_ASSET_SIDE && height == AUDIO_ASSET_SIDE) { "Audio asset $id must be 1x1" }
                require(rotationDegrees == 0 && hasAudio) { "Audio asset $id must be unrotated and have audio" }
            }

            MediaKind.TEXT -> {
                require(durationMicros == null && !hasAudio) { "Text asset $id must have no duration or audio" }
                val unitSize = width == 1 && height == 1 && rotationDegrees == 0
                require(unitSize) { "Text asset $id must be 1x1 and unrotated" }
            }
        }
    }

    /** True for media with its own timeline (video and audio): clips read a span of it and can carry sound. */
    val isTimed: Boolean
        get() = kind == MediaKind.VIDEO || kind == MediaKind.AUDIO

    /** Width divided by height as displayed, after applying rotation. */
    val displayAspectRatio: Float
        get() = if (rotationDegrees % HALF_TURN_DEGREES == 0) width.toFloat() / height else height.toFloat() / width
}

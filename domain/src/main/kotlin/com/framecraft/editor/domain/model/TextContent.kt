/*
 * What a text clip says and how it enters and leaves, plus the one shared asset every text clip points at.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Longest text a clip may hold, in characters. */
const val MAX_TEXT_LENGTH: Int = 500

/** Id of the shared asset every text clip references; text has no source file. */
const val TEXT_ASSET_ID: String = "text-asset"

/** How long a text animation lasts when the user first picks it. */
const val DEFAULT_TEXT_ANIMATION_MICROS: Long = 500_000L

/** The shared asset text clips reference so every clip still resolves to an asset. */
val TEXT_ASSET: MediaAsset =
    MediaAsset(
        id = TEXT_ASSET_ID,
        uri = "",
        kind = MediaKind.TEXT,
        durationMicros = null,
        width = 1,
        height = 1,
        rotationDegrees = 0,
        hasAudio = false,
        fingerprint = SourceFingerprint(sizeBytes = null, lastModifiedMillis = null),
    )

/** Ways a text clip can appear or disappear. */
@Serializable
enum class TextAnimationKind {
    /** Opacity rises from zero. */
    FADE,

    /** The text rises into place while fading in. */
    SLIDE_UP,

    /** The text grows past full size and settles. */
    POP,

    /** Characters appear one after another. */
    TYPEWRITER,
}

/** An entrance or exit of [kind] lasting [durationMicros]. */
@Serializable
data class TextAnimation(
    val kind: TextAnimationKind,
    val durationMicros: Long,
) {
    init {
        require(durationMicros > 0) { "Text animation must last a positive time, was $durationMicros" }
    }
}

/**
 * The [text] a clip shows in [style], entering with [animationIn] and leaving with [animationOut]. An auto-caption
 * also carries the timed speech it transcribes as [caption]; its text is then the words its clip can still play.
 */
@Serializable
data class TextContent(
    val text: String,
    val style: TextStyle = TextStyle(),
    val animationIn: TextAnimation? = null,
    val animationOut: TextAnimation? = null,
    val caption: CaptionContent? = null,
) {
    init {
        require(text.isNotBlank()) { "Text must not be blank" }
        require(text.length <= MAX_TEXT_LENGTH) { "Text is longer than $MAX_TEXT_LENGTH characters" }
    }
}

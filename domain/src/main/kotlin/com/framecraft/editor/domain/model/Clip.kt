/*
 * A placed segment of a media asset on a track.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Loudest clip volume, in percent of the source level. */
const val MAX_VOLUME_PERCENT: Int = 200

/** Clip volume that leaves the source level unchanged. */
const val UNITY_VOLUME_PERCENT: Int = 100

/**
 * A segment of [assetId] placed on the timeline at [startMicros] for [durationMicros],
 * reading the source from [trimStartMicros]. The timeline extent is half-open. An [anchor] keeps the clip in sync
 * with another clip; [startMicros] then always equals the start the anchor resolves to. Overlay clips carry an
 * [overlayTransform]. [volumePercent] scales the clip's sound, where 0 mutes it; the sound rises from silence over
 * [fadeInMicros] and falls back to silence over the last [fadeOutMicros], and the two fades never overlap. Text clips
 * carry [text], whose animations each fit within the clip.
 */
@Serializable
data class Clip(
    val id: String,
    val assetId: String,
    val startMicros: Long,
    val trimStartMicros: Long,
    val durationMicros: Long,
    val anchor: ClipAnchor? = null,
    val overlayTransform: OverlayTransform? = null,
    val volumePercent: Int = UNITY_VOLUME_PERCENT,
    val fadeInMicros: Long = 0,
    val fadeOutMicros: Long = 0,
    val text: TextContent? = null,
) {
    init {
        require(id.isNotBlank()) { "Clip id must not be blank" }
        require(startMicros >= 0) { "Clip $id starts before zero: $startMicros" }
        require(trimStartMicros >= 0) { "Clip $id has negative trim start: $trimStartMicros" }
        require(durationMicros > 0) { "Clip $id must have positive duration, was $durationMicros" }
        require(volumePercent in 0..MAX_VOLUME_PERCENT) { "Clip $id has volume out of range: $volumePercent" }
        require(fadeInMicros >= 0 && fadeOutMicros >= 0) { "Clip $id has a negative fade" }
        require(fadeInMicros + fadeOutMicros <= durationMicros) { "Clip $id has fades longer than the clip" }
        val animations = listOfNotNull(text?.animationIn, text?.animationOut)
        require(animations.all { it.durationMicros <= durationMicros }) { "Clip $id has an animation longer than it" }
    }

    /** Exclusive end of the clip on the timeline. */
    val endMicros: Long
        get() = startMicros + durationMicros

    /** True when the clip keeps the default sound: unity volume and no fades. */
    val hasDefaultAudio: Boolean
        get() = volumePercent == UNITY_VOLUME_PERCENT && fadeInMicros == 0L && fadeOutMicros == 0L
}

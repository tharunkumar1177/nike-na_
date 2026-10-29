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
 * carry [text], whose animations each fit within the clip. Base clips may carry a [canvasPlacement]; without one they
 * fit the canvas, centered and upright. A timed clip may play at a [speed]; it then reads [sourceDurationMicros] of
 * source, and its sound keeps its pitch when [maintainPitch] is set. Without a speed the clip reads exactly its own
 * duration of source. A video-track clip may carry the [outTransition] into the clip that starts where it ends; it
 * stays stored while the cut is gone or too short and plays again once the cut allows it. A clip on a video or
 * overlay track may carry a [look] that colors its picture.
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
    val canvasPlacement: CanvasPlacement? = null,
    val speed: ClipSpeed? = null,
    val sourceDurationMicros: Long? = null,
    val maintainPitch: Boolean = true,
    val outTransition: ClipTransition? = null,
    val look: ClipLook? = null,
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
        require((speed == null) == (sourceDurationMicros == null)) {
            "Clip $id needs a source length exactly when sped"
        }
        require(sourceDurationMicros == null || sourceDurationMicros > 0) { "Clip $id reads no source" }
    }

    /** Length of source the clip reads: its own duration at normal speed. */
    val sourceSpanMicros: Long
        get() = sourceDurationMicros ?: durationMicros

    /** Exclusive end of the clip on the timeline. */
    val endMicros: Long
        get() = startMicros + durationMicros

    /** True when the clip keeps the default sound: unity volume and no fades. */
    val hasDefaultAudio: Boolean
        get() = volumePercent == UNITY_VOLUME_PERCENT && fadeInMicros == 0L && fadeOutMicros == 0L
}

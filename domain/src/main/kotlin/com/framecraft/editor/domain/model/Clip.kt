/*
 * A placed segment of a media asset on a track.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/**
 * A segment of [assetId] placed on the timeline at [startMicros] for [durationMicros],
 * reading the source from [trimStartMicros]. The timeline extent is half-open.
 */
@Serializable
data class Clip(
    val id: String,
    val assetId: String,
    val startMicros: Long,
    val trimStartMicros: Long,
    val durationMicros: Long,
) {
    init {
        require(id.isNotBlank()) { "Clip id must not be blank" }
        require(startMicros >= 0) { "Clip $id starts before zero: $startMicros" }
        require(trimStartMicros >= 0) { "Clip $id has negative trim start: $trimStartMicros" }
        require(durationMicros > 0) { "Clip $id must have positive duration, was $durationMicros" }
    }

    /** Exclusive end of the clip on the timeline. */
    val endMicros: Long
        get() = startMicros + durationMicros
}

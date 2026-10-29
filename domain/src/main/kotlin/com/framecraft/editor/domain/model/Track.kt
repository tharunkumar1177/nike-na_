/*
 * A timeline lane holding non-overlapping clips, optionally magnetic (gap-free).
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** What a track carries. */
@Serializable
enum class TrackKind {
    /** The base video. */
    VIDEO,

    /** Picture-in-picture clips drawn over the base video. */
    OVERLAY,

    /** Sound-only clips mixed under the picture. */
    AUDIO,

    /** Text clips drawn above every picture. */
    TEXT,
    ;

    /** True for kinds whose clips are placed on the canvas with an overlay transform. */
    val isPlaced: Boolean
        get() = this == OVERLAY || this == TEXT

    /** True for kinds whose clips may play sound, so the mixer offers their volume. */
    val takesVolume: Boolean
        get() = this != TEXT
}

/**
 * An ordered lane of clips. Clips never overlap; gaps are implicit. A [magnetic] track
 * keeps its clips contiguous from time zero. [volumePercent] scales the sound of every clip on it, where 0 mutes the
 * track; text tracks keep unity.
 */
@Serializable
data class Track(
    val id: String,
    val kind: TrackKind,
    val magnetic: Boolean,
    val clips: List<Clip>,
    val volumePercent: Int = UNITY_VOLUME_PERCENT,
) {
    init {
        require(id.isNotBlank()) { "Track id must not be blank" }
        require(volumePercent in 0..MAX_VOLUME_PERCENT) { "Track $id has volume out of range: $volumePercent" }
        require(kind.takesVolume || volumePercent == UNITY_VOLUME_PERCENT) { "Text track $id has a volume" }
        clips.zipWithNext().forEach { (previous, next) ->
            require(previous.endMicros <= next.startMicros) {
                "Clips ${previous.id} and ${next.id} on track $id overlap or are out of order"
            }
        }
        if (magnetic) {
            require((clips.firstOrNull()?.startMicros ?: 0L) == 0L) { "Magnetic track $id must start at zero" }
            clips.zipWithNext().forEach { (previous, next) ->
                require(previous.endMicros == next.startMicros) {
                    "Magnetic track $id has a gap between ${previous.id} and ${next.id}"
                }
            }
        }
    }

    /** Exclusive end of the last clip, or zero for an empty track. */
    val endMicros: Long
        get() = clips.lastOrNull()?.endMicros ?: 0L
}

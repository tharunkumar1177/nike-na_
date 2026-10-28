/*
 * A timeline lane holding non-overlapping clips, optionally magnetic (gap-free).
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** What a track carries. */
@Serializable
enum class TrackKind {
    VIDEO,
}

/**
 * An ordered lane of clips. Clips never overlap; gaps are implicit. A [magnetic] track
 * keeps its clips contiguous from time zero.
 */
@Serializable
data class Track(
    val id: String,
    val kind: TrackKind,
    val magnetic: Boolean,
    val clips: List<Clip>,
) {
    init {
        require(id.isNotBlank()) { "Track id must not be blank" }
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

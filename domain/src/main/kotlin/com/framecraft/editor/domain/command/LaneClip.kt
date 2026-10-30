/*
 * A clip paired with the lane it sits on, and the track edits that take such clips out and put them back.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import kotlinx.serialization.Serializable

/** [clip] on track [trackId], kept so an inverse can put it back where it was. */
@Serializable
data class LaneClip(
    val trackId: String,
    val clip: Clip,
)

/**
 * Returns the captions in this project transcribing clip [parentClipId] that would show no words if it read
 * [sourceSpanMicros] of source from [trimStartMicros], each with its lane.
 */
internal fun Project.captionsSilencedBy(
    parentClipId: String,
    trimStartMicros: Long,
    sourceSpanMicros: Long,
): List<LaneClip> {
    val read = trimStartMicros until trimStartMicros + sourceSpanMicros
    return tracks.flatMap { track ->
        val silenced = track.clips.filter { it.anchor?.parentClipId == parentClipId && it.showsNoWordsOf(read) }
        silenced.map { LaneClip(track.id, it) }
    }
}

/** True when this clip is a caption none of whose words start inside [read]. */
private fun Clip.showsNoWordsOf(read: LongRange): Boolean =
    text?.caption?.words?.none { it.sourceStartMicros in read } == true

/**
 * Returns these tracks without the clips in [removedIds] and with each of [added] on its lane, re-sorted. Throws
 * [IllegalArgumentException] when a lane for [added] is gone.
 */
internal fun List<Track>.swappingClips(
    removedIds: Set<String>,
    added: List<LaneClip>,
): List<Track> {
    val missing = added.map { it.trackId }.toSet() - map { it.id }.toSet()
    require(missing.isEmpty()) { "Lanes $missing are gone" }
    return map { track ->
        val incoming = added.filter { it.trackId == track.id }.map { it.clip }
        if (incoming.isEmpty() && track.clips.none { it.id in removedIds }) {
            track
        } else {
            track.copy(clips = (track.clips.filter { it.id !in removedIds } + incoming).sortedBy { it.startMicros })
        }
    }
}

/*
 * Command that adds, regenerates, or removes auto-captions across their caption lanes as one undoable edit.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TEXT_ASSET
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On text lane [trackId], removes the captions [removedIds] and places the captions [added]. A missing lane is
 * created at position [trackIndex] of the track list, clamped to the list; a lane left empty is removed.
 */
@Serializable
data class CaptionLaneChange(
    val trackId: String,
    val trackIndex: Int,
    val removedIds: List<String>,
    val added: List<Clip>,
)

/**
 * Applies each of [changes] in order. Every removed or added clip must be a caption; an overlap, a missing clip, or a
 * caption out of step with its speech is rejected. The shared text asset is added for the first text clip and goes
 * with the last.
 */
@Serializable
@SerialName("replaceCaptions")
data class ReplaceCaptions(
    val changes: List<CaptionLaneChange>,
) : EditCommand {
    /** The inverse undoes each lane change in reverse order, putting removed captions and emptied lanes back. */
    override fun applyTo(project: Project): CommandOutcome {
        val inverses = mutableListOf<CaptionLaneChange>()
        val built =
            runCatching {
                changes.fold(project.tracks) { tracks, change ->
                    inverses.add(0, inverseOf(tracks, change))
                    tracks.applying(change)
                }
            }
        return built.fold(
            onSuccess = { tracks ->
                val assets = project.assetsFor(tracks)
                project.replacingTracks(ReplaceCaptions(inverses), assets) { tracks }
            },
            onFailure = { modelInvariant(it.message.orEmpty()) },
        )
    }

    /** Returns the change that undoes [change] when applied to the tracks [change] produces from [tracks]. */
    private fun inverseOf(
        tracks: List<Track>,
        change: CaptionLaneChange,
    ): CaptionLaneChange {
        val index = tracks.indexOfFirst { it.id == change.trackId }
        val lane = tracks.getOrNull(index)
        val removed = change.removedIds.map { id -> requireNotNull(lane?.clips?.firstOrNull { it.id == id }) { id } }
        val position = if (index >= 0) index else change.trackIndex.coerceIn(0, tracks.size)
        return CaptionLaneChange(change.trackId, position, change.added.map { it.id }, removed)
    }

    /** Returns these assets with the text asset kept exactly while some clip in [tracks] shows text. */
    private fun Project.assetsFor(tracks: List<Track>): List<MediaAsset> {
        val showsText = tracks.any { track -> track.clips.any { it.text != null } }
        val known = assets.any { it.id == TEXT_ASSET_ID }
        return when {
            showsText && !known -> assets + TEXT_ASSET
            !showsText && known -> assets.filter { it.id != TEXT_ASSET_ID }
            else -> assets
        }
    }
}

/** Returns these tracks with [change] applied. Throws [IllegalArgumentException] when it does not fit. */
private fun List<Track>.applying(change: CaptionLaneChange): List<Track> {
    require(change.added.all { it.text?.caption != null }) { "Only captions may be added as captions" }
    val existing = firstOrNull { it.id == change.trackId }
    require(existing == null || existing.kind == TrackKind.TEXT) { "Track ${change.trackId} is not a text lane" }
    val clips = existing?.clips.orEmpty()
    val removed = change.removedIds.toSet()
    require(clips.filter { it.id in removed }.count { it.text?.caption != null } == removed.size) {
        "Lane ${change.trackId} does not hold captions $removed"
    }
    val kept = (clips.filter { it.id !in removed } + change.added).sortedBy { it.startMicros }
    return when {
        existing == null && kept.isEmpty() -> this
        existing == null -> toMutableList().apply { add(change.trackIndex.coerceIn(0, size), lane(change, kept)) }
        kept.isEmpty() -> filter { it.id != change.trackId }
        else -> map { if (it.id == change.trackId) it.copy(clips = kept) else it }
    }
}

/** Returns a new text lane for [change] holding [clips]. */
private fun lane(
    change: CaptionLaneChange,
    clips: List<Clip>,
): Track = Track(change.trackId, TrackKind.TEXT, false, clips)

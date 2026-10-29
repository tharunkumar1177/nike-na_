/*
 * Command that places a sound clip on an audio track, creating the track if needed.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places [clip], which reads [asset], on audio track [trackId]. The asset is added if the project lacks it. A missing
 * track is created at position [trackIndex] of the track list, clamped to the list, which defaults to the end so a
 * new lane is drawn below the base video. An overlap is rejected. Each of [anchorRestorations] then re-attaches a
 * clip that lost its anchor when this clip was removed.
 */
@Serializable
@SerialName("addAudioClip")
data class AddAudioClip(
    val trackId: String,
    val clip: Clip,
    val asset: MediaAsset,
    val anchorRestorations: List<AnchorRestoration> = emptyList(),
    val trackIndex: Int = Int.MAX_VALUE,
) : EditCommand {
    /** The inverse removes the audio clip again. */
    override fun applyTo(project: Project): CommandOutcome {
        val known = project.assets.firstOrNull { it.id == asset.id }
        return if (known != null && known != asset) {
            modelInvariant("Asset ${asset.id} already exists with different details")
        } else {
            val assets = if (known == null) project.assets + asset else project.assets
            project.replacingTracks(RemoveAudioClip(clip.id), assets) {
                val laid = project.tracks.withLaneClip(TrackKind.AUDIO, trackId, clip, trackIndex)
                laid.restoringAnchors(anchorRestorations)
            }
        }
    }
}

/**
 * Returns these tracks with [clip] added to non-magnetic track [trackId] of [kind], which is created at [trackIndex]
 * (clamped) when missing. Throws when [trackId] names a track of another kind; overlaps fail track validation.
 */
internal fun List<Track>.withLaneClip(
    kind: TrackKind,
    trackId: String,
    clip: Clip,
    trackIndex: Int,
): List<Track> {
    val existing = firstOrNull { it.id == trackId }
    return if (existing == null) {
        val lane = Track(trackId, kind, false, listOf(clip))
        toMutableList().apply { add(trackIndex.coerceIn(0, size), lane) }
    } else {
        require(existing.kind == kind) { "Track $trackId is not a $kind track" }
        val placed = existing.copy(clips = (existing.clips + clip).sortedBy { it.startMicros })
        map { if (it.id == trackId) placed else it }
    }
}

/*
 * Command that removes a picture-in-picture clip together with its now-unused asset and empty track.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Removes overlay clip [clipId]. Its asset goes too unless another clip reads it, and its track goes if it becomes
 * empty. Clips anchored to the removed clip lose their anchor and stay where they are.
 */
@Serializable
@SerialName("removeOverlayClip")
data class RemoveOverlayClip(
    val clipId: String,
) : EditCommand {
    /** The inverse adds the clip, its asset, and its track back and re-attaches its anchored clips. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (track.kind != TrackKind.OVERLAY) {
            modelInvariant("Clip $clipId is not an overlay clip")
        } else {
            val asset = project.assetOf(clip)
            val restorations = restorationsFor(project.tracks, clipId)
            val inverse = AddOverlayClip(track.id, clip, asset, restorations, project.tracks.indexOf(track))
            val shared = project.tracks.any { lane -> lane.clips.any { it.id != clipId && it.assetId == asset.id } }
            val assets = if (shared) project.assets else project.assets.filter { it.id != asset.id }
            val remaining = track.clips.filter { it.id != clipId }
            project.replacingTracks(inverse, assets) {
                val lanes =
                    project.tracks.mapNotNull { lane ->
                        when {
                            lane.id != track.id -> lane
                            remaining.isEmpty() -> null
                            else -> lane.copy(clips = remaining)
                        }
                    }
                lanes.orphaningChildrenOf(clipId)
            }
        }
    }
}

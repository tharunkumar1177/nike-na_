/*
 * Command that removes a text clip together with its empty lane and, for the last text clip, the text asset.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Removes text clip [clipId]. Its lane goes if it becomes empty, and the shared text asset goes with the last text
 * clip. Clips anchored to the removed clip lose their anchor and stay where they are.
 */
@Serializable
@SerialName("removeTextClip")
data class RemoveTextClip(
    val clipId: String,
) : EditCommand {
    /** The inverse adds the clip and its lane back and re-attaches its anchored clips. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (track.kind != TrackKind.TEXT) {
            modelInvariant("Clip $clipId is not a text clip")
        } else {
            val restorations = restorationsFor(project.tracks, clipId)
            val inverse = AddTextClip(track.id, clip, restorations, project.tracks.indexOf(track))
            val shared = project.tracks.any { lane -> lane.clips.any { it.id != clipId && it.text != null } }
            val assets = if (shared) project.assets else project.assets.filter { it.id != TEXT_ASSET_ID }
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

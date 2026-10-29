/*
 * Command that removes a clip, closing the gap on magnetic tracks.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Removes the clip [clipId]. On a magnetic track, later clips shift left to close the gap. Clips anchored to the
 * removed clip lose their anchor and stay where they are.
 */
@Serializable
@SerialName("deleteClip")
data class DeleteClip(
    val clipId: String,
) : EditCommand {
    /** The inverse re-adds the clip at its original position and re-attaches its anchored clips. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return project.replacingTracks(AddClip(track.id, clip, restorationsFor(project.tracks, clipId))) {
            val remaining =
                track.clips
                    .filter { it.id != clipId }
                    .map { other ->
                        if (track.magnetic && other.startMicros > clip.startMicros) {
                            other.copy(startMicros = other.startMicros - clip.durationMicros)
                        } else {
                            other
                        }
                    }
            project.tracks
                .map { if (it.id == track.id) track.copy(clips = remaining) else it }
                .orphaningChildrenOf(clipId)
        }
    }
}

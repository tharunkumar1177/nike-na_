/*
 * Command that removes a clip, closing the gap on magnetic tracks.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Removes the clip [clipId]. On a magnetic track, later clips shift left to close the gap. */
@Serializable
@SerialName("deleteClip")
data class DeleteClip(
    val clipId: String,
) : EditCommand {
    /** The inverse re-adds the clip at its original position. */
    override fun applyTo(project: Project): CommandOutcome {
        val track =
            project.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } }
                ?: return CommandOutcome.Rejected(CommandRejection.ClipNotFound(clipId))
        val clip = track.clips.first { it.id == clipId }
        return project.replacingTrack(AddClip(track.id, clip)) {
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
            track.copy(clips = remaining)
        }
    }
}

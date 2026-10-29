/*
 * Command that places a clip on a track, pushing later clips right on magnetic tracks.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places [clip] on track [trackId]. On a magnetic track, clips starting at or after the clip shift
 * right by its duration; on other tracks an overlap is rejected. Each of [anchorRestorations] then re-attaches a
 * clip that lost its anchor when this clip was removed.
 */
@Serializable
@SerialName("addClip")
data class AddClip(
    val trackId: String,
    val clip: Clip,
    val anchorRestorations: List<AnchorRestoration> = emptyList(),
) : EditCommand {
    /** The inverse deletes the clip again. */
    override fun applyTo(project: Project): CommandOutcome {
        val track =
            project.tracks.firstOrNull { it.id == trackId }
                ?: return CommandOutcome.Rejected(CommandRejection.TrackNotFound(trackId))
        return project.replacingTracks(DeleteClip(clip.id)) {
            val shifted =
                if (track.magnetic) {
                    track.clips.map { other ->
                        if (other.startMicros >= clip.startMicros) {
                            other.copy(startMicros = other.startMicros + clip.durationMicros)
                        } else {
                            other
                        }
                    }
                } else {
                    track.clips
                }
            val added = track.copy(clips = (shifted + clip).sortedBy { it.startMicros })
            project.tracks
                .map { if (it.id == trackId) added else it }
                .restoringAnchors(anchorRestorations)
        }
    }
}

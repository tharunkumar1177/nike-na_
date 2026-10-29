/*
 * Command that moves a clip to a new place on its track.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Moves clip [clipId] to start at [newStartMicros]. On a magnetic track the clip is first taken out (later clips
 * close the gap) and [newStartMicros] is a clip boundary of that shortened track; clips from there on shift right to
 * make room. On other tracks the clip simply moves and an overlap is rejected.
 */
@Serializable
@SerialName("moveClip")
data class MoveClip(
    val clipId: String,
    val newStartMicros: Long,
) : EditCommand {
    /** The inverse moves the clip back to where it started. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return project.replacingTrack(MoveClip(clipId, clip.startMicros)) {
            val others = track.clips.filter { it.id != clipId }
            val placed =
                if (track.magnetic) {
                    others
                        .shiftedFrom(clip.endMicros, -clip.durationMicros)
                        .shiftedFrom(newStartMicros, clip.durationMicros)
                } else {
                    others
                }
            track.copy(clips = (placed + clip.copy(startMicros = newStartMicros)).sortedBy { it.startMicros })
        }
    }
}

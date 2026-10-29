/*
 * Command that changes where a clip starts, which part of its source it reads, and how long it lasts.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets clip [clipId] to start at [startMicros], read its source from [trimStartMicros], and last [durationMicros].
 * On a magnetic track the start must not move and later clips shift by the change in duration; on other tracks
 * an overlap is rejected. Clips shorter than one frame and image clips with a changed source offset are rejected.
 */
@Serializable
@SerialName("trimClip")
data class TrimClip(
    val clipId: String,
    val startMicros: Long,
    val trimStartMicros: Long,
    val durationMicros: Long,
) : EditCommand {
    /** The inverse restores the clip's previous start, source offset, and duration. */
    override fun applyTo(project: Project): CommandOutcome {
        val location = project.locate(clipId) ?: return clipNotFound(clipId)
        val minimum = project.frameRate.startOfFrame(1)
        return if (durationMicros < minimum) {
            CommandOutcome.Rejected(CommandRejection.ClipTooShort(minimum))
        } else {
            trim(project, location)
        }
    }

    /** Replaces the clip at [location] in [project] with its trimmed form. */
    private fun trim(
        project: Project,
        location: ClipLocation,
    ): CommandOutcome {
        val (track, clip) = location
        val inverse = TrimClip(clipId, clip.startMicros, clip.trimStartMicros, clip.durationMicros)
        return project.replacingTrack(inverse) {
            val isImage = project.assetOf(clip).kind == MediaKind.IMAGE
            require(!isImage || trimStartMicros == clip.trimStartMicros) {
                "Image clip $clipId has no source offset to trim"
            }
            val trimmed =
                clip.copy(
                    startMicros = startMicros,
                    trimStartMicros = trimStartMicros,
                    durationMicros = durationMicros,
                )
            val others = track.clips.filter { it.id != clipId }
            val delta = trimmed.endMicros - clip.endMicros
            val placed = if (track.magnetic) others.shiftedFrom(clip.endMicros, delta) else others
            track.copy(clips = (placed + trimmed).sortedBy { it.startMicros })
        }
    }
}

/*
 * Command that sets or clears the shape mask of one picture clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.takesLook
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Gives clip [clipId] [mask], or clears it when null. Setting one is rejected off a video or overlay track. */
@Serializable
@SerialName("setClipMask")
data class SetClipMask(
    val clipId: String,
    val mask: ClipMask?,
) : EditCommand {
    /** The inverse restores the clip's previous mask exactly. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (mask != null && !track.kind.takesLook) {
            CommandOutcome.Rejected(CommandRejection.MaskNotSupported(clipId))
        } else {
            project.replacingTrack(SetClipMask(clipId, clip.mask)) {
                track.copy(clips = track.clips.map { if (it.id == clipId) it.copy(mask = mask) else it })
            }
        }
    }
}

/*
 * Command that changes where an overlay clip sits on the canvas.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets the canvas placement of overlay clip [clipId] to [transform]; clips off overlay tracks are rejected. */
@Serializable
@SerialName("setOverlayTransform")
data class SetOverlayTransform(
    val clipId: String,
    val transform: OverlayTransform?,
) : EditCommand {
    /** The inverse restores the previous placement. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (track.kind != TrackKind.OVERLAY) {
            modelInvariant("Clip $clipId is not an overlay clip")
        } else {
            project.replacingTrack(SetOverlayTransform(clipId, clip.overlayTransform)) {
                val placed = track.clips.map { if (it.id == clipId) it.copy(overlayTransform = transform) else it }
                track.copy(clips = placed)
            }
        }
    }
}

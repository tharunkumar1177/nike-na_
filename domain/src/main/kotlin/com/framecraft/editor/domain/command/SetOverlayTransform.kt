/*
 * Command that changes where an overlay clip sits on the canvas.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets the canvas placement of overlay or text clip [clipId] to [transform]; clips on other tracks are rejected. */
@Serializable
@SerialName("setOverlayTransform")
data class SetOverlayTransform(
    val clipId: String,
    val transform: OverlayTransform?,
) : EditCommand {
    /** The inverse restores the previous placement. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (!track.kind.isPlaced) {
            modelInvariant("Clip $clipId is not placed on the canvas")
        } else {
            project.replacingTrack(SetOverlayTransform(clipId, clip.overlayTransform)) {
                val placed = track.clips.map { if (it.id == clipId) it.copy(overlayTransform = transform) else it }
                track.copy(clips = placed)
            }
        }
    }
}

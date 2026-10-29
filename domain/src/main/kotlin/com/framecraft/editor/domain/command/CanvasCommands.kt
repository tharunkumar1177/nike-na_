/*
 * Commands that change the project canvas and where base clips sit on it.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.CanvasAspect
import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets the project canvas shape to [aspect]; placements are normalized to the canvas, so no clip changes. */
@Serializable
@SerialName("setCanvasAspect")
data class SetCanvasAspect(
    val aspect: CanvasAspect,
) : EditCommand {
    /** The inverse restores the previous shape. */
    override fun applyTo(project: Project): CommandOutcome =
        CommandOutcome.Applied(
            project.copy(canvas = project.canvas.copy(aspect = aspect)),
            SetCanvasAspect(project.canvas.aspect),
        )
}

/** Sets what fills the canvas behind the base clips to [background]. */
@Serializable
@SerialName("setCanvasBackground")
data class SetCanvasBackground(
    val background: CanvasBackground,
) : EditCommand {
    /** The inverse restores the previous background. */
    override fun applyTo(project: Project): CommandOutcome =
        CommandOutcome.Applied(
            project.copy(canvas = project.canvas.copy(background = background)),
            SetCanvasBackground(project.canvas.background),
        )
}

/**
 * Sets the canvas placement of base clip [clipId] to [placement], where null fits it centered and upright; clips on
 * other tracks are rejected.
 */
@Serializable
@SerialName("setBaseClipPlacement")
data class SetBaseClipPlacement(
    val clipId: String,
    val placement: CanvasPlacement?,
) : EditCommand {
    /** The inverse restores the previous placement. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (track.kind != TrackKind.VIDEO) {
            modelInvariant("Clip $clipId is not on the base track")
        } else {
            project.replacingTrack(SetBaseClipPlacement(clipId, clip.canvasPlacement)) {
                val placed = track.clips.map { if (it.id == clipId) it.copy(canvasPlacement = placement) else it }
                track.copy(clips = placed)
            }
        }
    }
}

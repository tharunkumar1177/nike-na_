/*
 * Command that stores given transitions on clips exactly as given; it undoes every transition edit.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Clip [clipId] carries [transition] into the clip after it, or none when null. */
@Serializable
data class TransitionValue(
    val clipId: String,
    val transition: ClipTransition?,
)

/**
 * Gives each clip in [values] its transition as given, without checking its cut, so undo always restores what was
 * there. A missing clip rejects the command.
 */
@Serializable
@SerialName("setTransitions")
data class SetTransitions(
    val values: List<TransitionValue>,
) : EditCommand {
    /** The inverse gives the same clips their previous transitions. */
    override fun applyTo(project: Project): CommandOutcome {
        val missing = values.firstOrNull { project.locate(it.clipId) == null }
        return if (missing != null) {
            clipNotFound(missing.clipId)
        } else {
            val previous =
                values.map { value ->
                    TransitionValue(value.clipId, requireNotNull(project.locate(value.clipId)).clip.outTransition)
                }
            val byClip = values.associate { it.clipId to it.transition }
            project.replacingTracks(SetTransitions(previous)) {
                project.tracks.mapClips { clip ->
                    if (byClip.containsKey(clip.id)) clip.copy(outTransition = byClip[clip.id]) else clip
                }
            }
        }
    }
}

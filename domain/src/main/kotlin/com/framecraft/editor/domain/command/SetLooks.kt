/*
 * Command that stores given looks on clips exactly as given; it undoes every look edit.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Clip [clipId] carries [look], or none when null. */
@Serializable
data class LookValue(
    val clipId: String,
    val look: ClipLook?,
)

/** Gives each clip in [values] its look as given, so undo always restores what was there. A missing clip rejects. */
@Serializable
@SerialName("setLooks")
data class SetLooks(
    val values: List<LookValue>,
) : EditCommand {
    /** The inverse gives the same clips their previous looks. */
    override fun applyTo(project: Project): CommandOutcome {
        val missing = values.firstOrNull { project.locate(it.clipId) == null }
        return if (missing != null) {
            clipNotFound(missing.clipId)
        } else {
            val previous = values.map { LookValue(it.clipId, requireNotNull(project.locate(it.clipId)).clip.look) }
            val byClip = values.associate { it.clipId to it.look }
            project.replacingTracks(SetLooks(previous)) {
                project.tracks.mapClips { clip ->
                    if (byClip.containsKey(clip.id)) clip.copy(look = byClip[clip.id]) else clip
                }
            }
        }
    }
}

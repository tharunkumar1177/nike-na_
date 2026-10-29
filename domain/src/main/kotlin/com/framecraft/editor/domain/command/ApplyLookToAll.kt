/*
 * Command that gives one look to every picture clip in the project, as a single undo step.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.takesLook
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gives every clip on a video or overlay track [look], or clears every look when null. It is rejected for a filter
 * the catalog does not know and when the project has no picture clip.
 */
@Serializable
@SerialName("applyLookToAll")
data class ApplyLookToAll(
    val look: ClipLook?,
) : EditCommand {
    /** The inverse restores every clip's previous look. */
    override fun applyTo(project: Project): CommandOutcome {
        val values = valuesFor(project, look)
        val rejection =
            when {
                values.isEmpty() -> CommandRejection.NoLookTargets
                look == null -> null
                else -> unknownFilterOf(look)
            }
        return rejection?.let { CommandOutcome.Rejected(it) } ?: SetLooks(values).applyTo(project)
    }

    /** Which clips apply to all reaches, shared with the editor so it can say how many changed. */
    companion object {
        /** Returns the look each picture clip of [project] takes from [look]; a look that changes nothing is none. */
        fun valuesFor(
            project: Project,
            look: ClipLook?,
        ): List<LookValue> {
            val stored = look?.takeUnless { it.isNeutral }
            return project.tracks
                .filter { it.kind.takesLook }
                .flatMap { track -> track.clips.map { LookValue(it.id, stored) } }
        }
    }
}

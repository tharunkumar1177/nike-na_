/*
 * Command that renames the project.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Renames the project to [name] with surrounding whitespace removed. */
@Serializable
@SerialName("renameProject")
data class RenameProject(
    val name: String,
) : EditCommand {
    /** Rejects blank names; the inverse restores the previous name. */
    override fun applyTo(project: Project): CommandOutcome {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) {
            CommandOutcome.Rejected(CommandRejection.BlankName)
        } else {
            CommandOutcome.Applied(project.copy(name = trimmed), RenameProject(project.name))
        }
    }
}

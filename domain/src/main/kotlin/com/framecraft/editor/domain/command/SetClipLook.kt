/*
 * Command that sets or clears the look of one picture clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.LookFilterCatalog
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.takesLook
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gives clip [clipId] [look], storing a look that changes nothing as none, or clears it when null. Setting one is
 * rejected off a video or overlay track and for a filter the catalog does not know.
 */
@Serializable
@SerialName("setClipLook")
data class SetClipLook(
    val clipId: String,
    val look: ClipLook?,
) : EditCommand {
    /** The inverse restores the clip's previous look exactly. */
    override fun applyTo(project: Project): CommandOutcome {
        val location = project.locate(clipId) ?: return clipNotFound(clipId)
        val rejection =
            when {
                look == null -> null
                !location.track.kind.takesLook -> CommandRejection.LookNotSupported(clipId)
                else -> unknownFilterOf(look)
            }
        return if (rejection != null) {
            CommandOutcome.Rejected(rejection)
        } else {
            SetLooks(listOf(LookValue(clipId, look?.takeUnless { it.isNeutral }))).applyTo(project)
        }
    }
}

/** Returns the rejection for [look]'s filter when the catalog does not know it, or null. */
internal fun unknownFilterOf(look: ClipLook): CommandRejection? =
    look.filterId
        ?.takeIf { LookFilterCatalog.find(it) == null }
        ?.let { CommandRejection.UnknownLookFilter(it) }

/*
 * Command that sets or clears the chroma key of one picture clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.takesLook
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gives clip [clipId] [chromaKey], storing a key that keys nothing as none, or clears it when null. Setting one is
 * rejected off a video or overlay track.
 */
@Serializable
@SerialName("setClipChromaKey")
data class SetClipChromaKey(
    val clipId: String,
    val chromaKey: ChromaKey?,
) : EditCommand {
    /** The inverse restores the clip's previous key exactly. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        val stored = chromaKey?.takeUnless { it.isNeutral }
        return if (stored != null && !track.kind.takesLook) {
            CommandOutcome.Rejected(CommandRejection.ChromaKeyNotSupported(clipId))
        } else {
            project.replacingTrack(SetClipChromaKey(clipId, clip.chromaKey)) {
                track.copy(clips = track.clips.map { if (it.id == clipId) it.copy(chromaKey = stored) else it })
            }
        }
    }
}

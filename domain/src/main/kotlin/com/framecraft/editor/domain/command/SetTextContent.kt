/*
 * Command that changes what a text clip says, how it looks, and how it enters and leaves.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TextContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets text clip [clipId] to show [content]. Animations longer than the clip are shortened to fit it. Clips that
 * are not text are rejected.
 */
@Serializable
@SerialName("setTextContent")
data class SetTextContent(
    val clipId: String,
    val content: TextContent,
) : EditCommand {
    /** The inverse restores the previous content exactly. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        val previous = clip.text
        return if (previous == null) {
            modelInvariant("Clip $clipId is not a text clip")
        } else {
            project.replacingTrack(SetTextContent(clipId, previous)) {
                val changed = clip.copy(text = content.fitted(clip.durationMicros))
                track.copy(clips = track.clips.map { if (it.id == clipId) changed else it })
            }
        }
    }
}

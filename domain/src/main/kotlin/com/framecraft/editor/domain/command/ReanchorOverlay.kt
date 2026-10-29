/*
 * Command that changes which clip an overlay follows without moving it.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attaches clip [clipId] to [parentClipId] at its current position, or detaches it when [parentClipId] is null.
 * A missing parent or an anchor cycle is rejected.
 */
@Serializable
@SerialName("reanchorOverlay")
data class ReanchorOverlay(
    val clipId: String,
    val parentClipId: String?,
) : EditCommand {
    /** The inverse attaches the clip back to its previous parent, or detaches it if it had none. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        val parent = parentClipId?.let { project.locate(it)?.clip }
        return if (parentClipId != null && parent == null) {
            clipNotFound(parentClipId)
        } else {
            project.replacingTrack(ReanchorOverlay(clipId, clip.anchor?.parentClipId)) {
                val anchor = parent?.let { ClipAnchor(it.id, clip.startMicros - it.startMicros) }
                track.copy(clips = track.clips.map { if (it.id == clipId) it.copy(anchor = anchor) else it })
            }
        }
    }
}

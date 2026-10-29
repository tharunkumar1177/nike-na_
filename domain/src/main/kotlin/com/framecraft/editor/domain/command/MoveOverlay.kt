/*
 * Command that moves an overlay clip in time, attaching it to a new parent or detaching it.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Moves clip [clipId]. With an [anchor], the clip attaches to the anchor's parent and starts where the anchor
 * resolves; [startMicros] records that start. Without one, the clip is detached and starts at [startMicros]. An
 * overlap, a missing parent, or an anchor cycle is rejected.
 */
@Serializable
@SerialName("moveOverlay")
data class MoveOverlay(
    val clipId: String,
    val anchor: ClipAnchor?,
    val startMicros: Long,
) : EditCommand {
    /** The inverse restores the clip's previous anchor and start. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        val parentStart = anchor?.let { project.locate(it.parentClipId)?.clip?.startMicros }
        return if (anchor != null && parentStart == null) {
            clipNotFound(anchor.parentClipId)
        } else {
            project.replacingTrack(MoveOverlay(clipId, clip.anchor, clip.startMicros)) {
                val start = anchor?.let { (parentStart ?: 0L) + it.offsetMicros } ?: startMicros
                val moved = clip.copy(startMicros = start, anchor = anchor)
                track.copy(clips = (track.clips.filter { it.id != clipId } + moved).sortedBy { it.startMicros })
            }
        }
    }
}

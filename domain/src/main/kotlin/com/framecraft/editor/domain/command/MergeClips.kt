/*
 * Command that joins two adjacent parts of the same source back into one clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Joins clip [rightClipId] onto clip [leftClipId], which keeps its id. The clips must be on the same track, touch,
 * use the same asset, read the source continuously, and share their anchor parent and overlay placement; otherwise
 * the command is rejected. Clips anchored to the right part move their anchor to the merged clip.
 */
@Serializable
@SerialName("mergeClips")
data class MergeClips(
    val leftClipId: String,
    val rightClipId: String,
) : EditCommand {
    /** The inverse splits the merged clip where the right part began. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, left) = project.locate(leftClipId) ?: return clipNotFound(leftClipId)
        val right = track.clips.firstOrNull { it.id == rightClipId }
        return if (right == null) {
            clipNotFound(rightClipId)
        } else {
            project.replacingTracks(SplitClip(leftClipId, left.endMicros, rightClipId)) {
                requireJoinable(left, right, project.assetOf(left).kind == MediaKind.IMAGE)
                val merged = left.copy(durationMicros = left.durationMicros + right.durationMicros)
                val joined =
                    track.copy(
                        clips =
                            track.clips
                                .filter { it.id != rightClipId }
                                .map { if (it.id == leftClipId) merged else it },
                    )
                val shift = right.startMicros - left.startMicros
                project.tracks
                    .map { if (it.id == track.id) joined else it }
                    .mapClips { child -> retargeted(child, shift) }
            }
        }
    }

    /** Returns [child] attached to the merged clip, [shiftMicros] later, if it was anchored to the right part. */
    private fun retargeted(
        child: Clip,
        shiftMicros: Long,
    ): Clip {
        val anchor = child.anchor
        return if (anchor != null && anchor.parentClipId == rightClipId) {
            child.copy(anchor = ClipAnchor(leftClipId, anchor.offsetMicros + shiftMicros))
        } else {
            child
        }
    }

    /** Throws unless [right] continues [left] in time, source, anchor, and placement; [isImage] has no offset. */
    private fun requireJoinable(
        left: Clip,
        right: Clip,
        isImage: Boolean,
    ) {
        require(left.assetId == right.assetId) { "Clips $leftClipId and $rightClipId use different assets" }
        require(left.endMicros == right.startMicros) { "Clips $leftClipId and $rightClipId do not touch" }
        val expectedTrimStart = if (isImage) left.trimStartMicros else left.trimStartMicros + left.durationMicros
        require(right.trimStartMicros == expectedTrimStart) {
            "Clips $leftClipId and $rightClipId do not read the source continuously"
        }
        require(right.anchor == shiftedAnchor(left.anchor, left.durationMicros)) {
            "Clips $leftClipId and $rightClipId are anchored differently"
        }
        require(right.overlayTransform == left.overlayTransform) {
            "Clips $leftClipId and $rightClipId are placed differently"
        }
    }
}

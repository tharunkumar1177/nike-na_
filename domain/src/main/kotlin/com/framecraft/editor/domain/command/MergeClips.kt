/*
 * Command that joins two adjacent parts of the same source back into one clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Joins clip [rightClipId] onto clip [leftClipId], which keeps its id. The clips must be on the same track, touch,
 * use the same asset, and read the source continuously; otherwise the command is rejected.
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
            project.replacingTrack(SplitClip(leftClipId, left.endMicros, rightClipId)) {
                requireJoinable(left, right, project.assetOf(left).kind == MediaKind.IMAGE)
                val merged = left.copy(durationMicros = left.durationMicros + right.durationMicros)
                track.copy(
                    clips =
                        track.clips
                            .filter { it.id != rightClipId }
                            .map { if (it.id == leftClipId) merged else it },
                )
            }
        }
    }

    /** Throws unless [right] continues [left] in time and in source; [isImage] clips have no source offset. */
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
    }
}

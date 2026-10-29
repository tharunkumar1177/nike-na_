/*
 * Command that cuts a clip in two at a timeline position.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuts clip [clipId] at [atMicros]. The left part keeps [clipId]; the right part becomes [rightClipId], which the
 * caller generates so replaying the command creates the same id. Both parts must last at least one frame.
 */
@Serializable
@SerialName("splitClip")
data class SplitClip(
    val clipId: String,
    val atMicros: Long,
    val rightClipId: String,
) : EditCommand {
    /** The inverse merges the two parts back into one clip. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        val minimum = project.frameRate.startOfFrame(1)
        return if (!isSplittable(clip, atMicros, project.frameRate)) {
            CommandOutcome.Rejected(CommandRejection.ClipTooShort(minimum))
        } else {
            project.replacingTrack(MergeClips(clipId, rightClipId)) {
                val left = clip.copy(durationMicros = atMicros - clip.startMicros)
                val right = rightPart(clip, left.durationMicros, project.assetOf(clip).kind == MediaKind.IMAGE)
                track.copy(clips = track.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) })
            }
        }
    }

    /** Returns the part of [clip] after the cut, [leftDurationMicros] in; [isImage] clips keep their offset. */
    private fun rightPart(
        clip: Clip,
        leftDurationMicros: Long,
        isImage: Boolean,
    ): Clip =
        clip.copy(
            id = rightClipId,
            startMicros = atMicros,
            trimStartMicros = if (isImage) clip.trimStartMicros else clip.trimStartMicros + leftDurationMicros,
            durationMicros = clip.endMicros - atMicros,
        )

    /** Split rules shared with the editor's Split tool. */
    companion object {
        /** True when cutting [clip] at [atMicros] leaves at least one frame of [frameRate] on each side. */
        fun isSplittable(
            clip: Clip,
            atMicros: Long,
            frameRate: FrameRate,
        ): Boolean {
            val minimum = frameRate.startOfFrame(1)
            return atMicros - clip.startMicros >= minimum && clip.endMicros - atMicros >= minimum
        }
    }
}

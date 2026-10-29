/*
 * Command that cuts a clip in two at a timeline position.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuts clip [clipId] at [atMicros]. The left part keeps [clipId]; the right part becomes [rightClipId], which the
 * caller generates so replaying the command creates the same id. Both parts must last at least one frame. Clips
 * anchored to the cut clip that start at or after the cut move their anchor to the right part. The left part keeps
 * the fade-in and the right part keeps the fade-out, each shortened to fit its part; a text clip's in and out
 * animations split the same way.
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
            val inverse =
                MergeClips(
                    clipId,
                    rightClipId,
                    clip.fadeInMicros,
                    clip.fadeOutMicros,
                    clip.text?.animationIn,
                    clip.text?.animationOut,
                )
            project.replacingTracks(inverse) {
                val leftDuration = atMicros - clip.startMicros
                val left =
                    clip.copy(
                        durationMicros = leftDuration,
                        fadeInMicros = minOf(clip.fadeInMicros, leftDuration),
                        fadeOutMicros = 0,
                        text = clip.text?.copy(animationOut = null)?.fitted(leftDuration),
                    )
                val right = rightPart(clip, left.durationMicros, project.assetOf(clip).isTimed)
                val parts = track.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
                val cut = track.copy(clips = parts)
                project.tracks
                    .map { if (it.id == track.id) cut else it }
                    .mapClips(::retargeted)
            }
        }
    }

    /** Returns [child] attached to the right part if it is anchored to the cut clip and starts at or after the cut. */
    private fun retargeted(child: Clip): Clip {
        val anchor = child.anchor
        return if (anchor?.parentClipId == clipId && child.startMicros >= atMicros) {
            child.copy(anchor = ClipAnchor(rightClipId, child.startMicros - atMicros))
        } else {
            child
        }
    }

    /** Returns the part of [clip] after the cut, [leftDurationMicros] in; only [isTimed] clips move their offset. */
    private fun rightPart(
        clip: Clip,
        leftDurationMicros: Long,
        isTimed: Boolean,
    ): Clip =
        clip.copy(
            id = rightClipId,
            startMicros = atMicros,
            trimStartMicros = if (isTimed) clip.trimStartMicros + leftDurationMicros else clip.trimStartMicros,
            durationMicros = clip.endMicros - atMicros,
            anchor = shiftedAnchor(clip.anchor, leftDurationMicros),
            fadeInMicros = 0,
            fadeOutMicros = minOf(clip.fadeOutMicros, clip.endMicros - atMicros),
            text = clip.text?.copy(animationIn = null)?.fitted(clip.endMicros - atMicros),
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

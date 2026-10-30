/*
 * Command that cuts a clip in two at a timeline position.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.model.slice
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuts clip [clipId] at [atMicros]. The left part keeps [clipId]; the right part becomes [rightClipId], which the
 * caller generates so replaying the command creates the same id. Both parts must last at least one frame. Clips
 * anchored to the cut clip that start at or after the cut move their anchor to the right part. The left part keeps
 * the fade-in and the right part keeps the fade-out, each shortened to fit its part; a text clip's in and out
 * animations split the same way. A sped clip is cut where its speed plays the cut, or after [leftSourceMicros] of
 * source when given, and each part keeps the speeds of its own source. The right part keeps the transition after the
 * clip; the new cut has none.
 */
@Serializable
@SerialName("splitClip")
data class SplitClip(
    val clipId: String,
    val atMicros: Long,
    val rightClipId: String,
    val leftSourceMicros: Long? = null,
) : EditCommand {
    /** The inverse merges the two parts back into one clip. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        val minimum = project.frameRate.startOfFrame(1)
        val lock = captionLock(clip)
        return if (lock != null) {
            lock
        } else if (!isSplittable(clip, atMicros, project.frameRate)) {
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
                    clip.speed,
                )
            project.replacingTracks(inverse) {
                val leftDuration = atMicros - clip.startMicros
                val leftSource = sourceCut(clip, leftDuration, project.frameRate)
                val left =
                    clip.copy(
                        durationMicros = leftDuration,
                        fadeInMicros = minOf(clip.fadeInMicros, leftDuration),
                        fadeOutMicros = 0,
                        text = clip.text?.copy(animationOut = null)?.fitted(leftDuration),
                        speed = clip.speed?.slice(clip.sourceSpanMicros, 0L, leftSource),
                        sourceDurationMicros = leftSource.takeIf { clip.speed != null },
                        outTransition = null,
                    )
                val right = rightPart(clip, leftDuration, leftSource, project.assetOf(clip).isTimed)
                val parts = track.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
                val cut = track.copy(clips = parts)
                val rightSourceStart = clip.trimStartMicros + leftSource
                project.tracks
                    .map { if (it.id == track.id) cut else it }
                    .mapClips { retargeted(it, clip.trimStartMicros, rightSourceStart) }
            }
        }
    }

    /**
     * Returns how much source the left part of [clip] reads when it lasts [leftDurationMicros] at [frameRate]: the
     * duration itself at normal speed, otherwise [leftSourceMicros] or the source the speed plays by then.
     */
    private fun sourceCut(
        clip: Clip,
        leftDurationMicros: Long,
        frameRate: FrameRate,
    ): Long {
        val speed = clip.speed ?: return leftDurationMicros
        val span = clip.sourceSpanMicros
        val cut = leftSourceMicros ?: SpeedMap.of(speed, span, frameRate).sourceAt(leftDurationMicros)
        return cut.coerceIn(1L, span - 1L)
    }

    /**
     * Returns [child] attached to the right part if it is anchored to the cut clip and starts at or after the cut; a
     * caption moves when its first word read after [trimStart] starts at or after [rightSourceStart] of the source.
     */
    private fun retargeted(
        child: Clip,
        trimStart: Long,
        rightSourceStart: Long,
    ): Clip {
        val anchor = child.anchor
        val caption = child.text?.caption
        val firstWord = caption?.words?.firstOrNull { it.sourceStartMicros >= trimStart }
        val afterCut = firstWord?.let { it.sourceStartMicros >= rightSourceStart } ?: (child.startMicros >= atMicros)
        return if (anchor?.parentClipId == clipId && afterCut) {
            child.copy(anchor = ClipAnchor(rightClipId, child.startMicros - atMicros))
        } else {
            child
        }
    }

    /**
     * Returns the part of [clip] after the cut, [leftDurationMicros] and [leftSourceMicros] of source in; only
     * [isTimed] clips move their offset.
     */
    private fun rightPart(
        clip: Clip,
        leftDurationMicros: Long,
        leftSourceMicros: Long,
        isTimed: Boolean,
    ): Clip =
        clip.copy(
            id = rightClipId,
            startMicros = atMicros,
            trimStartMicros = if (isTimed) clip.trimStartMicros + leftSourceMicros else clip.trimStartMicros,
            durationMicros = clip.endMicros - atMicros,
            anchor = shiftedAnchor(clip.anchor, leftDurationMicros),
            fadeInMicros = 0,
            fadeOutMicros = minOf(clip.fadeOutMicros, clip.endMicros - atMicros),
            text = clip.text?.copy(animationIn = null)?.fitted(clip.endMicros - atMicros),
            speed = clip.speed?.slice(clip.sourceSpanMicros, leftSourceMicros, clip.sourceSpanMicros),
            sourceDurationMicros = (clip.sourceSpanMicros - leftSourceMicros).takeIf { clip.speed != null },
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

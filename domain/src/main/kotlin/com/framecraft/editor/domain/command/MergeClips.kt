/*
 * Command that joins two adjacent parts of the same source back into one clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TextAnimation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Joins clip [rightClipId] onto clip [leftClipId], which keeps its id. The clips must be on the same track, touch,
 * use the same asset, read the source continuously, share their anchor parent, overlay placement, and volume, and
 * have no fade at the join; otherwise the command is rejected. The merged clip takes the left fade-in and the right
 * fade-out unless [fadeInMicros] or [fadeOutMicros] override them. Text parts must show the same text and style with no
 * animation at the join; the merged clip enters like the left part and leaves like the right one unless [animationIn]
 * or [animationOut] override them. Clips anchored to the right part move their anchor to the merged clip. Parts must
 * play at the same constant speed and pitch setting unless [speed] gives the merged clip's speed, as when undoing a
 * split of a curve. A transition at the join rejects the merge; the merged clip keeps the right part's transition.
 */
@Serializable
@SerialName("mergeClips")
data class MergeClips(
    val leftClipId: String,
    val rightClipId: String,
    val fadeInMicros: Long? = null,
    val fadeOutMicros: Long? = null,
    val animationIn: TextAnimation? = null,
    val animationOut: TextAnimation? = null,
    val speed: ClipSpeed? = null,
) : EditCommand {
    /** The inverse splits the merged clip where the right part began, after the same source. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, left) = project.locate(leftClipId) ?: return clipNotFound(leftClipId)
        val right = track.clips.firstOrNull { it.id == rightClipId }
        return if (right == null) {
            clipNotFound(rightClipId)
        } else {
            project.replacingTracks(SplitClip(leftClipId, left.endMicros, rightClipId, left.sourceDurationMicros)) {
                requireJoinable(left, right, project.assetOf(left).isTimed)
                val mergedSpeed = speed ?: left.speed
                val merged =
                    left.copy(
                        durationMicros = left.durationMicros + right.durationMicros,
                        speed = mergedSpeed,
                        sourceDurationMicros =
                            (left.sourceSpanMicros + right.sourceSpanMicros).takeIf { mergedSpeed != null },
                        fadeInMicros = fadeInMicros ?: left.fadeInMicros,
                        fadeOutMicros = fadeOutMicros ?: right.fadeOutMicros,
                        outTransition = right.outTransition,
                        text =
                            left.text?.copy(
                                animationIn = animationIn ?: left.text.animationIn,
                                animationOut = animationOut ?: right.text?.animationOut,
                            ),
                    )
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

    /**
     * Throws unless [right] continues [left] in time, source, anchor, placement, sound, text, and look. Only [isTimed]
     * clips advance their source offset.
     */
    private fun requireJoinable(
        left: Clip,
        right: Clip,
        isTimed: Boolean,
    ) {
        require(left.assetId == right.assetId) { "Clips $leftClipId and $rightClipId use different assets" }
        require(left.endMicros == right.startMicros) { "Clips $leftClipId and $rightClipId do not touch" }
        val expectedTrimStart = if (isTimed) left.trimStartMicros + left.sourceSpanMicros else left.trimStartMicros
        require(right.trimStartMicros == expectedTrimStart) {
            "Clips $leftClipId and $rightClipId do not read the source continuously"
        }
        require(right.anchor == shiftedAnchor(left.anchor, left.durationMicros)) {
            "Clips $leftClipId and $rightClipId are anchored differently"
        }
        require(right.overlayTransform == left.overlayTransform && right.canvasPlacement == left.canvasPlacement) {
            "Clips $leftClipId and $rightClipId are placed differently"
        }
        require(right.volumePercent == left.volumePercent && left.fadeOutMicros == 0L && right.fadeInMicros == 0L) {
            "Clips $leftClipId and $rightClipId differ in volume or fade at the join"
        }
        require(left.text?.still() == right.text?.still() && left.text?.animationOut == null) {
            "Clips $leftClipId and $rightClipId differ in text or animate at the join"
        }
        require(right.text?.animationIn == null) { "Clip $rightClipId animates in at the join" }
        require(left.outTransition == null) { "Clips $leftClipId and $rightClipId have a transition at the join" }
        require(left.look == right.look) { "Clips $leftClipId and $rightClipId have different looks" }
        val sameSpeed = left.speed == right.speed && left.speed !is ClipSpeed.Curve
        require((speed != null || sameSpeed) && left.maintainPitch == right.maintainPitch) {
            "Clips $leftClipId and $rightClipId play at different speeds"
        }
    }
}

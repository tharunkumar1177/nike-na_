/*
 * Where transitions play: which cuts can take one, how long it may last, and the window it covers on the timeline.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate

/**
 * A transition that plays: [left] blends into [right] by [kind] over [durationMicros], centered on the cut. The
 * window starts [tailMicros] before the cut and ends [headMicros] after it.
 */
data class TransitionCut(
    val left: Clip,
    val right: Clip,
    val kind: TransitionKind,
    val durationMicros: Long,
) {
    /** Timeline position of the cut. */
    val cutMicros: Long
        get() = left.endMicros

    /** Part of the window before the cut, where [left] would play alone. */
    val tailMicros: Long
        get() = durationMicros / 2

    /** Part of the window after the cut, where [right] would play alone. */
    val headMicros: Long
        get() = durationMicros - tailMicros

    /** Timeline start of the window. */
    val startMicros: Long
        get() = cutMicros - tailMicros

    /** Exclusive timeline end of the window. */
    val endMicros: Long
        get() = cutMicros + headMicros
}

/**
 * Rules for transitions at cuts. A cut is where a video-track clip ends exactly where the next one starts. Its
 * transition lasts at most half the shorter clip, so the windows on either end of a clip never meet, and at most
 * twice the hidden source each clip has past the cut, read at the speed it plays there. Stills have unlimited
 * hidden source; clips on a speed curve have none.
 */
object TransitionGeometry {
    /** Returns the shortest transition in a project at [frameRate]: 100 ms, and never under two frames. */
    fun minimumMicros(frameRate: FrameRate): Long = maxOf(MIN_TRANSITION_MICROS, frameRate.startOfFrame(2))

    /** Returns the clip that starts where [clip] ends on [track], or null when [clip] ends at no cut. */
    fun nextOf(
        track: Track,
        clip: Clip,
    ): Clip? {
        val index = track.clips.indexOfFirst { it.id == clip.id }
        return track.clips.getOrNull(index + 1)?.takeIf { index >= 0 && it.startMicros == clip.endMicros }
    }

    /** Returns the longest transition the cut from [left] into [right] in [project] allows, 0 when it allows none. */
    fun capMicros(
        project: Project,
        left: Clip,
        right: Clip,
    ): Long {
        val handle = minOf(outHandleMicros(project, left), inHandleMicros(project, right))
        val shorter = minOf(left.durationMicros, right.durationMicros) / 2
        return minOf(shorter, handle * 2, MAX_TRANSITION_MICROS)
    }

    /**
     * Returns [requestedMicros] held between the minimum for [frameRate] and [capMicros], or null when the cap is
     * under the minimum.
     */
    fun clampedMicros(
        requestedMicros: Long,
        capMicros: Long,
        frameRate: FrameRate,
    ): Long? {
        val minimum = minimumMicros(frameRate)
        return if (capMicros < minimum) null else requestedMicros.coerceIn(minimum, capMicros)
    }

    /**
     * Returns the transitions that play on [track] of [project], in timeline order, each held to what its cut allows.
     * Only video tracks play transitions.
     */
    fun cutsOf(
        project: Project,
        track: Track,
    ): List<TransitionCut> =
        if (track.kind != TrackKind.VIDEO) {
            emptyList()
        } else {
            track.clips.zipWithNext().mapNotNull { (left, right) -> cutOf(project, left, right) }
        }

    /** Returns the transition that plays from [left] into [right], or null when there is none or it cannot play. */
    private fun cutOf(
        project: Project,
        left: Clip,
        right: Clip,
    ): TransitionCut? =
        left.outTransition
            ?.takeIf { left.endMicros == right.startMicros }
            ?.let { transition ->
                clampedMicros(transition.durationMicros, capMicros(project, left, right), project.frameRate)
                    ?.let { TransitionCut(left, right, transition.kind, it) }
            }

    /** Returns the timeline length of hidden source after [clip] ends, capped at the longest transition. */
    private fun outHandleMicros(
        project: Project,
        clip: Clip,
    ): Long {
        val sourceDuration = project.assetOf(clip).durationMicros
        val hidden = sourceDuration?.let { it - clip.trimStartMicros - clip.sourceSpanMicros }
        return handleMicros(clip, hidden, project.frameRate)
    }

    /** Returns the timeline length of hidden source before [clip] starts, capped at the longest transition. */
    private fun inHandleMicros(
        project: Project,
        clip: Clip,
    ): Long {
        val timed = project.assetOf(clip).isTimed
        return handleMicros(clip, clip.trimStartMicros.takeIf { timed }, project.frameRate)
    }

    /**
     * Returns [hiddenSourceMicros] of [clip]'s source as timeline time at the clip's speed, capped at the longest
     * transition; null hidden source is a still, which is unlimited, and a speed curve has none.
     */
    private fun handleMicros(
        clip: Clip,
        hiddenSourceMicros: Long?,
        frameRate: FrameRate,
    ): Long =
        when {
            hiddenSourceMicros == null -> MAX_TRANSITION_MICROS
            clip.speed is ClipSpeed.Curve || hiddenSourceMicros <= 0 -> 0L
            else -> SpeedMap.durationFor(clip.speed, hiddenSourceMicros, frameRate).coerceAtMost(MAX_TRANSITION_MICROS)
        }
}

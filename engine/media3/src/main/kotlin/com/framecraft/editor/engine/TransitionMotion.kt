/*
 * How each side of a transition moves on the canvas as the transition plays.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.TransitionKind

/** How far the outgoing clip zooms in by the end of a zoom transition, as a share of its size. */
private const val ZOOM_GROWTH = 0.3f

/** Canvas width in normalized units, where -1 and 1 are the left and right edges. */
private const val CANVAS_WIDTH = 2f

/**
 * One side of a transition at a moment: its [alpha], its horizontal [shiftX] in normalized canvas units (2 is the
 * canvas width), and its [zoom] factor.
 */
internal data class Motion(
    val alpha: Float = 1f,
    val shiftX: Float = 0f,
    val zoom: Float = 1f,
)

/** The motion of both sides of every transition kind, by how far the transition has played. */
internal object TransitionMotion {
    /** Returns how far a transition from [startMicros] to [endMicros] has played at [timeMicros], from 0 to 1. */
    fun progress(
        startMicros: Long,
        endMicros: Long,
        timeMicros: Long,
    ): Float = ((timeMicros - startMicros).toFloat() / (endMicros - startMicros)).coerceIn(0f, 1f)

    /** Returns the outgoing clip's motion in a [kind] transition [progress] of the way through. */
    fun outgoing(
        kind: TransitionKind,
        progress: Float,
    ): Motion {
        val fading = (1f - 2f * progress).coerceAtLeast(0f)
        return when (kind) {
            TransitionKind.DISSOLVE, TransitionKind.SLIDE_LEFT -> Motion()
            TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE -> Motion(alpha = fading)
            TransitionKind.PUSH_LEFT -> Motion(shiftX = -CANVAS_WIDTH * progress)
            TransitionKind.ZOOM -> Motion(zoom = 1f + ZOOM_GROWTH * progress)
        }
    }

    /** Returns the incoming clip's motion in a [kind] transition [progress] of the way through. */
    fun incoming(
        kind: TransitionKind,
        progress: Float,
    ): Motion {
        val appearing = (2f * progress - 1f).coerceAtLeast(0f)
        return when (kind) {
            TransitionKind.DISSOLVE, TransitionKind.ZOOM -> Motion(alpha = progress)
            TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE -> Motion(alpha = appearing)
            TransitionKind.SLIDE_LEFT, TransitionKind.PUSH_LEFT -> Motion(shiftX = CANVAS_WIDTH * (1f - progress))
        }
    }
}

/*
 * Pure text animation curves: how visible, how large, how far shifted, and how much of a text clip shows at a time.
 */
package com.framecraft.editor.engine.text

import com.framecraft.editor.domain.model.TextAnimation
import com.framecraft.editor.domain.model.TextAnimationKind
import com.framecraft.editor.domain.model.TextContent
import kotlin.math.ceil

/** How far a sliding text travels, in half-canvas units (y downward). */
const val TEXT_SLIDE_DISTANCE: Float = 0.25f

/** Smallest scale a popping text takes, so the compositor never gets a degenerate size. */
const val MIN_TEXT_SCALE: Float = 0.01f

/** Overshoot of the pop curve past full size. */
private const val POP_OVERSHOOT = 1.70158f

/** The state of animated text at one moment: multiply [alpha] and [scale], add [offsetY], show [revealed] chars. */
data class TextFrame(
    val alpha: Float,
    val scale: Float,
    val offsetY: Float,
    val revealed: Int,
)

/** Evaluates text animations. The out animation plays the in curve backwards in time. */
object TextCurves {
    /**
     * Returns the frame of [content] at [localMicros] into a clip lasting [durationMicros]. The in and out
     * animations compose: alphas and scales multiply, offsets add, and the smaller revealed count wins.
     */
    fun frameAt(
        content: TextContent,
        localMicros: Long,
        durationMicros: Long,
    ): TextFrame {
        val length = content.text.length
        val time = localMicros.coerceIn(0L, durationMicros)
        val entering = content.animationIn?.let { frameOf(it, progress(time, it), length) }
        val leaving = content.animationOut?.let { frameOf(it, progress(durationMicros - time, it), length) }
        val still = TextFrame(alpha = 1f, scale = 1f, offsetY = 0f, revealed = length)
        return listOfNotNull(entering, leaving).fold(still) { acc, frame ->
            TextFrame(
                alpha = acc.alpha * frame.alpha,
                scale = acc.scale * frame.scale,
                offsetY = acc.offsetY + frame.offsetY,
                revealed = minOf(acc.revealed, frame.revealed),
            )
        }
    }

    /** Returns the cubic ease-out of [t]: fast at first, settling at 1. */
    fun easeOutCubic(t: Float): Float {
        val rest = 1f - t.coerceIn(0f, 1f)
        return 1f - rest * rest * rest
    }

    /** Returns the back ease-out of [t]: passes 1 slightly before settling, which reads as a pop. */
    fun overshoot(t: Float): Float {
        val shifted = t.coerceIn(0f, 1f) - 1f
        return 1f + (POP_OVERSHOOT + 1f) * shifted * shifted * shifted + POP_OVERSHOOT * shifted * shifted
    }

    /** Returns how far through [animation] the text is [elapsedMicros] from its edge, from 0 to 1. */
    private fun progress(
        elapsedMicros: Long,
        animation: TextAnimation,
    ): Float = (elapsedMicros.toFloat() / animation.durationMicros).coerceIn(0f, 1f)

    /** Returns the frame of one [animation] at [progress] for text of [length] characters. */
    private fun frameOf(
        animation: TextAnimation,
        progress: Float,
        length: Int,
    ): TextFrame {
        val eased = easeOutCubic(progress)
        return when (animation.kind) {
            TextAnimationKind.FADE -> TextFrame(eased, 1f, 0f, length)
            TextAnimationKind.SLIDE_UP -> TextFrame(eased, 1f, TEXT_SLIDE_DISTANCE * (1f - eased), length)
            TextAnimationKind.POP -> TextFrame(1f, overshoot(progress).coerceAtLeast(MIN_TEXT_SCALE), 0f, length)
            TextAnimationKind.TYPEWRITER -> TextFrame(1f, 1f, 0f, ceil(length * progress).toInt())
        }
    }
}

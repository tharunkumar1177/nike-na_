/*
 * Unit tests for text animation curves.
 */
package com.framecraft.editor.engine.text

import com.framecraft.editor.domain.model.TextAnimation
import com.framecraft.editor.domain.model.TextAnimationKind
import com.framecraft.editor.domain.model.TextContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tolerance for float comparisons. */
private const val EPSILON = 1e-4f

/** Clip length used by every case: four seconds. */
private const val CLIP = 4_000_000L

/** Animation length used by every case: one second. */
private const val SECOND = 1_000_000L

/** Verifies each animation kind, its time reversal on exit, and how in and out compose. */
class TextCurvesTest {
    /** Returns "Hello" entering with [entering] and leaving with [leaving], each lasting a second. */
    private fun content(
        entering: TextAnimationKind?,
        leaving: TextAnimationKind?,
    ) = TextContent(
        text = "Hello",
        animationIn = entering?.let { TextAnimation(it, SECOND) },
        animationOut = leaving?.let { TextAnimation(it, SECOND) },
    )

    /** Text without animations is fully shown for the whole clip. */
    @Test
    fun stillTextIsAlwaysShown() {
        val frame = TextCurves.frameAt(content(null, null), 0L, CLIP)
        assertEquals(TextFrame(alpha = 1f, scale = 1f, offsetY = 0f, revealed = 5), frame)
    }

    /** A fade rises from transparent and its exit mirrors the entrance in time. */
    @Test
    fun fadeOutMirrorsFadeIn() {
        val fade = content(TextAnimationKind.FADE, TextAnimationKind.FADE)
        assertEquals(0f, TextCurves.frameAt(fade, 0L, CLIP).alpha, EPSILON)
        assertEquals(1f, TextCurves.frameAt(fade, 2 * SECOND, CLIP).alpha, EPSILON)
        val entering = TextCurves.frameAt(fade, SECOND / 4, CLIP).alpha
        val leaving = TextCurves.frameAt(fade, CLIP - SECOND / 4, CLIP).alpha
        assertEquals(entering, leaving, EPSILON)
        assertEquals(TextCurves.easeOutCubic(0.25f), entering, EPSILON)
    }

    /** A slide starts below its place and settles there. */
    @Test
    fun slideRisesIntoPlace() {
        val slide = content(TextAnimationKind.SLIDE_UP, null)
        assertEquals(TEXT_SLIDE_DISTANCE, TextCurves.frameAt(slide, 0L, CLIP).offsetY, EPSILON)
        assertEquals(0f, TextCurves.frameAt(slide, SECOND, CLIP).offsetY, EPSILON)
    }

    /** A pop starts tiny, passes full size, and settles at full size. */
    @Test
    fun popOvershootsThenSettles() {
        val pop = content(TextAnimationKind.POP, null)
        assertEquals(MIN_TEXT_SCALE, TextCurves.frameAt(pop, 0L, CLIP).scale, EPSILON)
        assertTrue(TextCurves.frameAt(pop, SECOND * 3 / 4, CLIP).scale > 1f)
        assertEquals(1f, TextCurves.frameAt(pop, SECOND, CLIP).scale, EPSILON)
    }

    /** Typing reveals characters over the entrance, and the fewer revealed of in and out wins. */
    @Test
    fun typewriterRevealsAndComposes() {
        val typing = content(TextAnimationKind.TYPEWRITER, TextAnimationKind.TYPEWRITER)
        assertEquals(0, TextCurves.frameAt(typing, 0L, CLIP).revealed)
        assertEquals(3, TextCurves.frameAt(typing, SECOND / 2, CLIP).revealed)
        assertEquals(5, TextCurves.frameAt(typing, 2 * SECOND, CLIP).revealed)
        assertEquals(1, TextCurves.frameAt(typing, CLIP - SECOND / 10, CLIP).revealed)
        val short = TextCurves.frameAt(typing, SECOND / 2, SECOND)
        assertEquals(3, short.revealed)
    }
}

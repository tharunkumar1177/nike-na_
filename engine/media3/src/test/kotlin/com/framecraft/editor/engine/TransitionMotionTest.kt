/*
 * Unit tests for how each side of a transition moves as it plays.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tolerance for comparing motion values. */
private const val DELTA = 1e-6f

/** Verifies the motion of both sides of every transition kind. */
class TransitionMotionTest {
    /** Progress runs from 0 at the start to 1 at the end and stays there outside. */
    @Test
    fun progressClamps() {
        assertEquals(0f, TransitionMotion.progress(1_000, 2_000, 500), DELTA)
        assertEquals(0.25f, TransitionMotion.progress(1_000, 2_000, 1_250), DELTA)
        assertEquals(1f, TransitionMotion.progress(1_000, 2_000, 3_000), DELTA)
    }

    /** A dissolve fades the incoming clip in over an opaque outgoing clip; a zoom grows the outgoing clip too. */
    @Test
    fun dissolveAndZoom() {
        assertEquals(Motion(), TransitionMotion.outgoing(TransitionKind.DISSOLVE, 0.5f))
        assertEquals(0.5f, TransitionMotion.incoming(TransitionKind.DISSOLVE, 0.5f).alpha, DELTA)
        assertEquals(1.3f, TransitionMotion.outgoing(TransitionKind.ZOOM, 1f).zoom, DELTA)
        assertEquals(0.25f, TransitionMotion.incoming(TransitionKind.ZOOM, 0.25f).alpha, DELTA)
    }

    /** A fade empties the picture by halfway and fills it again by the end. */
    @Test
    fun fadesPassThroughBackdrop() {
        listOf(TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE).forEach { kind ->
            assertEquals(0.5f, TransitionMotion.outgoing(kind, 0.25f).alpha, DELTA)
            assertEquals(0f, TransitionMotion.outgoing(kind, 0.75f).alpha, DELTA)
            assertEquals(0f, TransitionMotion.incoming(kind, 0.25f).alpha, DELTA)
            assertEquals(0.5f, TransitionMotion.incoming(kind, 0.75f).alpha, DELTA)
        }
    }

    /** Slides and pushes bring the incoming clip from one canvas width right; a push moves the outgoing clip left. */
    @Test
    fun slidesAndPushes() {
        assertEquals(2f, TransitionMotion.incoming(TransitionKind.SLIDE_LEFT, 0f).shiftX, DELTA)
        assertEquals(0f, TransitionMotion.incoming(TransitionKind.PUSH_LEFT, 1f).shiftX, DELTA)
        assertEquals(0f, TransitionMotion.outgoing(TransitionKind.SLIDE_LEFT, 0.5f).shiftX, DELTA)
        assertEquals(-1f, TransitionMotion.outgoing(TransitionKind.PUSH_LEFT, 0.5f).shiftX, DELTA)
    }
}

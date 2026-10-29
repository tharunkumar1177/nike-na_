/*
 * Unit tests for planning transitions: the base track playing through each window, the incoming lane, the fade
 * backdrop, and the compositor inputs they register.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies a 1 s transition at the 4 s cut between A [0, 4 s), read from 0, and B [4 s, 8 s), read from 5 s. */
class TransitionPlanTest {
    private val video =
        MediaAsset(
            id = "v",
            uri = "content://media/v",
            kind = MediaKind.VIDEO,
            durationMicros = 10_000_000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    /** Builds a project whose base track runs A then B, with [kind] at the cut, B read from [rightTrimMicros]. */
    private fun project(
        kind: TransitionKind,
        rightTrimMicros: Long = 5_000_000L,
    ) = Project(
        id = "p",
        name = "P",
        frameRate = FrameRate.FPS_30,
        assets = listOf(video),
        tracks =
            listOf(
                Track(
                    "t",
                    TrackKind.VIDEO,
                    true,
                    listOf(
                        Clip("a", "v", 0, 0, 4_000_000, outTransition = ClipTransition(kind, 1_000_000)),
                        Clip("b", "v", 4_000_000, rightTrimMicros, 4_000_000),
                    ),
                ),
            ),
    )

    /** The base plays A on through the window as it fades out, then B on from where the lane leaves it. */
    @Test
    fun basePlaysThroughWindow() {
        val plan = PlaybackPlanner.plan(project(TransitionKind.DISSOLVE))
        assertTrue(plan.composited)
        assertEquals(listOf(SequenceRole.INCOMING, SequenceRole.TRACK), plan.sequences.map { it.role })
        val (a, b) = plan.sequences[1].segments.map { it as PlanSegment.Media }
        assertEquals(4_500_000L, a.durationMicros)
        assertEquals(0L, a.sourceStartMicros)
        assertEquals(4_500_000L, a.sourceSpanMicros)
        assertEquals(1_000_000L, a.fadeOutMicros)
        assertEquals(3_500_000L, b.durationMicros)
        assertEquals(5_500_000L, b.sourceStartMicros)
        assertEquals(3_500_000L, b.sourceSpanMicros)
        assertEquals(0L, b.fadeInMicros)
        val placement = CanvasPlacement()
        assertEquals(
            listOf(
                BaseSpan(0, 3_500_000, placement),
                BaseSpan(3_500_000, 4_500_000, placement, TransitionKind.DISSOLVE),
                BaseSpan(4_500_000, 8_000_000, placement),
            ),
            plan.sequences[1].baseSpans,
        )
    }

    /** The lane plays B's hidden source up to where the base takes over, its sound rising over the window. */
    @Test
    fun laneLeadsIntoIncomingClip() {
        val lane = PlaybackPlanner.plan(project(TransitionKind.SLIDE_LEFT)).sequences.first()
        assertEquals(listOf(3_500_000L, 1_000_000L, 3_500_000L), lane.segments.map { it.durationMicros })
        val media = lane.segments[1] as PlanSegment.Media
        assertEquals(4_500_000L, media.sourceStartMicros)
        assertEquals(1_000_000L, media.sourceSpanMicros)
        assertEquals(1_000_000L, media.fadeInMicros)
        assertEquals(
            listOf(TransitionSpan(3_500_000, 4_500_000, TransitionKind.SLIDE_LEFT, CanvasPlacement())),
            lane.transitionSpans,
        )
    }

    /** A fade adds a backdrop under the base, and the compositor registers lane, base, and backdrop in that order. */
    @Test
    fun fadeAddsBackdrop() {
        val plan = PlaybackPlanner.plan(project(TransitionKind.FADE_WHITE))
        val roles = plan.sequences.map { it.role }
        assertEquals(listOf(SequenceRole.INCOMING, SequenceRole.TRACK, SequenceRole.BACKDROP), roles)
        val still = plan.sequences.last().segments[1] as PlanSegment.Media
        assertEquals(MediaKind.IMAGE, still.kind)
        assertTrue(still.uri.startsWith("data:image/png"))
        val inputs = CanvasLayout.inputsOf(plan).map { it::class }
        assertEquals(
            listOf(
                CompositorInput.Clock::class,
                CompositorInput.Incoming::class,
                CompositorInput.Base::class,
                CompositorInput.Backdrop::class,
            ),
            inputs,
        )
    }

    /** A stored transition whose cut has no hidden source plays as a plain cut. */
    @Test
    fun silentTransitionPlansPlainCut() {
        val plan = PlaybackPlanner.plan(project(TransitionKind.DISSOLVE, rightTrimMicros = 0L))
        assertFalse(plan.composited)
        assertEquals(listOf(SequenceRole.TRACK), plan.sequences.map { it.role })
        val segments = plan.sequences.single().segments
        assertEquals(listOf(4_000_000L, 4_000_000L), segments.map { it.durationMicros })
    }
}

/*
 * Unit tests for planning masks and chroma keys: segments carry them through plain cuts and transition lanes, the
 * blurred background never does, and any of them sends the picture through the compositor.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasSettings
import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.MaskShape
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

/** Verifies masks and keys on a base track of A [0, 4 s), read from 0, and B [4 s, 8 s), read from 5 s. */
class MaskKeyPlanTest {
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

    private val circle = ClipMask.defaultFor(MaskShape.CIRCLE)

    private val green = ChromaKey(0xFF00FF00.toInt())

    /** Builds a project of A then B, where [left] and [right] dress each clip, with an optional [transition]. */
    private fun project(
        left: (Clip) -> Clip = { it },
        right: (Clip) -> Clip = { it },
        transition: ClipTransition? = null,
        canvas: CanvasSettings = CanvasSettings(),
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
                        left(Clip("a", "v", 0, 0, 4_000_000, outTransition = transition)),
                        right(Clip("b", "v", 4_000_000, 5_000_000, 4_000_000)),
                    ),
                ),
            ),
        canvas = canvas,
    )

    /** Returns the media segments of [sequence], in order. */
    private fun mediaOf(segments: List<PlanSegment>) = segments.filterIsInstance<PlanSegment.Media>()

    /** Each segment carries its own clip's mask and key, and either one composites the picture. */
    @Test
    fun segmentsCarryMaskAndKeyAndComposite() {
        assertFalse(PlaybackPlanner.plan(project()).composited)
        val masked = PlaybackPlanner.plan(project(left = { it.copy(mask = circle) }))
        assertTrue(masked.composited)
        assertEquals(listOf(circle, null), mediaOf(masked.sequences.single().segments).map { it.mask })
        val keyed = PlaybackPlanner.plan(project(right = { it.copy(chromaKey = green) }))
        assertTrue(keyed.composited)
        assertEquals(listOf(null, green), mediaOf(keyed.sequences.single().segments).map { it.chromaKey })
    }

    /** Through a transition the base keeps the outgoing mask and the incoming lane plays the incoming key. */
    @Test
    fun transitionLanesCarryMaskAndKey() {
        val plan =
            PlaybackPlanner.plan(
                project(
                    left = { it.copy(mask = circle) },
                    right = { it.copy(chromaKey = green) },
                    transition = ClipTransition(TransitionKind.DISSOLVE, 1_000_000),
                ),
            )
        val (incoming, base) = plan.sequences
        assertEquals(listOf(green), mediaOf(incoming.segments).map { it.chromaKey })
        assertEquals(listOf(circle, null), mediaOf(base.segments).map { it.mask })
    }

    /** The blurred background copies the base segments whole; the composition draws only their look there. */
    @Test
    fun blurCopiesSegments() {
        val canvas = CanvasSettings(background = CanvasBackground.Blur)
        val plan = PlaybackPlanner.plan(project(left = { it.copy(mask = circle) }, canvas = canvas))
        val blur = plan.background as BackgroundPlan.Blur
        assertEquals(circle, mediaOf(blur.segments).first().mask)
        assertTrue(plan.composited)
    }
}

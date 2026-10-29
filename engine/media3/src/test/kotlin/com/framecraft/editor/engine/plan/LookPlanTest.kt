/*
 * Unit tests for planning looks: segments carry their clip's look through plain cuts, transition lanes, and the
 * blurred background, and the plan knows when a filter needs SDR frames.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasSettings
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipLook
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies looks on a base track of A [0, 4 s), read from 0, and B [4 s, 8 s), read from 5 s. */
class LookPlanTest {
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

    private val warm = ClipLook(temperature = 40)

    private val noir = ClipLook(filterId = "noir")

    /** Builds a project of A with [leftLook] then B with [rightLook], with an optional [transition] at the cut. */
    private fun project(
        leftLook: ClipLook?,
        rightLook: ClipLook?,
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
                        Clip("a", "v", 0, 0, 4_000_000, outTransition = transition, look = leftLook),
                        Clip("b", "v", 4_000_000, 5_000_000, 4_000_000, look = rightLook),
                    ),
                ),
            ),
        canvas = canvas,
    )

    /** Returns the looks of the media segments of [sequence], in order. */
    private fun looksOf(sequence: SequencePlan) = sequence.segments.map { (it as PlanSegment.Media).look }

    /** Each segment of a plain cut carries its own clip's look. */
    @Test
    fun segmentsCarryLooks() {
        val plan = PlaybackPlanner.plan(project(warm, null))
        assertEquals(listOf(warm, null), looksOf(plan.sequences.single()))
    }

    /** Through a transition the base keeps the outgoing look and the incoming lane plays the incoming look. */
    @Test
    fun transitionLanesCarryLooks() {
        val plan = PlaybackPlanner.plan(project(warm, noir, ClipTransition(TransitionKind.DISSOLVE, 1_000_000)))
        val (incoming, base) = plan.sequences
        assertEquals(listOf(noir), incoming.segments.filterIsInstance<PlanSegment.Media>().map { it.look })
        assertEquals(listOf(warm, noir), looksOf(base))
    }

    /** The blurred background copies the base clips' looks. */
    @Test
    fun blurCarriesLooks() {
        val canvas = CanvasSettings(background = CanvasBackground.Blur)
        val plan = PlaybackPlanner.plan(project(warm, noir, canvas = canvas))
        val blur = plan.background as BackgroundPlan.Blur
        assertEquals(listOf(warm, noir), blur.segments.map { (it as PlanSegment.Media).look })
    }

    /** Only a filter at a visible intensity needs SDR frames; adjustments alone and a filter at 0 do not. */
    @Test
    fun filtersNeedSdr() {
        assertTrue(PlaybackPlanner.plan(project(null, noir)).hasLutLook)
        assertTrue(PlaybackPlanner.plan(project(null, noir.copy(filterIntensity = 50))).hasLutLook)
        assertFalse(PlaybackPlanner.plan(project(warm, null)).hasLutLook)
        assertFalse(PlaybackPlanner.plan(project(noir.copy(filterIntensity = 0), null)).hasLutLook)
        assertFalse(PlaybackPlanner.plan(project(null, null)).composited)
        assertNull(PlaybackPlanner.plan(project(null, null)).background)
    }
}

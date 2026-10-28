/*
 * Unit tests for translating projects into playback plans.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies segment order, gap filling, and image timing. */
class PlaybackPlannerTest {
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
    private val image =
        MediaAsset(
            id = "i",
            uri = "content://media/i",
            kind = MediaKind.IMAGE,
            durationMicros = null,
            width = 800,
            height = 600,
            rotationDegrees = 0,
            hasAudio = false,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    /** Builds a project with one track holding [clips]. */
    private fun project(
        magnetic: Boolean,
        vararg clips: Clip,
    ) =
        Project(
            id = "p",
            name = "P",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, image),
            tracks = listOf(Track("t", TrackKind.VIDEO, magnetic, clips.toList())),
        )

    /** Back-to-back clips map to media segments with source trim and full source duration. */
    @Test
    fun mapsContiguousClips() {
        val plan =
            PlaybackPlanner.plan(
                project(true, Clip("a", "v", 0, 2_000_000, 3_000_000), Clip("b", "i", 3_000_000, 0, 1_500_000)),
            )
        val segments = plan.sequences.single().segments
        assertEquals(
            listOf(
                PlanSegment.Media("content://media/v", MediaKind.VIDEO, 10_000_000, 2_000_000, 3_000_000),
                PlanSegment.Media("content://media/i", MediaKind.IMAGE, 1_500_000, 0, 1_500_000),
            ),
            segments,
        )
    }

    /** Implicit gaps, including a leading one, become gap segments. */
    @Test
    fun fillsGaps() {
        val plan =
            PlaybackPlanner.plan(
                project(false, Clip("a", "v", 1_000_000, 0, 1_000_000), Clip("b", "v", 4_000_000, 0, 1_000_000)),
            )
        val durations = plan.sequences.single().segments.map { it.durationMicros to (it is PlanSegment.Gap) }
        assertEquals(
            listOf(1_000_000L to true, 1_000_000L to false, 2_000_000L to true, 1_000_000L to false),
            durations,
        )
    }

    /** Empty tracks produce no sequences. */
    @Test
    fun skipsEmptyTracks() {
        assertEquals(0, PlaybackPlanner.plan(project(true)).sequences.size)
    }
}

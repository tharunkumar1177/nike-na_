/*
 * Unit tests for translating projects into playback plans.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
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
    ) = Project(
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
        val segments = plan.sequences.single().segments
        val durations = segments.map { it.durationMicros to (it is PlanSegment.Gap) }
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

    /** Overlay lanes follow the base sequence, padded from zero, with one placement span per overlay clip. */
    @Test
    fun plansOverlaysAfterBase() {
        val placed = OverlayTransform(centerX = 0.5f, scale = 0.4f)
        val o1 = Clip("o1", "v", 1_000_000, 0, 2_000_000, ClipAnchor("a", 1_000_000), placed)
        val o3 = Clip("o3", "i", 1_250_000, 0, 1_000_000, ClipAnchor("o1", 250_000), null)
        val project =
            Project(
                id = "p",
                name = "P",
                frameRate = FrameRate.FPS_30,
                assets = listOf(video, image),
                tracks =
                    listOf(
                        Track("lane", TrackKind.OVERLAY, false, listOf(o1)),
                        Track("t", TrackKind.VIDEO, true, listOf(Clip("a", "v", 0, 0, 6_000_000))),
                        Track("lane2", TrackKind.OVERLAY, false, listOf(o3)),
                        Track("empty", TrackKind.OVERLAY, false, emptyList()),
                    ),
            )
        val sequences = PlaybackPlanner.plan(project).sequences
        assertEquals(3, sequences.size)
        assertEquals(emptyList<OverlaySpan>(), sequences[0].overlaySpans)
        val overlayMedia = PlanSegment.Media("content://media/v", MediaKind.VIDEO, 10_000_000, 0, 2_000_000)
        assertEquals(listOf(PlanSegment.Gap(1_000_000), overlayMedia), sequences[1].segments)
        assertEquals(listOf(OverlaySpan(1_000_000, 3_000_000, placed)), sequences[1].overlaySpans)
        assertEquals(listOf(1_250_000L, 1_000_000L), sequences[2].segments.map { it.durationMicros })
        assertEquals(listOf(OverlaySpan(1_250_000, 2_250_000, OverlayTransform())), sequences[2].overlaySpans)
    }

    /** Audio lanes come last with their volume and fades, and the base pads to music that outlasts it. */
    @Test
    fun plansAudioLastAndPadsBase() {
        val song = video.copy(id = "s", uri = "content://media/s", kind = MediaKind.AUDIO, width = 1, height = 1)
        val music = Clip("m", "s", 1_000_000, 0, 5_000_000, volumePercent = 150, fadeOutMicros = 1_000_000)
        val base = Clip("a", "v", 0, 0, 4_000_000, volumePercent = 0)
        val project =
            Project(
                id = "p",
                name = "P",
                frameRate = FrameRate.FPS_30,
                assets = listOf(video, song),
                tracks =
                    listOf(
                        Track("music", TrackKind.AUDIO, false, listOf(music)),
                        Track("t", TrackKind.VIDEO, true, listOf(base)),
                    ),
            )
        val sequences = PlaybackPlanner.plan(project).sequences
        assertEquals(listOf(TrackKind.VIDEO, TrackKind.AUDIO), sequences.map { it.kind })
        val muted = PlanSegment.Media("content://media/v", MediaKind.VIDEO, 10_000_000, 0, 4_000_000, 0)
        assertEquals(listOf(muted, PlanSegment.Gap(2_000_000)), sequences[0].segments)
        val tune = PlanSegment.Media("content://media/s", MediaKind.AUDIO, 10_000_000, 0, 5_000_000, 150, 0, 1_000_000)
        assertEquals(listOf(PlanSegment.Gap(1_000_000), tune), sequences[1].segments)
    }
}

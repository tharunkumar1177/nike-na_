/*
 * Unit tests for translating projects into playback plans.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasAspect
import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.CanvasSettings
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.TEXT_ASSET
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** A sped clip plays its source span over its timeline duration at its speed and pitch setting. */
    @Test
    fun carriesSpeed() {
        val twice = ClipSpeed.Constant(2_000)
        val sped =
            Clip(
                "a",
                "v",
                0,
                1_000_000,
                2_000_000,
                speed = twice,
                sourceDurationMicros = 4_000_000,
                maintainPitch = false,
            )
        val media =
            PlaybackPlanner
                .plan(project(true, sped))
                .sequences
                .single()
                .segments
                .single() as PlanSegment.Media
        assertEquals(twice, media.speed)
        assertEquals(4_000_000L, media.sourceSpanMicros)
        assertEquals(2_000_000L, media.durationMicros)
        assertFalse(media.maintainPitch)
    }

    /** Empty tracks produce no sequences. */
    @Test
    fun skipsEmptyTracks() {
        assertEquals(0, PlaybackPlanner.plan(project(true)).sequences.size)
    }

    /** Overlay lanes come before the base from the top lane down, padded to the end, with one span per clip. */
    @Test
    fun plansOverlaysBeforeBase() {
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
        assertEquals(listOf(TrackKind.OVERLAY, TrackKind.OVERLAY, TrackKind.VIDEO), sequences.map { it.kind })
        val overlayMedia = PlanSegment.Media("content://media/v", MediaKind.VIDEO, 10_000_000, 0, 2_000_000)
        assertEquals(
            listOf(PlanSegment.Gap(1_000_000), overlayMedia, PlanSegment.Gap(3_000_000)),
            sequences[0].segments,
        )
        assertEquals(listOf(OverlaySpan(1_000_000, 3_000_000, placed)), sequences[0].overlaySpans)
        assertEquals(listOf(1_250_000L, 1_000_000L, 3_750_000L), sequences[1].segments.map { it.durationMicros })
        assertEquals(listOf(OverlaySpan(1_250_000, 2_250_000, OverlayTransform())), sequences[1].overlaySpans)
        assertEquals(emptyList<OverlaySpan>(), sequences[2].overlaySpans)
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

    /** Text adds no sequence, pads the base to its end, and lists spans from the bottom text lane up. */
    @Test
    fun plansTextAsSpansOnTop() {
        val placed = OverlayTransform(centerY = 0.6f)
        val title = TextContent("Title")
        val caption = TextContent("Caption")
        val top = Clip("top", TEXT_ASSET_ID, 0, 0, 2_000_000, ClipAnchor("a", 0), placed, text = title)
        val lower = Clip("low", TEXT_ASSET_ID, 1_000_000, 0, 4_000_000, ClipAnchor("a", 1_000_000), text = caption)
        val project =
            Project(
                id = "p",
                name = "P",
                frameRate = FrameRate.FPS_30,
                assets = listOf(video, TEXT_ASSET),
                tracks =
                    listOf(
                        Track("upper", TrackKind.TEXT, false, listOf(top)),
                        Track("lower", TrackKind.TEXT, false, listOf(lower)),
                        Track("t", TrackKind.VIDEO, true, listOf(Clip("a", "v", 0, 0, 3_000_000))),
                    ),
            )
        val plan = PlaybackPlanner.plan(project)
        assertEquals(listOf(TrackKind.VIDEO), plan.sequences.map { it.kind })
        val base = plan.sequences.single()
        assertEquals(PlanSegment.Gap(2_000_000), base.segments.last())
        assertEquals(
            listOf(
                TextSpan(1_000_000, 5_000_000, OverlayTransform(), caption),
                TextSpan(0, 2_000_000, placed, title),
            ),
            plan.textSpans,
        )
    }

    /** Default canvases skip the compositor; a shape, a background, or a base placement turns it on. */
    @Test
    fun canvasChoicesTurnOnCompositing() {
        val plain = project(true, Clip("a", "v", 0, 0, 2_000_000), Clip("b", "i", 2_000_000, 0, 1_000_000))
        val defaults = PlaybackPlanner.plan(plain)
        assertFalse(defaults.composited)
        assertNull(defaults.aspectRatio)
        assertNull(defaults.background)
        assertEquals(
            listOf(BaseSpan(0, 2_000_000, CanvasPlacement()), BaseSpan(2_000_000, 3_000_000, CanvasPlacement())),
            defaults.sequences.single().baseSpans,
        )
        val portrait = PlaybackPlanner.plan(plain.copy(canvas = CanvasSettings(CanvasAspect.PORTRAIT_9_16)))
        assertTrue(portrait.composited)
        assertEquals(9f / 16f, requireNotNull(portrait.aspectRatio), 0f)
        val filled = CanvasPlacement(CanvasFit.FILL, scale = 1.5f)
        val track = plain.tracks.single()
        val first = track.clips[0].copy(canvasPlacement = filled)
        val placedTrack = track.copy(clips = listOf(first) + track.clips.drop(1))
        val placed = PlaybackPlanner.plan(plain.copy(tracks = listOf(placedTrack)))
        assertTrue(placed.composited)
        assertEquals(
            listOf(BaseSpan(0, 2_000_000, filled), BaseSpan(2_000_000, 3_000_000, CanvasPlacement())),
            placed.sequences.single().baseSpans,
        )
    }

    /** A solid background plans its color and a blur copies the first base track's segments and spans. */
    @Test
    fun plansBackgrounds() {
        val plain = project(false, Clip("a", "v", 1_000_000, 0, 2_000_000))
        val solid = PlaybackPlanner.plan(plain.copy(canvas = CanvasSettings(background = CanvasBackground.Solid(RED))))
        assertEquals(BackgroundPlan.Solid(RED), solid.background)
        val blur = PlaybackPlanner.plan(plain.copy(canvas = CanvasSettings(background = CanvasBackground.Blur)))
        val base = blur.sequences.single()
        assertEquals(BackgroundPlan.Blur(base.segments, base.baseSpans), blur.background)
        assertEquals(
            listOf(CompositorInput.Clock, CompositorInput.Base(base.baseSpans), CompositorInput.Blur(base.baseSpans)),
            CanvasLayout.inputsOf(blur),
        )
    }
}

/** Opaque red. */
private const val RED = 0xFFFF0000.toInt()

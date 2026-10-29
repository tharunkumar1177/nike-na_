/*
 * Unit tests for clip speed: the speed model and its time mapping, the speed command, and how split, merge, and trim
 * treat sped clips.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.edit.TrimController
import com.framecraft.editor.domain.edit.TrimEdge
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.model.SpeedPoint
import com.framecraft.editor.domain.model.SpeedPreset
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.slice
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies speed against a magnetic base track of A [0, 4 s) and B [4 s, 8 s), both reading a 10 s video. */
class SpeedCommandsTest {
    private val json = Json { classDiscriminator = "type" }

    private val video =
        MediaAsset(
            id = "video",
            uri = "content://media/video",
            kind = MediaKind.VIDEO,
            durationMicros = 10 * SECOND,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    private val fixture =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video),
            tracks =
                listOf(
                    Track(
                        "base",
                        TrackKind.VIDEO,
                        true,
                        listOf(Clip("A", "video", 0L, 0L, 4 * SECOND), Clip("B", "video", 4 * SECOND, 0L, 4 * SECOND)),
                    ),
                ),
        )

    private val twice = ClipSpeed.Constant(2_000)

    /** Applies [command] to [project] and returns the outcome as applied, failing otherwise. */
    private fun applied(
        command: EditCommand,
        project: Project = fixture,
    ): CommandOutcome.Applied {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Applied, "Expected $command to apply, got $outcome")
        return outcome as CommandOutcome.Applied
    }

    /** Returns clip [id] of [project]'s base track. */
    private fun clip(
        project: Project,
        id: String,
    ): Clip =
        project.tracks
            .single()
            .clips
            .single { it.id == id }

    /** Speeds outside their ranges and curves that do not span the clip are refused. */
    @Test
    fun speedsValidate() {
        assertThrows<IllegalArgumentException> { ClipSpeed.Constant(99) }
        assertThrows<IllegalArgumentException> { ClipSpeed.Constant(100_001) }
        assertThrows<IllegalArgumentException> { ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000))) }
        assertThrows<IllegalArgumentException> {
            ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(50_000, 1_000)))
        }
        assertThrows<IllegalArgumentException> {
            ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(100_000, 20_000)))
        }
        SpeedPreset.entries.forEach { assertEquals(it, SpeedPreset.matching(it.curve)) }
        assertNull(SpeedPreset.matching(SpeedPreset.customStart))
    }

    /** A constant speed divides the timeline length and maps both ways. */
    @Test
    fun constantSpeedMapsTime() {
        val map = SpeedMap.of(twice, 4 * SECOND, FrameRate.FPS_30)
        assertEquals(2 * SECOND, map.durationMicros)
        assertEquals(SECOND, map.timelineAt(2 * SECOND))
        assertEquals(2 * SECOND, map.sourceAt(SECOND))
        assertEquals(2f, map.speedAt(0L))
        assertNull(map.nextChangeAfter(0L))
        assertEquals(4 * SECOND, SpeedMap.spanFor(twice, 2 * SECOND, FrameRate.FPS_30))
        assertEquals(3 * SECOND, SpeedMap.durationFor(null, 3 * SECOND, FrameRate.FPS_30))
    }

    /** A flat curve plays like its constant speed and a ramp lasts between its slowest and fastest. */
    @Test
    fun curveSpeedMapsTime() {
        val flat = ClipSpeed.Curve(listOf(SpeedPoint(0, 2_000), SpeedPoint(100_000, 2_000)))
        val flatMap = SpeedMap.of(flat, 4 * SECOND, FrameRate.FPS_30)
        assertTrue(abs(flatMap.durationMicros - 2 * SECOND) <= 1)
        val ramp = ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(100_000, 4_000)))
        val rampMap = SpeedMap.of(ramp, 4 * SECOND, FrameRate.FPS_30)
        assertTrue(rampMap.durationMicros in SECOND + 1 until 4 * SECOND)
        assertTrue(rampMap.speedAt(0L) < rampMap.speedAt(3 * SECOND))
        val half = rampMap.sourceAt(rampMap.durationMicros / 2)
        assertTrue(abs(rampMap.timelineAt(half) - rampMap.durationMicros / 2) <= 1)
        assertTrue(requireNotNull(rampMap.nextChangeAfter(0L)) <= FrameRate.FPS_30.startOfFrame(1))
    }

    /** Slicing a curve keeps the speeds at the cut and the points between, moved to the part. */
    @Test
    fun curveSlices() {
        val ramp = ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(50_000, 3_000), SpeedPoint(100_000, 1_000)))
        val left = ramp.slice(4 * SECOND, 0L, SECOND) as ClipSpeed.Curve
        assertEquals(listOf(SpeedPoint(0, 1_000), SpeedPoint(100_000, 2_000)), left.points)
        val right = ramp.slice(4 * SECOND, SECOND, 4 * SECOND) as ClipSpeed.Curve
        assertEquals(
            listOf(SpeedPoint(0, 2_000), SpeedPoint(33_333, 3_000), SpeedPoint(100_000, 1_000)),
            right.points,
        )
        assertEquals(twice, twice.slice(4 * SECOND, SECOND, 2 * SECOND))
    }

    /** Doubling a clip's speed halves it, ripples the clips after it, and undoes exactly. */
    @Test
    fun speedRipplesAndUndoes() {
        val result = applied(SetClipSpeed("A", twice))
        val sped = clip(result.project, "A")
        assertEquals(2 * SECOND, sped.durationMicros)
        assertEquals(4 * SECOND, sped.sourceSpanMicros)
        assertEquals(2 * SECOND, clip(result.project, "B").startMicros)
        assertEquals(fixture, applied(result.inverse, result.project).project)
        val slow = applied(SetClipSpeed("A", ClipSpeed.Constant(500), maintainPitch = false)).project
        assertEquals(8 * SECOND, clip(slow, "A").durationMicros)
        assertTrue(!clip(slow, "A").maintainPitch)
    }

    /** A speed that leaves less than one frame is refused, as are speeds on clips without a timed source. */
    @Test
    fun speedRejectsTooShort() {
        val tiny = applied(TrimClip("A", 0L, 0L, 40_000L)).project
        val outcome = SetClipSpeed("A", ClipSpeed.Constant(100_000)).applyTo(tiny)
        assertTrue((outcome as CommandOutcome.Rejected).reason is CommandRejection.ClipTooShort)
        val missing = SetClipSpeed("Z", twice).applyTo(fixture)
        assertEquals(CommandRejection.ClipNotFound("Z"), (missing as CommandOutcome.Rejected).reason)
    }

    /** Splitting a sped clip cuts its source where the speed plays the cut, and merging undoes it exactly. */
    @Test
    fun splitAndMergeKeepSpeed() {
        val ramp = ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(100_000, 3_000)))
        val sped = applied(SetClipSpeed("A", ramp)).project
        val cutAt = clip(sped, "A").durationMicros / 2
        val split = applied(SplitClip("A", cutAt, "A2"), sped)
        val left = clip(split.project, "A")
        val right = clip(split.project, "A2")
        assertEquals(4 * SECOND, left.sourceSpanMicros + right.sourceSpanMicros)
        assertEquals(left.trimStartMicros + left.sourceSpanMicros, right.trimStartMicros)
        assertTrue(left.sourceSpanMicros < 2 * SECOND, "The slow start reads less source per second")
        assertEquals(sped, applied(split.inverse, split.project).project)
        val unmerged = MergeClips("A", "A2").applyTo(split.project)
        assertTrue(unmerged is CommandOutcome.Rejected)
    }

    /** Parts at the same constant speed merge into one sped clip. */
    @Test
    fun constantPartsMerge() {
        val sped = applied(SetClipSpeed("A", twice)).project
        val split = applied(SplitClip("A", SECOND, "A2"), sped).project
        assertEquals(2 * SECOND, clip(split, "A2").trimStartMicros)
        val merged = clip(applied(MergeClips("A", "A2"), split).project, "A")
        assertEquals(clip(sped, "A"), merged)
    }

    /** Trimming a sped clip's end trims its source at the speed and its command applies. */
    @Test
    fun trimFollowsSpeed() {
        val sped = applied(SetClipSpeed("A", twice)).project
        val track = sped.tracks.single()
        val controller = TrimController.forClip(track, clip(sped, "A"), video, sped.frameRate, TrimEdge.END)
        val proposal = controller.propose(-SECOND, emptyList(), 0L)
        assertEquals(SECOND, proposal.durationMicros)
        assertEquals(2 * SECOND, proposal.sourceDurationMicros)
        val trimmed = clip(applied(controller.commandFor(proposal), sped).project, "A")
        assertEquals(2 * SECOND, trimmed.sourceSpanMicros)
        val longest = controller.propose(10 * SECOND, emptyList(), 0L)
        assertEquals(5 * SECOND, longest.durationMicros)
        val start = TrimController.forClip(track, clip(sped, "A"), video, sped.frameRate, TrimEdge.START)
        val pinned = start.propose(SECOND, emptyList(), 0L)
        assertEquals(SECOND, pinned.durationMicros)
        assertEquals(2 * SECOND, pinned.trimStartMicros)
    }

    /** The speed command round-trips through JSON with constant, curve, and normal speeds. */
    @Test
    fun speedCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                SetClipSpeed("A", twice),
                SetClipSpeed("A", SpeedPreset.HERO.curve, maintainPitch = false, fadeInMicros = 0L),
                SetClipSpeed("A", null),
                SplitClip("A", SECOND, "A2", leftSourceMicros = 2 * SECOND),
                MergeClips("A", "A2", speed = SpeedPreset.BULLET.curve),
                TrimClip("A", 0L, 0L, SECOND, sourceDurationMicros = 2 * SECOND),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

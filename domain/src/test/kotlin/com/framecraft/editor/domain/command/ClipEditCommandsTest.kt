/*
 * Unit tests for trim, split, merge, and move commands: results, inverses, rejections, and JSON round-trips.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** One frame at 30 fps, the shortest clip allowed. */
private const val FRAME = 33_334L

/** Verifies the clip edit commands on magnetic and free tracks. */
class ClipEditCommandsTest {
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

    private val image =
        MediaAsset(
            id = "image",
            uri = "content://media/image",
            kind = MediaKind.IMAGE,
            durationMicros = null,
            width = 1080,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = false,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    /** Returns clip [id] of [asset] at [start] lasting [duration], reading from [trimStart]; times in microseconds. */
    private fun clip(
        id: String,
        start: Long,
        duration: Long,
        trimStart: Long = 0L,
        asset: String = "video",
    ) = Clip(id = id, assetId = asset, startMicros = start, trimStartMicros = trimStart, durationMicros = duration)

    /** Returns a project with one track holding [clips]. */
    private fun project(
        magnetic: Boolean,
        vararg clips: Clip,
    ) = Project(
        id = "project",
        name = "Trip",
        frameRate = FrameRate.FPS_30,
        assets = listOf(video, image),
        tracks = listOf(Track("track", TrackKind.VIDEO, magnetic, clips.toList())),
    )

    /** Returns the clips on the single track of [project]. */
    private fun clipsOf(project: Project): List<Clip> = project.tracks.single().clips

    /** Applies [command] to [project], failing if it is rejected, and checks its inverse restores [project]. */
    private fun applied(
        command: EditCommand,
        project: Project,
    ): Project {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Applied, "Expected success, got $outcome")
        val forward = outcome as CommandOutcome.Applied
        val restored = forward.inverse.applyTo(forward.project)
        assertEquals(project, (restored as CommandOutcome.Applied).project)
        return forward.project
    }

    /** Returns why [command] was rejected on [project], failing the test if it applied. */
    private fun rejection(
        command: EditCommand,
        project: Project,
    ): CommandRejection {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Rejected, "Expected rejection, got $outcome")
        return (outcome as CommandOutcome.Rejected).reason
    }

    /** Shortening a clip on a magnetic track pulls later clips left. */
    @Test
    fun trimEndShrinkPullsFollowers() {
        val start = project(true, clip("a", 0, 2 * SECOND), clip("b", 2 * SECOND, SECOND))
        val result = applied(TrimClip("a", 0, 0, SECOND), start)
        assertEquals(listOf(0L, SECOND), clipsOf(result).map { it.startMicros })
        assertEquals(SECOND, clipsOf(result).first().durationMicros)
    }

    /** Lengthening a clip on a magnetic track pushes later clips right. */
    @Test
    fun trimEndExtendPushesFollowers() {
        val start = project(true, clip("a", 0, 2 * SECOND), clip("b", 2 * SECOND, SECOND))
        val result = applied(TrimClip("a", 0, 0, 3 * SECOND), start)
        assertEquals(3 * SECOND, clipsOf(result).last().startMicros)
    }

    /** Trimming the start on a magnetic track keeps the start, moves the source offset, and ripples. */
    @Test
    fun trimStartOnMagneticTrackPinsStart() {
        val start = project(true, clip("a", 0, 2 * SECOND), clip("b", 2 * SECOND, SECOND))
        val result = applied(TrimClip("a", 0, SECOND, SECOND), start)
        val (a, b) = clipsOf(result)
        assertEquals(listOf(0L, SECOND, SECOND), listOf(a.startMicros, a.trimStartMicros, a.durationMicros))
        assertEquals(SECOND, b.startMicros)
    }

    /** Moving a clip's start on a magnetic track would open a gap and is rejected. */
    @Test
    fun trimRejectsMovedStartOnMagneticTrack() {
        val start = project(true, clip("a", 0, SECOND), clip("b", SECOND, SECOND))
        assertTrue(rejection(TrimClip("b", 2 * SECOND, 0, SECOND), start) is CommandRejection.ModelInvariant)
    }

    /** Trims on a free track leave other clips in place and reject overlaps. */
    @Test
    fun trimOnFreeTrackDoesNotRipple() {
        val start = project(false, clip("a", 0, SECOND), clip("b", 2 * SECOND, SECOND))
        val result = applied(TrimClip("a", 0, 0, 3 * SECOND / 2), start)
        assertEquals(2 * SECOND, clipsOf(result).last().startMicros)
        assertTrue(rejection(TrimClip("a", 0, 0, 3 * SECOND), start) is CommandRejection.ModelInvariant)
    }

    /** Clips shorter than one frame and clips reading past the source are rejected. */
    @Test
    fun trimRejectsTooShortAndPastSource() {
        val start = project(true, clip("a", 0, 2 * SECOND))
        assertEquals(CommandRejection.ClipTooShort(FRAME), rejection(TrimClip("a", 0, 0, FRAME - 1), start))
        assertTrue(rejection(TrimClip("a", 0, 9 * SECOND, 2 * SECOND), start) is CommandRejection.ModelInvariant)
        assertEquals(clipNotFound("x"), TrimClip("x", 0, 0, SECOND).applyTo(start))
    }

    /** Image clips can last any length but have no source offset to trim. */
    @Test
    fun trimImageClips() {
        val start = project(true, clip("a", 0, SECOND, asset = "image"))
        assertEquals(60 * SECOND, clipsOf(applied(TrimClip("a", 0, 0, 60 * SECOND), start)).single().durationMicros)
        assertTrue(rejection(TrimClip("a", 0, SECOND, SECOND), start) is CommandRejection.ModelInvariant)
    }

    /** Splitting a video clip keeps the left id and continues the source in the right part. */
    @Test
    fun splitVideoClip() {
        val start = project(true, clip("a", 0, 3 * SECOND, trimStart = SECOND), clip("b", 3 * SECOND, SECOND))
        val (left, right, after) = clipsOf(applied(SplitClip("a", SECOND, "a2"), start))
        assertEquals(clip("a", 0, SECOND, trimStart = SECOND), left)
        assertEquals(clip("a2", SECOND, 2 * SECOND, trimStart = 2 * SECOND), right)
        assertEquals(3 * SECOND, after.startMicros)
    }

    /** Splitting an image clip keeps the source offset in both parts. */
    @Test
    fun splitImageClip() {
        val start = project(true, clip("a", 0, 2 * SECOND, asset = "image"))
        val (_, right) = clipsOf(applied(SplitClip("a", SECOND, "a2"), start))
        assertEquals(0L, right.trimStartMicros)
    }

    /** Splits leaving less than a frame on a side, or reusing an id, are rejected. */
    @Test
    fun splitRejections() {
        val start = project(true, clip("a", 0, SECOND), clip("b", SECOND, SECOND))
        assertEquals(CommandRejection.ClipTooShort(FRAME), rejection(SplitClip("a", FRAME - 1, "a2"), start))
        assertEquals(CommandRejection.ClipTooShort(FRAME), rejection(SplitClip("a", SECOND - 1, "a2"), start))
        assertTrue(rejection(SplitClip("a", SECOND / 2, "b"), start) is CommandRejection.ModelInvariant)
        assertTrue(SplitClip.isSplittable(clipsOf(start).first(), FRAME, FrameRate.FPS_30))
        assertFalse(SplitClip.isSplittable(clipsOf(start).first(), 0, FrameRate.FPS_30))
    }

    /** Merging rejects clips that do not continue each other. */
    @Test
    fun mergeRejectsUnrelatedClips() {
        val gap = project(false, clip("a", 0, SECOND), clip("b", 2 * SECOND, SECOND, trimStart = SECOND))
        assertTrue(rejection(MergeClips("a", "b"), gap) is CommandRejection.ModelInvariant)
        val jump = project(true, clip("a", 0, SECOND), clip("b", SECOND, SECOND, trimStart = 5 * SECOND))
        assertTrue(rejection(MergeClips("a", "b"), jump) is CommandRejection.ModelInvariant)
        val mixed = project(true, clip("a", 0, SECOND), clip("b", SECOND, SECOND, asset = "image"))
        assertTrue(rejection(MergeClips("a", "b"), mixed) is CommandRejection.ModelInvariant)
        assertEquals(clipNotFound("z"), MergeClips("a", "z").applyTo(mixed))
    }

    /** Merging two continuous parts makes one clip. */
    @Test
    fun mergeContinuousParts() {
        val start = project(true, clip("a", 0, SECOND), clip("a2", SECOND, SECOND, trimStart = SECOND))
        assertEquals(listOf(clip("a", 0, 2 * SECOND)), clipsOf(applied(MergeClips("a", "a2"), start)))
    }

    /** Reordering on a magnetic track lands the clip in the slot and keeps the track contiguous. */
    @Test
    fun moveOnMagneticTrack() {
        val start =
            project(
                true,
                clip("a", 0, 2 * SECOND),
                clip("b", 2 * SECOND, 3 * SECOND),
                clip("c", 5 * SECOND, 4 * SECOND),
            )
        val cases =
            listOf(
                MoveClip("a", 3 * SECOND) to listOf("b", "a", "c"),
                MoveClip("c", 0) to listOf("c", "a", "b"),
                MoveClip("a", 7 * SECOND) to listOf("b", "c", "a"),
                MoveClip("b", 0) to listOf("b", "a", "c"),
                MoveClip("b", 2 * SECOND) to listOf("a", "b", "c"),
            )
        cases.forEach { (command, order) -> assertEquals(order, clipsOf(applied(command, start)).map { it.id }) }
        assertTrue(rejection(MoveClip("a", SECOND), start) is CommandRejection.ModelInvariant)
    }

    /** Moving on a free track changes only the clip's start and rejects overlaps. */
    @Test
    fun moveOnFreeTrack() {
        val start = project(false, clip("a", 0, SECOND), clip("b", 2 * SECOND, SECOND))
        val moved = applied(MoveClip("a", 5 * SECOND), start)
        assertEquals(listOf(2 * SECOND, 5 * SECOND), clipsOf(moved).map { it.startMicros })
        assertTrue(rejection(MoveClip("a", 5 * SECOND / 2), start) is CommandRejection.ModelInvariant)
    }

    /** The new commands survive a JSON round-trip through the sealed hierarchy. */
    @Test
    fun commandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                TrimClip("a", 1, 2, 3),
                SplitClip("a", 4, "b"),
                MergeClips("a", "b"),
                MoveClip("a", 5),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
        assertTrue(json.encodeToString(EditCommand.serializer(), MoveClip("a", 5)).contains("\"type\":\"moveClip\""))
    }
}

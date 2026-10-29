/*
 * Unit tests for transitions: where a cut can take one and for how long, the transition commands and their undo,
 * and how split, merge, and trim treat a clip's transition.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.SpeedPoint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionGeometry
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/**
 * Verifies transitions on a magnetic base track of A [0, 4 s) reading a 10 s video from 0, and B [4 s, 8 s) and
 * C [8 s, 10 s) reading it from 5 s and 1 s, so every cut has hidden source on both sides.
 */
class TransitionCommandsTest {
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

    private val photo =
        MediaAsset(
            id = "photo",
            uri = "content://media/photo",
            kind = MediaKind.IMAGE,
            durationMicros = null,
            width = 1080,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = false,
            fingerprint = SourceFingerprint(sizeBytes = 2L, lastModifiedMillis = null),
        )

    private val fixture =
        baseOf(
            Clip("A", "video", 0L, 0L, 4 * SECOND),
            Clip("B", "video", 4 * SECOND, 5 * SECOND, 4 * SECOND),
            Clip("C", "video", 8 * SECOND, 1 * SECOND, 2 * SECOND),
        )

    private val dissolve = ClipTransition(TransitionKind.DISSOLVE, SECOND)

    /** Returns a project whose magnetic base track holds [clips]. */
    private fun baseOf(vararg clips: Clip): Project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, photo),
            tracks = listOf(Track("base", TrackKind.VIDEO, true, clips.toList())),
        )

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

    /** Returns the transitions that play on [project]'s base track. */
    private fun cuts(project: Project) = TransitionGeometry.cutsOf(project, project.tracks.single())

    /** A cut allows half the shorter clip, twice the hidden source on each side, and never more than 4 s. */
    @Test
    fun capFollowsClipsAndHandles() {
        val a = clip(fixture, "A")
        val b = clip(fixture, "B")
        val c = clip(fixture, "C")
        assertEquals(2 * SECOND, TransitionGeometry.capMicros(fixture, a, b))
        assertEquals(SECOND, TransitionGeometry.capMicros(fixture, b, c))
        val bare = baseOf(Clip("A", "video", 0L, 0L, 4 * SECOND), Clip("B", "video", 4 * SECOND, 0L, 4 * SECOND))
        assertEquals(0L, TransitionGeometry.capMicros(bare, clip(bare, "A"), clip(bare, "B")))
        val tight =
            baseOf(Clip("A", "video", 0L, 0L, 9_800_000L), Clip("B", "video", 9_800_000L, 5 * SECOND, 5 * SECOND))
        assertEquals(400_000L, TransitionGeometry.capMicros(tight, clip(tight, "A"), clip(tight, "B")))
    }

    /** Stills have unlimited hidden source; a clip on a speed curve has none; a constant speed scales the handle. */
    @Test
    fun capFollowsStillsAndSpeed() {
        val stills = baseOf(Clip("P", "photo", 0L, 0L, 2 * SECOND), Clip("Q", "photo", 2 * SECOND, 0L, 2 * SECOND))
        assertEquals(SECOND, TransitionGeometry.capMicros(stills, clip(stills, "P"), clip(stills, "Q")))
        val curve = ClipSpeed.Curve(listOf(SpeedPoint(0, 1_000), SpeedPoint(100_000, 2_000)))
        val curved = applied(SetClipSpeed("B", curve)).project
        assertEquals(0L, TransitionGeometry.capMicros(curved, clip(curved, "A"), clip(curved, "B")))
        val tight = baseOf(Clip("A", "video", 0L, 0L, 4 * SECOND), Clip("B", "video", 4 * SECOND, 200_000L, 4 * SECOND))
        val fast = applied(SetClipSpeed("B", ClipSpeed.Constant(500)), tight).project
        assertEquals(800_000L, TransitionGeometry.capMicros(fast, clip(fast, "A"), clip(fast, "B")))
    }

    /** A transition is held to its cut's limits when set, and undo restores the cut exactly. */
    @Test
    fun setTransitionClampsAndUndoes() {
        val set = applied(SetTransition("A", dissolve.copy(durationMicros = 3 * SECOND)))
        assertEquals(dissolve.copy(durationMicros = 2 * SECOND), clip(set.project, "A").outTransition)
        val cut = cuts(set.project).single()
        assertEquals(3 * SECOND, cut.startMicros)
        assertEquals(5 * SECOND, cut.endMicros)
        assertEquals(fixture, applied(set.inverse, set.project).project)
        val short = applied(SetTransition("A", dissolve.copy(durationMicros = 1L)))
        assertEquals(100_000L, clip(short.project, "A").outTransition?.durationMicros)
    }

    /** Clearing removes the transition, and undo puts it back. */
    @Test
    fun clearingUndoes() {
        val set = applied(SetTransition("A", dissolve)).project
        val cleared = applied(SetTransition("A", null), set)
        assertNull(clip(cleared.project, "A").outTransition)
        assertEquals(set, applied(cleared.inverse, cleared.project).project)
    }

    /** Clips with no cut after them, cuts without hidden source, and missing clips are refused. */
    @Test
    fun setTransitionRejects() {
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.NotButted("C")),
            SetTransition("C", dissolve).applyTo(fixture),
        )
        val bare = baseOf(Clip("A", "video", 0L, 0L, 4 * SECOND), Clip("B", "video", 4 * SECOND, 0L, 4 * SECOND))
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.TransitionTooShort(0L)),
            SetTransition("A", dissolve).applyTo(bare),
        )
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.ClipNotFound("Z")),
            SetTransition("Z", dissolve).applyTo(fixture),
        )
    }

    /** Applying to all reaches every cut that fits, each held to its limit, and one undo clears them all. */
    @Test
    fun applyToAllIsOneUndo() {
        val all = applied(ApplyTransitionToAll("base", dissolve.copy(durationMicros = 3 * SECOND)))
        assertEquals(2 * SECOND, clip(all.project, "A").outTransition?.durationMicros)
        assertEquals(SECOND, clip(all.project, "B").outTransition?.durationMicros)
        assertNull(clip(all.project, "C").outTransition)
        assertEquals(2, cuts(all.project).size)
        assertEquals(fixture, applied(all.inverse, all.project).project)
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.TrackNotFound("lane")),
            ApplyTransitionToAll("lane", dissolve).applyTo(fixture),
        )
    }

    /** A split keeps the transition on the right part, leaves the new cut bare, and undoes exactly. */
    @Test
    fun splitMovesTransitionRight() {
        val set = applied(SetTransition("A", dissolve)).project
        val split = applied(SplitClip("A", 2 * SECOND, "A2"), set)
        assertNull(clip(split.project, "A").outTransition)
        assertEquals(dissolve, clip(split.project, "A2").outTransition)
        assertEquals(set, applied(split.inverse, split.project).project)
    }

    /** Merging across a transition is refused; the merged clip keeps the right part's transition. */
    @Test
    fun mergeRespectsTransitions() {
        val split = applied(SplitClip("A", 2 * SECOND, "A2")).project
        val atJoin = applied(SetTransition("A", dissolve), split).project
        assertTrue(MergeClips("A", "A2").applyTo(atJoin) is CommandOutcome.Rejected)
        val after = applied(SetTransition("A2", dissolve), split).project
        val merged = applied(MergeClips("A", "A2"), after).project
        assertEquals(dissolve, clip(merged, "A").outTransition)
    }

    /** A trim that uses up the hidden source silences the transition without losing it, and undo brings it back. */
    @Test
    fun trimKeepsStoredTransition() {
        val set = applied(SetTransition("A", dissolve)).project
        val trimmed = applied(TrimClip("B", 4 * SECOND, 0L, 4 * SECOND), set)
        assertTrue(cuts(trimmed.project).isEmpty())
        assertEquals(dissolve, clip(trimmed.project, "A").outTransition)
        val restored = applied(trimmed.inverse, trimmed.project).project
        assertEquals(1, cuts(restored).size)
    }

    /** The window splits an odd length with the extra microsecond after the cut. */
    @Test
    fun windowCentersOnCut() {
        val set = applied(SetTransition("A", dissolve.copy(durationMicros = 500_001L))).project
        val cut = cuts(set).single()
        assertEquals(250_000L, cut.tailMicros)
        assertEquals(250_001L, cut.headMicros)
        assertEquals(cut.durationMicros, cut.endMicros - cut.startMicros)
    }

    /** Every transition command survives a JSON round trip. */
    @Test
    fun transitionCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                SetTransition("A", dissolve),
                SetTransition("A", null),
                SetTransitions(listOf(TransitionValue("A", dissolve), TransitionValue("B", null))),
                ApplyTransitionToAll("base", ClipTransition(TransitionKind.PUSH_LEFT, 600_000L)),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

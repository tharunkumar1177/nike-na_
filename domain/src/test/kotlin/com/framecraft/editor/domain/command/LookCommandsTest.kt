/*
 * Unit tests for looks: the look model, setting and clearing one, applying one to every picture clip, and how
 * split and merge treat a clip's look.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.LookFilterCatalog
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/**
 * Verifies looks on a magnetic base track of video A [0, 4 s) and photo P [4 s, 6 s), an overlay lane holding video
 * O [0, 2 s), and an audio lane holding A's sound S [0, 4 s).
 */
class LookCommandsTest {
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
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, photo),
            tracks =
                listOf(
                    Track(
                        "base",
                        TrackKind.VIDEO,
                        true,
                        listOf(Clip("A", "video", 0L, 0L, 4 * SECOND), Clip("P", "photo", 4 * SECOND, 0L, 2 * SECOND)),
                    ),
                    Track("lane", TrackKind.OVERLAY, false, listOf(Clip("O", "video", 0L, 0L, 2 * SECOND))),
                    Track("sound", TrackKind.AUDIO, false, listOf(Clip("S", "video", 0L, 0L, 4 * SECOND))),
                ),
        )

    private val warm = ClipLook(filterId = "honey", filterIntensity = 60, brightness = 10, temperature = 25)

    /** Applies [command] to [project] and returns the outcome as applied, failing otherwise. */
    private fun applied(
        command: EditCommand,
        project: Project = fixture,
    ): CommandOutcome.Applied {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Applied, "Expected $command to apply, got $outcome")
        return outcome as CommandOutcome.Applied
    }

    /** Returns the look of clip [id] in [project]. */
    private fun lookOf(
        project: Project,
        id: String,
    ): ClipLook? = requireNotNull(project.locate(id)).clip.look

    /** A look checks its ranges; neutral means no filter and no adjustment, and a filter at 0 draws nothing. */
    @Test
    fun lookValidatesAndClassifies() {
        assertThrows<IllegalArgumentException> { ClipLook(filterIntensity = 101) }
        assertThrows<IllegalArgumentException> { ClipLook(tint = -101) }
        assertThrows<IllegalArgumentException> { ClipLook(filterId = " ") }
        assertTrue(ClipLook().isNeutral)
        assertFalse(ClipLook(saturation = 1).isNeutral)
        val faded = ClipLook(filterId = "noir", filterIntensity = 0)
        assertFalse(faded.isNeutral)
        assertFalse(faded.usesFilter)
        assertTrue(warm.usesFilter)
        val ids = LookFilterCatalog.all.map { it.id }
        assertEquals(18, ids.toSet().size)
    }

    /** Setting a look stores it, clearing and undo restore exactly, and a neutral look is stored as none. */
    @Test
    fun setLookStoresAndUndoes() {
        val set = applied(SetClipLook("A", warm))
        assertEquals(warm, lookOf(set.project, "A"))
        assertEquals(fixture, applied(set.inverse, set.project).project)
        val cleared = applied(SetClipLook("A", null), set.project)
        assertNull(lookOf(cleared.project, "A"))
        assertEquals(set.project, applied(cleared.inverse, cleared.project).project)
        val neutral = applied(SetClipLook("A", ClipLook()), set.project)
        assertNull(lookOf(neutral.project, "A"))
    }

    /** Photos and overlays take looks; sound, unknown filters, and missing clips are refused. */
    @Test
    fun setLookRejects() {
        assertEquals(warm, lookOf(applied(SetClipLook("P", warm)).project, "P"))
        assertEquals(warm, lookOf(applied(SetClipLook("O", warm)).project, "O"))
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.LookNotSupported("S")),
            SetClipLook("S", warm).applyTo(fixture),
        )
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.UnknownLookFilter("glow")),
            SetClipLook("A", ClipLook(filterId = "glow")).applyTo(fixture),
        )
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.ClipNotFound("Z")),
            SetClipLook("Z", warm).applyTo(fixture),
        )
        assertTrue(SetClipLook("S", null).applyTo(fixture) is CommandOutcome.Applied)
    }

    /** Applying to all reaches every picture clip but not sound, and one undo restores every look. */
    @Test
    fun applyToAllIsOneUndo() {
        val one = applied(SetClipLook("A", ClipLook(contrast = 40))).project
        val all = applied(ApplyLookToAll(warm), one)
        assertEquals(listOf(warm, warm, warm), listOf("A", "P", "O").map { lookOf(all.project, it) })
        assertNull(lookOf(all.project, "S"))
        assertEquals(one, applied(all.inverse, all.project).project)
        assertEquals(3, ApplyLookToAll.valuesFor(fixture, warm).size)
        val soundOnly = fixture.copy(tracks = fixture.tracks.filter { it.kind == TrackKind.AUDIO })
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.NoLookTargets),
            ApplyLookToAll(warm).applyTo(soundOnly),
        )
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.UnknownLookFilter("glow")),
            ApplyLookToAll(ClipLook(filterId = "glow")).applyTo(fixture),
        )
    }

    /** Both parts of a split keep the look; parts with equal looks merge and different looks do not. */
    @Test
    fun splitAndMergeKeepLooks() {
        val set = applied(SetClipLook("A", warm)).project
        val split = applied(SplitClip("A", 2 * SECOND, "A2"), set).project
        assertEquals(warm, lookOf(split, "A"))
        assertEquals(warm, lookOf(split, "A2"))
        assertEquals(warm, lookOf(applied(MergeClips("A", "A2"), split).project, "A"))
        val differs = applied(SetClipLook("A2", ClipLook(tint = 5)), split).project
        assertTrue(MergeClips("A", "A2").applyTo(differs) is CommandOutcome.Rejected)
    }

    /** Sound extracted from a clip with a look carries none. */
    @Test
    fun extractedSoundHasNoLook() {
        val silent = fixture.copy(tracks = fixture.tracks.filter { it.kind != TrackKind.AUDIO })
        val looked = applied(SetClipLook("A", warm), silent).project
        val ids = ArrayDeque(listOf("lane2", "S2"))
        val extract = requireNotNull(AudioPlacement.proposeExtract(looked, "A", IdGenerator { ids.removeFirst() }))
        assertNull(extract.clip.look)
        assertNull(lookOf(applied(extract, looked).project, extract.clip.id))
    }

    /** A project refuses a look on a clip off a picture track. */
    @Test
    fun projectRefusesLookOnSound() {
        val tracks =
            fixture.tracks.map { track ->
                val looked = track.clips.map { it.copy(look = warm) }
                if (track.kind == TrackKind.AUDIO) track.copy(clips = looked) else track
            }
        assertThrows<IllegalArgumentException> { fixture.copy(tracks = tracks) }
    }

    /** Every look command survives a JSON round trip. */
    @Test
    fun lookCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                SetClipLook("A", warm),
                SetClipLook("A", null),
                SetLooks(listOf(LookValue("A", warm), LookValue("P", null))),
                ApplyLookToAll(ClipLook(filterId = "noir")),
                ApplyLookToAll(null),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

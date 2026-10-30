/*
 * Unit tests for masks and chroma keys: their models, setting and clearing them, and how split, merge, and audio
 * extraction treat them.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MaskShape
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

/** Opaque pure green. */
private const val GREEN = 0xFF00FF00.toInt()

/**
 * Verifies masks and keys on a base track of video A [0, 4 s) and photo P [4 s, 6 s), an overlay lane holding video
 * O [0, 2 s), and an audio lane holding A's sound S [0, 4 s).
 */
class MaskKeyCommandsTest {
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

    private val circle = ClipMask.defaultFor(MaskShape.CIRCLE).copy(centerX = 0.3f, invert = true)

    private val green = ChromaKey(GREEN)

    /** Applies [command] to [project] and returns the outcome as applied, failing otherwise. */
    private fun applied(
        command: EditCommand,
        project: Project = fixture,
    ): CommandOutcome.Applied {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Applied, "Expected $command to apply, got $outcome")
        return outcome as CommandOutcome.Applied
    }

    /** Returns clip [id] in [project]. */
    private fun clipOf(
        project: Project,
        id: String,
    ): Clip = requireNotNull(project.locate(id)).clip

    /** Masks and keys check their ranges, every shape has a valid default, and a key needs an opaque color. */
    @Test
    fun modelsValidate() {
        assertThrows<IllegalArgumentException> { ClipMask(MaskShape.CIRCLE, halfWidth = 0f) }
        assertThrows<IllegalArgumentException> { ClipMask(MaskShape.CIRCLE, halfHeight = 2.5f) }
        assertThrows<IllegalArgumentException> { ClipMask(MaskShape.CIRCLE, centerX = 3f) }
        assertThrows<IllegalArgumentException> { ClipMask(MaskShape.STAR, featherPercent = 101) }
        assertThrows<IllegalArgumentException> { ClipMask(MaskShape.RECTANGLE, rotationDegrees = Float.NaN) }
        assertEquals(MaskShape.entries.toList(), MaskShape.entries.map { ClipMask.defaultFor(it).shape })
        assertEquals(20, ClipMask.defaultFor(MaskShape.RECTANGLE).cornerPercent)
        assertEquals(0.7f, ClipMask.defaultFor(MaskShape.RECTANGLE).halfWidth)
        assertThrows<IllegalArgumentException> { ChromaKey(0x0000FF00) }
        assertThrows<IllegalArgumentException> { ChromaKey(GREEN, tolerancePercent = 101) }
        assertTrue(ChromaKey(GREEN, tolerancePercent = 0).isNeutral)
        assertFalse(green.isNeutral)
    }

    /** Setting a mask stores it, clearing and undo restore exactly, and photos and overlays take masks. */
    @Test
    fun setMaskStoresAndUndoes() {
        val set = applied(SetClipMask("A", circle))
        assertEquals(circle, clipOf(set.project, "A").mask)
        assertEquals(fixture, applied(set.inverse, set.project).project)
        val cleared = applied(SetClipMask("A", null), set.project)
        assertNull(clipOf(cleared.project, "A").mask)
        assertEquals(set.project, applied(cleared.inverse, cleared.project).project)
        assertEquals(circle, clipOf(applied(SetClipMask("P", circle)).project, "P").mask)
        assertEquals(circle, clipOf(applied(SetClipMask("O", circle)).project, "O").mask)
    }

    /** Setting a key stores it, a key that keys nothing is stored as none, and undo restores exactly. */
    @Test
    fun setKeyStoresAndUndoes() {
        val set = applied(SetClipChromaKey("O", green))
        assertEquals(green, clipOf(set.project, "O").chromaKey)
        assertEquals(fixture, applied(set.inverse, set.project).project)
        val neutral = applied(SetClipChromaKey("O", green.copy(tolerancePercent = 0)), set.project)
        assertNull(clipOf(neutral.project, "O").chromaKey)
        assertEquals(set.project, applied(neutral.inverse, neutral.project).project)
    }

    /** Sound clips refuse masks and keys, clearing them is always allowed, and missing clips are refused. */
    @Test
    fun soundAndMissingClipsReject() {
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.MaskNotSupported("S")),
            SetClipMask("S", circle).applyTo(fixture),
        )
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.ChromaKeyNotSupported("S")),
            SetClipChromaKey("S", green).applyTo(fixture),
        )
        assertTrue(SetClipMask("S", null).applyTo(fixture) is CommandOutcome.Applied)
        assertTrue(SetClipChromaKey("S", null).applyTo(fixture) is CommandOutcome.Applied)
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.ClipNotFound("Z")),
            SetClipMask("Z", circle).applyTo(fixture),
        )
    }

    /** Both parts of a split keep mask and key; parts merge only when both match. */
    @Test
    fun splitAndMergeKeepMaskAndKey() {
        val masked = applied(SetClipMask("A", circle)).project
        val keyed = applied(SetClipChromaKey("A", green), masked).project
        val split = applied(SplitClip("A", 2 * SECOND, "A2"), keyed).project
        assertEquals(circle, clipOf(split, "A2").mask)
        assertEquals(green, clipOf(split, "A2").chromaKey)
        val merged = applied(MergeClips("A", "A2"), split).project
        assertEquals(circle, clipOf(merged, "A").mask)
        val reshaped = applied(SetClipMask("A2", ClipMask.defaultFor(MaskShape.STAR)), split).project
        assertTrue(MergeClips("A", "A2").applyTo(reshaped) is CommandOutcome.Rejected)
        val rekeyed = applied(SetClipChromaKey("A2", green.copy(spillPercent = 0)), split).project
        assertTrue(MergeClips("A", "A2").applyTo(rekeyed) is CommandOutcome.Rejected)
    }

    /** Sound extracted from a masked and keyed clip carries neither. */
    @Test
    fun extractedSoundDropsMaskAndKey() {
        val silent = fixture.copy(tracks = fixture.tracks.filter { it.kind != TrackKind.AUDIO })
        val masked = applied(SetClipMask("A", circle), silent).project
        val keyed = applied(SetClipChromaKey("A", green), masked).project
        val ids = ArrayDeque(listOf("lane2", "S2"))
        val extract = requireNotNull(AudioPlacement.proposeExtract(keyed, "A", IdGenerator { ids.removeFirst() }))
        assertNull(extract.clip.mask)
        assertNull(extract.clip.chromaKey)
        assertTrue(extract.applyTo(keyed) is CommandOutcome.Applied)
    }

    /** A project refuses a mask or key on a clip off a picture track. */
    @Test
    fun projectRefusesMaskOrKeyOnSound() {
        listOf<(Clip) -> Clip>({ it.copy(mask = circle) }, { it.copy(chromaKey = green) }).forEach { change ->
            val tracks =
                fixture.tracks.map { track ->
                    if (track.kind == TrackKind.AUDIO) track.copy(clips = track.clips.map(change)) else track
                }
            assertThrows<IllegalArgumentException> { fixture.copy(tracks = tracks) }
        }
    }

    /** Both commands survive a JSON round trip. */
    @Test
    fun commandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                SetClipMask("A", circle),
                SetClipMask("A", null),
                SetClipChromaKey("O", green),
                SetClipChromaKey("O", null),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

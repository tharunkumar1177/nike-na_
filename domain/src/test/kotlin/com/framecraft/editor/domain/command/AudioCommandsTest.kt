/*
 * Unit tests for sound editing: extraction, music clips, volume and fades, the fade rules of trim, split, and
 * merge, the audio model rules, and the JSON of the audio commands.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.AUDIO_ASSET_SIDE
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies audio editing against a base track with video clip A [0, 6 s) and photo clip P [6 s, 9 s). */
class AudioCommandsTest {
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
        video.copy(
            id = "photo",
            uri = "content://media/photo",
            kind = MediaKind.IMAGE,
            durationMicros = null,
            hasAudio = false,
        )

    private val music =
        MediaAsset(
            id = "music",
            uri = "content://media/music",
            kind = MediaKind.AUDIO,
            durationMicros = 20 * SECOND,
            width = AUDIO_ASSET_SIDE,
            height = AUDIO_ASSET_SIDE,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 2L, lastModifiedMillis = 3L),
        )

    /** The base clips: video clip A [0, 6 s) and photo clip P [6 s, 9 s). */
    private val base = listOf(Clip("A", "video", 0L, 0L, 6 * SECOND), Clip("P", "photo", 6 * SECOND, 0L, 3 * SECOND))

    /** Returns the fixture project with [baseClips] on the base track and [audio] tracks below it. */
    private fun fixture(
        vararg audio: Track,
        baseClips: List<Clip> = base,
    ): Project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, photo) + if (audio.isEmpty()) emptyList() else listOf(music),
            tracks = listOf(Track("T0", TrackKind.VIDEO, true, baseClips)) + audio,
        )

    /** Returns the volume and fades of clip [id] in [project]. */
    private fun soundOf(
        project: Project,
        id: String,
    ): List<Long> = clip(project, id).let { listOf(it.volumePercent.toLong(), it.fadeInMicros, it.fadeOutMicros) }

    /** Returns an audio lane [id] holding [clips]. */
    private fun lane(
        id: String,
        vararg clips: Clip,
    ) = Track(id, TrackKind.AUDIO, false, clips.toList())

    /** Applies [command] to [project], failing if it is rejected, and checks its inverse restores [project]. */
    private fun applied(
        command: EditCommand,
        project: Project,
    ): CommandOutcome.Applied {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Applied, "Expected success, got $outcome")
        val forward = outcome as CommandOutcome.Applied
        val restored = forward.inverse.applyTo(forward.project)
        assertEquals(project, (restored as CommandOutcome.Applied).project)
        return forward
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

    /** Returns clip [id] in [project]. */
    private fun clip(
        project: Project,
        id: String,
    ): Clip = project.tracks.flatMap { it.clips }.single { it.id == id }

    /** The audio lifted from clip A: same source range, attached to A. */
    private val extracted = Clip("X", "video", 0L, 0L, 6 * SECOND, anchor = ClipAnchor("A", 0L))

    /** Extracting mutes the video and adds an attached audio lane below; undo and redo restore each state exactly. */
    @Test
    fun extractMutesAndUndoRedoRestores() {
        val start = fixture()
        val forward = applied(ExtractAudio("A", "L1", extracted), start)
        assertEquals(0, clip(forward.project, "A").volumePercent)
        assertEquals(listOf(TrackKind.VIDEO, TrackKind.AUDIO), forward.project.tracks.map { it.kind })
        assertEquals(RemoveAudioClip("X", "A", 100), forward.inverse)
        val undone = forward.inverse.applyTo(forward.project) as CommandOutcome.Applied
        val redone = undone.inverse.applyTo(undone.project) as CommandOutcome.Applied
        assertEquals(forward.project, redone.project)
    }

    /** Extraction is refused for clips without sound and for audio that reads another asset. */
    @Test
    fun extractRejectsSilentSources() {
        val silent = fixture().copy(assets = listOf(video.copy(hasAudio = false), photo))
        assertTrue(rejection(ExtractAudio("A", "L1", extracted), silent) is CommandRejection.ModelInvariant)
        val fromPhoto = extracted.copy(assetId = "photo", startMicros = 6 * SECOND, anchor = ClipAnchor("P", 0L))
        assertTrue(rejection(ExtractAudio("P", "L1", fromPhoto), fixture()) is CommandRejection.ModelInvariant)
        assertEquals(clipNotFound("missing"), ExtractAudio("missing", "L1", extracted).applyTo(fixture()))
    }

    /** Music adds its asset and a lane at the end; removing it drops both again. */
    @Test
    fun musicAddsAndRemovesLaneAndAsset() {
        val start = fixture()
        val song = Clip("M", "music", 2 * SECOND, 0L, 20 * SECOND)
        val added = applied(AddAudioClip("L1", song, music), start).project
        assertEquals(listOf("T0", "L1"), added.tracks.map { it.id })
        assertEquals(22 * SECOND, added.durationMicros)
        val removed = applied(RemoveAudioClip("M"), added).project
        assertEquals(start, removed)
    }

    /** Removing extracted audio keeps the shared video asset; the video stays muted unless this undoes extraction. */
    @Test
    fun removeExtractedKeepsSharedAsset() {
        val start = applied(ExtractAudio("A", "L1", extracted), fixture()).project
        val removed = applied(RemoveAudioClip("X"), start).project
        assertEquals(listOf("video", "photo"), removed.assets.map { it.id })
        assertEquals(0, clip(removed, "A").volumePercent)
        assertTrue(rejection(RemoveAudioClip("A"), start) is CommandRejection.ModelInvariant)
    }

    /** Audio lanes refuse clips of other kinds and overlaps; non-audio lanes refuse audio clips. */
    @Test
    fun audioLanesKeepTheirKind() {
        val start = fixture(lane("L1", Clip("M", "music", 0L, 0L, 4 * SECOND)))
        val overlap = Clip("N", "music", 2 * SECOND, 0L, 4 * SECOND)
        assertTrue(rejection(AddAudioClip("L1", overlap, music), start) is CommandRejection.ModelInvariant)
        assertTrue(rejection(AddAudioClip("T0", overlap, music), start) is CommandRejection.ModelInvariant)
        val picture = Clip("Q", "photo", 5 * SECOND, 0L, SECOND)
        assertTrue(rejection(AddAudioClip("L1", picture, photo), start) is CommandRejection.ModelInvariant)
    }

    /** Volume and fades change together and invert; images and fades longer than the clip are refused. */
    @Test
    fun setClipAudioChangesAndRejects() {
        val start = fixture()
        val result = applied(SetClipAudio("A", 150, SECOND, 2 * SECOND), start).project
        assertEquals(listOf(150L, SECOND, 2 * SECOND), soundOf(result, "A"))
        assertTrue(rejection(SetClipAudio("P", 50, 0L, 0L), start) is CommandRejection.ModelInvariant)
        assertTrue(rejection(SetClipAudio("A", 100, 4 * SECOND, 3 * SECOND), start) is CommandRejection.ModelInvariant)
        assertTrue(rejection(SetClipAudio("A", 201, 0L, 0L), start) is CommandRejection.ModelInvariant)
    }

    /** Shortening a clip shrinks its fades, fade-in first, and undo brings the old fades back. */
    @Test
    fun trimClampsFadesWithExactInverse() {
        val faded = applied(SetClipAudio("A", 100, 3 * SECOND, 3 * SECOND), fixture()).project
        val trimmed = applied(TrimClip("A", 0L, 0L, 4 * SECOND), faded).project
        assertEquals(listOf(100L, 3 * SECOND, SECOND), soundOf(trimmed, "A"))
        val explicit = applied(TrimClip("A", 0L, 0L, 4 * SECOND, 0L, 2 * SECOND), faded).project
        assertEquals(listOf(100L, 0L, 2 * SECOND), soundOf(explicit, "A"))
    }

    /** Splitting gives the fade-in to the left part and the fade-out to the right part; merging joins them back. */
    @Test
    fun splitDividesFadesAndMergeJoins() {
        val faded = applied(SetClipAudio("A", 80, SECOND, 5 * SECOND), fixture()).project
        val split = applied(SplitClip("A", 2 * SECOND, "A2"), faded).project
        assertEquals(listOf(80L, SECOND, 0L), soundOf(split, "A"))
        assertEquals(listOf(80L, 0L, 4 * SECOND), soundOf(split, "A2"))
        val louder = applied(SetClipAudio("A2", 120, 0L, 0L), split).project
        assertTrue(rejection(MergeClips("A", "A2"), louder) is CommandRejection.ModelInvariant)
    }

    /** Audio model rules hold: no magnetic audio lanes, no audio assets on video tracks, silent images. */
    @Test
    fun modelRejectsInvalidAudio() {
        assertThrows<IllegalArgumentException> { fixture(Track("L1", TrackKind.AUDIO, true, emptyList())) }
        val onBase = base + Clip("M", "music", 9 * SECOND, 0L, SECOND)
        assertThrows<IllegalArgumentException> { fixture(lane("L1"), baseClips = onBase) }
        assertThrows<IllegalArgumentException> { music.copy(width = 2) }
        assertThrows<IllegalArgumentException> { Clip("c", "a", 0L, 0L, SECOND, fadeInMicros = -1L) }
        assertThrows<IllegalArgumentException> { fixture(baseClips = base.map { it.copy(volumePercent = 50) }) }
    }

    /** The audio commands survive a JSON round-trip through the sealed hierarchy. */
    @Test
    fun audioCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                ExtractAudio("A", "L1", extracted),
                AddAudioClip("L1", Clip("M", "music", 0L, 0L, SECOND, volumePercent = 50), music),
                RemoveAudioClip("X", "A", 100),
                SetClipAudio("A", 0, SECOND, SECOND),
                TrimClip("A", 0L, 0L, SECOND, 0L, 0L),
                MergeClips("A", "A2", SECOND, null),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

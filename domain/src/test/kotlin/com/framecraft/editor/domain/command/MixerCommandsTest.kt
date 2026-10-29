/*
 * Unit tests for the mixer and voiceovers: track volumes, clip ducking, voiceover takes, and how removing a lane's
 * last clip and undoing it keep the lane's volume.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.IdGenerator
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
 * Verifies the mixer on a magnetic base track holding video A [0, 4 s) and an audio lane holding music M [0, 3 s).
 */
class MixerCommandsTest {
    private val json = Json { classDiscriminator = "type" }

    private val video = asset("video", MediaKind.VIDEO)

    private val song = asset("song", MediaKind.AUDIO)

    /** Returns a ten-second asset [id] of [kind] with sound. */
    private fun asset(
        id: String,
        kind: MediaKind,
    ): MediaAsset {
        val picture = kind == MediaKind.VIDEO
        return MediaAsset(
            id = id,
            uri = "content://media/$id",
            kind = kind,
            durationMicros = 10 * SECOND,
            width = if (picture) 1920 else 1,
            height = if (picture) 1080 else 1,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )
    }

    private val fixture =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, song),
            tracks =
                listOf(
                    Track("base", TrackKind.VIDEO, true, listOf(Clip("A", "video", 0L, 0L, 4 * SECOND))),
                    Track("music", TrackKind.AUDIO, false, listOf(Clip("M", "song", 0L, 0L, 3 * SECOND))),
                ),
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

    /** Returns the volume of track [id] in [project]. */
    private fun volumeOf(
        project: Project,
        id: String,
    ): Int? = project.tracks.firstOrNull { it.id == id }?.volumePercent

    /** Track volumes and ducking check their ranges; text tracks keep unity and voiceovers must be sound. */
    @Test
    fun modelValidatesMixFields() {
        assertThrows<IllegalArgumentException> { Track("t", TrackKind.AUDIO, false, emptyList(), 201) }
        assertThrows<IllegalArgumentException> { Track("t", TrackKind.TEXT, false, emptyList(), 50) }
        assertThrows<IllegalArgumentException> { Clip("c", "song", 0L, 0L, SECOND, autoDuckPercent = 101) }
        assertFalse(Clip("c", "song", 0L, 0L, SECOND, autoDuckPercent = 10).hasDefaultAudio)
        val base = fixture.tracks.first()
        val pictureVoice = base.copy(clips = base.clips.map { it.copy(voiceover = true) })
        assertThrows<IllegalArgumentException> { fixture.copy(tracks = listOf(pictureVoice) + fixture.tracks.drop(1)) }
    }

    /** Setting track volumes stores them as one undo step that restores every previous volume. */
    @Test
    fun trackVolumesStoreAndUndo() {
        val set = applied(SetTrackVolumes(listOf(TrackVolumeValue("base", 0), TrackVolumeValue("music", 150))))
        assertEquals(0, volumeOf(set.project, "base"))
        assertEquals(150, volumeOf(set.project, "music"))
        assertEquals(fixture, applied(set.inverse, set.project).project)
    }

    /** A missing track, a text track, and a volume out of range are refused. */
    @Test
    fun trackVolumesReject() {
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.TrackNotFound("lane")),
            SetTrackVolumes(listOf(TrackVolumeValue("lane", 50))).applyTo(fixture),
        )
        val titled = fixture.copy(tracks = fixture.tracks + Track("words", TrackKind.TEXT, false, emptyList()))
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.TrackVolumeNotSupported("words")),
            SetTrackVolumes(listOf(TrackVolumeValue("words", 50))).applyTo(titled),
        )
        assertTrue(SetTrackVolumes(listOf(TrackVolumeValue("music", 300))).applyTo(fixture) is CommandOutcome.Rejected)
    }

    /** Removing a lane's last clip and undoing it brings the lane back at its volume. */
    @Test
    fun removingLastClipKeepsLaneVolume() {
        val quiet = applied(SetTrackVolumes(listOf(TrackVolumeValue("music", 40)))).project
        val removed = applied(RemoveAudioClip("M"), quiet)
        assertNull(volumeOf(removed.project, "music"))
        assertEquals(quiet, applied(removed.inverse, removed.project).project)
    }

    /** Clip sound edits carry ducking, undo restores it, and edits that leave it out turn it off. */
    @Test
    fun clipAudioCarriesDucking() {
        val ducked = applied(SetClipAudio("M", 80, 0L, 0L, autoDuckPercent = 60))
        val lane = ducked.project.tracks[1]
        assertEquals(60, lane.clips.single().autoDuckPercent)
        assertEquals(fixture, applied(ducked.inverse, ducked.project).project)
        val reset = applied(SetClipAudio("M", 80, 0L, 0L), ducked.project).project.tracks[1]
        assertEquals(0, reset.clips.single().autoDuckPercent)
    }

    /** A take lands as a voiceover at the playhead; parts that duck differently do not merge. */
    @Test
    fun takesAreVoiceovers() {
        val take = asset("take", MediaKind.AUDIO).copy(durationMicros = 2 * SECOND)
        val ids = ArrayDeque(listOf("lane2", "V"))
        val proposed = AudioPlacement.proposeTake(fixture, SECOND, take, IdGenerator { ids.removeFirst() })
        val command = requireNotNull(proposed)
        assertTrue(command.clip.voiceover)
        assertEquals(SECOND, command.clip.startMicros)
        assertTrue(AudioPlacement.canRecord(fixture, SECOND))
        val split = applied(SplitClip("M", SECOND, "M2")).project
        val differs = applied(SetClipAudio("M2", 100, 0L, 0L, autoDuckPercent = 30), split).project
        assertTrue(MergeClips("M", "M2").applyTo(differs) is CommandOutcome.Rejected)
    }

    /** No room for a voiceover once every audio lane is busy at the playhead. */
    @Test
    fun noRecordingWhenLanesAreFull() {
        val lanes =
            (0 until AudioPlacement.MAX_AUDIO_LANES).map { index ->
                Track("lane$index", TrackKind.AUDIO, false, listOf(Clip("S$index", "song", 0L, 0L, 3 * SECOND)))
            }
        val full = fixture.copy(tracks = fixture.tracks.take(1) + lanes)
        assertFalse(AudioPlacement.canRecord(full, SECOND))
        assertTrue(AudioPlacement.canRecord(full, 3 * SECOND))
    }

    /** The mixer commands survive a JSON round trip. */
    @Test
    fun mixerCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                SetTrackVolumes(listOf(TrackVolumeValue("base", 0))),
                SetClipAudio("M", 80, 0L, 0L, autoDuckPercent = 60),
                AddAudioClip(
                    "music",
                    Clip("V", "song", 0L, 0L, SECOND, voiceover = true),
                    song,
                    trackVolumePercent = 40,
                ),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

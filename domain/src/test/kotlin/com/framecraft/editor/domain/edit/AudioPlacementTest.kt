/*
 * Unit tests for where new music and extracted audio land.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.AUDIO_ASSET_SIDE
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies [AudioPlacement] on a base track with a trimmed video clip A [0, 4 s) and a photo clip P [4 s, 7 s). */
class AudioPlacementTest {
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

    private val photo = video.copy(id = "photo", kind = MediaKind.IMAGE, durationMicros = null, hasAudio = false)

    private val song =
        video.copy(
            id = "song",
            kind = MediaKind.AUDIO,
            durationMicros = 5 * SECOND,
            width = AUDIO_ASSET_SIDE,
            height = AUDIO_ASSET_SIDE,
        )

    private val project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, photo),
            tracks =
                listOf(
                    Track(
                        "T0",
                        TrackKind.VIDEO,
                        true,
                        listOf(
                            Clip("A", "video", 0L, 2 * SECOND, 4 * SECOND, fadeInMicros = SECOND),
                            Clip("P", "photo", 4 * SECOND, 0L, 3 * SECOND),
                        ),
                    ),
                ),
        )

    /** Returns a generator that hands out "[prefix]1", "[prefix]2", and so on. */
    private fun ids(prefix: String = "id"): IdGenerator {
        var next = 0
        return IdGenerator { "$prefix${++next}" }
    }

    /** Applies [command] to [start], failing the test if it is rejected. */
    private fun applied(
        command: EditCommand,
        start: Project,
    ): Project = (command.applyTo(start) as CommandOutcome.Applied).project

    /** Music starts on the frame at the playhead, plays in full, and opens a lane below the base, past its end. */
    @Test
    fun musicOpensLaneAtPlayhead() {
        val command = AudioPlacement.proposeMusic(project, 3_010_000L, song, ids())
        assertNotNull(command)
        val placed = applied(command!!, project)
        val lane = placed.tracks.last()
        assertEquals(TrackKind.AUDIO, lane.kind)
        assertEquals(listOf(3 * SECOND to 5 * SECOND), lane.clips.map { it.startMicros to it.durationMicros })
        assertEquals(8 * SECOND, placed.durationMicros)
    }

    /** A free lane is reused, an overlap opens a new lane, and a full set of lanes refuses more music. */
    @Test
    fun musicFillsFirstFreeLaneUpToCap() {
        var current = applied(AudioPlacement.proposeMusic(project, 0L, song, ids())!!, project)
        val reused = AudioPlacement.proposeMusic(current, 5 * SECOND, song, ids())
        assertEquals(current.tracks.last().id, reused?.trackId)
        val generator = ids("more")
        repeat(AudioPlacement.MAX_AUDIO_LANES - 1) {
            current = applied(AudioPlacement.proposeMusic(current, SECOND, song, generator)!!, current)
        }
        assertEquals(AudioPlacement.MAX_AUDIO_LANES, current.tracks.count { it.kind == TrackKind.AUDIO })
        assertNull(AudioPlacement.proposeMusic(current, 2 * SECOND, song, ids()))
        assertNull(AudioPlacement.proposeMusic(project, 0L, video, ids()))
    }

    /** Extraction copies the clip's source range and fades, attaches to it, and can happen only once. */
    @Test
    fun extractAttachesOnceAndSkipsPhotos() {
        val command = AudioPlacement.proposeExtract(project, "A", ids())!!
        val expected = Clip("id2", "video", 0L, 2 * SECOND, 4 * SECOND, ClipAnchor("A", 0L), fadeInMicros = SECOND)
        assertEquals(expected, command.clip)
        val extracted = applied(command, project)
        assertFalse(AudioPlacement.canExtract(extracted, extracted.tracks.first().clips.first()))
        assertNull(AudioPlacement.proposeExtract(extracted, "A", ids()))
        assertNull(AudioPlacement.proposeExtract(project, "P", ids()))
        assertNull(AudioPlacement.proposeExtract(project, "missing", ids()))
    }
}

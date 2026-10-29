/*
 * Unit tests for where new text clips go: at the playhead, attached to the base clip, on a free text lane.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies text placement against base clips A [0, 6 s) and B [6 s, 8 s). */
class TextPlacementTest {
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

    private val project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video),
            tracks =
                listOf(
                    Track(
                        id = "base",
                        kind = TrackKind.VIDEO,
                        magnetic = true,
                        clips =
                            listOf(
                                Clip("A", "video", 0L, 0L, 6 * SECOND),
                                Clip("B", "video", 6 * SECOND, 0L, 2 * SECOND),
                            ),
                    ),
                ),
        )

    private val hello = TextContent("Hello")

    /** Returns ids [prefix]1, [prefix]2, and so on. */
    private fun ids(prefix: String = "id"): IdGenerator {
        var next = 0
        return IdGenerator { "$prefix${++next}" }
    }

    /** Returns [start] with [command] applied, failing if it is rejected. */
    private fun applied(
        command: EditCommand,
        start: Project,
    ): Project = (command.applyTo(start) as CommandOutcome.Applied).project

    /** New text opens a lane above the base, lasts three seconds, and attaches to the clip under the playhead. */
    @Test
    fun textOpensLaneAtPlayhead() {
        val command = TextPlacement.propose(project, SECOND + 10_000L, hello, ids())
        assertNotNull(command)
        assertEquals(SECOND, command!!.clip.startMicros)
        assertEquals(TextPlacement.DEFAULT_TEXT_DURATION_MICROS, command.clip.durationMicros)
        assertEquals(ClipAnchor("A", SECOND), command.clip.anchor)
        val placed = applied(command, project)
        assertEquals(listOf(TrackKind.TEXT, TrackKind.VIDEO), placed.tracks.map { it.kind })
    }

    /** Text stops at the end of its base clip. */
    @Test
    fun textEndsWithItsBaseClip() {
        val command = TextPlacement.propose(project, 7 * SECOND, hello, ids())
        assertEquals(ClipAnchor("B", SECOND), command?.clip?.anchor)
        assertEquals(SECOND, command?.clip?.durationMicros)
    }

    /** Overlapping text goes on a new lane after the other text lanes, up to the lane limit. */
    @Test
    fun textFillsFreeLanesUpToCap() {
        var current = applied(TextPlacement.propose(project, 0L, hello, ids())!!, project)
        val later = TextPlacement.propose(current, 4 * SECOND, hello, ids("later"))
        assertEquals(current.tracks.first().id, later?.trackId)
        val generator = ids("more")
        repeat(TextPlacement.MAX_TEXT_LANES - 1) {
            current = applied(TextPlacement.propose(current, 0L, hello, generator)!!, current)
        }
        val kinds = current.tracks.map { it.kind }
        assertEquals(TrackKind.VIDEO, kinds.last())
        assertEquals(TextPlacement.MAX_TEXT_LANES, kinds.count { it == TrackKind.TEXT })
        assertNull(TextPlacement.propose(current, SECOND, hello, ids("full")))
        assertFalse(TextPlacement.canPlace(current, SECOND))
        assertTrue(TextPlacement.canPlace(current, 4 * SECOND))
    }

    /** Nothing is proposed without a base clip to attach to. */
    @Test
    fun noTextWithoutBase() {
        val empty = project.copy(tracks = emptyList())
        assertNull(TextPlacement.propose(empty, 0L, hello, ids()))
        assertFalse(TextPlacement.canPlace(empty, 0L))
    }
}

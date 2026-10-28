/*
 * Unit tests for edit commands: results, inverses, rejections, and JSON round-trips.
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Verifies every command against magnetic and free tracks. */
class EditCommandTest {
    private val json = Json { classDiscriminator = "type" }

    private val asset =
        MediaAsset(
            id = "asset",
            uri = "content://media/asset",
            kind = MediaKind.VIDEO,
            durationMicros = 10_000_000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    /** Returns a one-second clip [id] starting at [startSeconds]. */
    private fun clip(
        id: String,
        startSeconds: Long,
    ) = Clip(
        id = id,
        assetId = "asset",
        startMicros = startSeconds * 1_000_000L,
        trimStartMicros = 0L,
        durationMicros = 1_000_000L,
    )

    /** Returns a project with one track holding [clips]. */
    private fun project(
        magnetic: Boolean,
        vararg clips: Clip,
    ) = Project(
        id = "project",
        name = "Trip",
        frameRate = FrameRate.FPS_30,
        assets = listOf(asset),
        tracks = listOf(Track("track", TrackKind.VIDEO, magnetic, clips.toList())),
    )

    /** Applies [command], failing the test if it is rejected. */
    private fun applied(
        command: EditCommand,
        project: Project,
    ): CommandOutcome.Applied {
        val outcome = command.applyTo(project)
        assertTrue(outcome is CommandOutcome.Applied, "Expected success, got $outcome")
        return outcome as CommandOutcome.Applied
    }

    /** Returns the clips on the single track of [project]. */
    private fun clipsOf(project: Project): List<Clip> = project.tracks.single().clips

    /** Asserts that applying [command] then its inverse restores [project]. */
    private fun assertRoundTrips(
        command: EditCommand,
        project: Project,
    ) {
        val forward = applied(command, project)
        assertEquals(project, applied(forward.inverse, forward.project).project)
    }

    /** Rename trims the name and its inverse restores the previous name. */
    @Test
    fun renameTrimsAndInverts() {
        val start = project(magnetic = true, clip("a", 0))
        val result = applied(RenameProject("  Beach  "), start)
        assertEquals("Beach", result.project.name)
        assertEquals(RenameProject("Trip"), result.inverse)
        assertRoundTrips(RenameProject("Beach"), start)
    }

    /** Blank names are rejected. */
    @Test
    fun renameRejectsBlank() {
        val outcome = RenameProject("   ").applyTo(project(magnetic = true, clip("a", 0)))
        assertEquals(CommandOutcome.Rejected(CommandRejection.BlankName), outcome)
    }

    /** Deleting on a magnetic track closes the gap; undoing restores the original. */
    @Test
    fun deleteClosesGapOnMagneticTrack() {
        val start = project(magnetic = true, clip("a", 0), clip("b", 1), clip("c", 2))
        val result = applied(DeleteClip("b"), start)
        assertEquals(listOf(0L, 1_000_000L), clipsOf(result.project).map { it.startMicros })
        assertRoundTrips(DeleteClip("b"), start)
    }

    /** Deleting on a free track leaves an implicit gap. */
    @Test
    fun deleteLeavesGapOnFreeTrack() {
        val start = project(magnetic = false, clip("a", 0), clip("b", 1), clip("c", 2))
        val result = applied(DeleteClip("b"), start)
        assertEquals(listOf(0L, 2_000_000L), clipsOf(result.project).map { it.startMicros })
        assertRoundTrips(DeleteClip("b"), start)
    }

    /** Deleting the only clip leaves a valid empty track. */
    @Test
    fun deleteLastClip() {
        val start = project(magnetic = true, clip("a", 0))
        assertTrue(clipsOf(applied(DeleteClip("a"), start).project).isEmpty())
        assertRoundTrips(DeleteClip("a"), start)
    }

    /** Unknown ids are rejected without changing anything. */
    @Test
    fun rejectsUnknownIds() {
        val start = project(magnetic = true, clip("a", 0))
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.ClipNotFound("missing")),
            DeleteClip("missing").applyTo(start),
        )
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.TrackNotFound("missing")),
            AddClip("missing", clip("b", 1)).applyTo(start),
        )
    }

    /** Adding on a magnetic track pushes later clips right. */
    @Test
    fun addShiftsFollowersOnMagneticTrack() {
        val start = project(magnetic = true, clip("a", 0), clip("c", 1))
        val result = applied(AddClip("track", clip("b", 1)), start)
        assertEquals(listOf("a", "b", "c"), clipsOf(result.project).map { it.id })
        assertEquals(2_000_000L, clipsOf(result.project).last().startMicros)
        assertRoundTrips(AddClip("track", clip("b", 1)), start)
    }

    /** Adding an overlapping clip on a free track is rejected as a model invariant. */
    @Test
    fun addRejectsOverlapOnFreeTrack() {
        val outcome = AddClip("track", clip("b", 0)).applyTo(project(magnetic = false, clip("a", 0)))
        assertTrue((outcome as CommandOutcome.Rejected).reason is CommandRejection.ModelInvariant)
    }

    /** Every command survives a JSON round-trip through the sealed hierarchy. */
    @Test
    fun commandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                RenameProject("Beach"),
                DeleteClip("a"),
                AddClip("track", clip("a", 0)),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
        assertTrue(json.encodeToString(EditCommand.serializer(), DeleteClip("a")).contains("\"type\":\"deleteClip\""))
    }

    /** Unknown command types fail to decode instead of being misread. */
    @Test
    fun unknownCommandTypeFails() {
        assertThrows<SerializationException> {
            json.decodeFromString(EditCommand.serializer(), """{"type":"teleportClip","clipId":"a"}""")
        }
    }
}

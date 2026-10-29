/*
 * Unit tests for anchored overlays: how base edits ripple to anchored clips, the overlay commands, and their JSON.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/**
 * Verifies overlay anchoring against a fixed project: base track A [0, 6 s) and B [6 s, 10 s); overlay lane with O1
 * anchored to A at +1 s for 2 s and O2 anchored to B at +0.5 s for 1 s; a second lane with O3 anchored to O1 at
 * +0.25 s for 1 s.
 */
class OverlayCommandsTest {
    private val json = Json { classDiscriminator = "type" }

    private val video = asset("video")
    private val pip = asset("pip")

    /** Returns a ten-second video asset with [id]. */
    private fun asset(id: String) =
        MediaAsset(
            id = id,
            uri = "content://media/$id",
            kind = MediaKind.VIDEO,
            durationMicros = 10 * SECOND,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    /** Returns overlay clip [id] of the pip asset, anchored to [parent] at [offset], starting at [start]. */
    private fun overlay(
        id: String,
        parent: String,
        offset: Long,
        start: Long,
        duration: Long,
    ) = Clip(id, "pip", start, 0L, duration, ClipAnchor(parent, offset), OverlayTransform.DEFAULT)

    /** Returns the fixture project; [withO2] false leaves O2 out and [extra] adds clips to the first overlay lane. */
    private fun fixture(
        withO2: Boolean = true,
        extra: List<Clip> = emptyList(),
    ): Project {
        val base =
            listOf(
                Clip("A", "video", 0L, 0L, 6 * SECOND),
                Clip("B", "video", 6 * SECOND, 0L, 4 * SECOND),
            )
        val lane =
            listOfNotNull(
                overlay("O1", "A", SECOND, SECOND, 2 * SECOND),
                overlay("O2", "B", SECOND / 2, 6_500_000L, SECOND).takeIf { withO2 },
            ) + extra
        val chained = overlay("O3", "O1", SECOND / 4, 1_250_000L, SECOND)
        return Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, pip),
            tracks =
                listOf(
                    Track("T0", TrackKind.VIDEO, true, base),
                    Track("T1", TrackKind.OVERLAY, false, lane.sortedBy { it.startMicros }),
                    Track("T2", TrackKind.OVERLAY, false, listOf(chained)),
                ),
        )
    }

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

    /** Returns clip [id] in [project]. */
    private fun clip(
        project: Project,
        id: String,
    ): Clip = project.tracks.flatMap { it.clips }.single { it.id == id }

    /** Shortening A pulls B left; B's overlay follows and A's overlay stays. */
    @Test
    fun trimBaseEndRipplesFollowerChildren() {
        val result = applied(TrimClip("A", 0L, 0L, 4 * SECOND), fixture())
        assertEquals(4 * SECOND, clip(result, "B").startMicros)
        assertEquals(SECOND, clip(result, "O1").startMicros)
        assertEquals(4_500_000L, clip(result, "O2").startMicros)
    }

    /** Lengthening B leaves its overlay where it was. */
    @Test
    fun trimParentEndKeepsChild() {
        val result = applied(TrimClip("B", 6 * SECOND, 0L, 6 * SECOND), fixture())
        assertEquals(6_500_000L, clip(result, "O2").startMicros)
        assertEquals(12 * SECOND, clip(result, "B").endMicros)
    }

    /** Moving B to the front carries both overlays and the chained overlay with their parents. */
    @Test
    fun reorderBaseCarriesChildren() {
        val result = applied(MoveClip("B", 0L), fixture())
        assertEquals(listOf("B", "A"), result.tracks[0].clips.map { it.id })
        assertEquals(SECOND / 2, clip(result, "O2").startMicros)
        assertEquals(5 * SECOND, clip(result, "O1").startMicros)
        assertEquals(5_250_000L, clip(result, "O3").startMicros)
    }

    /** Overlays after the cut move their anchor to the right part without moving; earlier ones stay on the left. */
    @Test
    fun splitRetargetsLaterChildren() {
        val start = fixture(extra = listOf(overlay("O4", "A", 4 * SECOND, 4 * SECOND, SECOND / 2)))
        val result = applied(SplitClip("A", 2_500_000L, "R"), start)
        assertEquals(ClipAnchor("A", SECOND), clip(result, "O1").anchor)
        assertEquals(ClipAnchor("R", 1_500_000L), clip(result, "O4").anchor)
        assertEquals(4 * SECOND, clip(result, "O4").startMicros)
    }

    /** An overlay starting exactly at the cut follows the right part at offset zero. */
    @Test
    fun splitAtChildStartMovesItRight() {
        val result = applied(SplitClip("A", SECOND, "R"), fixture())
        assertEquals(ClipAnchor("R", 0L), clip(result, "O1").anchor)
        assertEquals(SECOND, clip(result, "O1").startMicros)
    }

    /** Deleting a parent detaches its overlay in place; grandchildren keep following; undo re-attaches. */
    @Test
    fun deleteParentOrphansChildren() {
        val result = applied(DeleteClip("A"), fixture(withO2 = false))
        assertEquals(listOf(0L), result.tracks[0].clips.map { it.startMicros })
        assertNull(clip(result, "O1").anchor)
        assertEquals(SECOND, clip(result, "O1").startMicros)
        assertEquals(1_250_000L, clip(result, "O3").startMicros)
    }

    /** A base edit that would make overlays collide on their lane is rejected. */
    @Test
    fun deleteThatCollidesOverlaysIsRejected() {
        assertTrue(rejection(DeleteClip("A"), fixture()) is CommandRejection.ModelInvariant)
    }

    /** Moving an overlay onto another parent places it at that parent plus the offset; its child follows. */
    @Test
    fun moveOverlayReanchors() {
        val result = applied(MoveOverlay("O1", ClipAnchor("B", 1_500_000L), 7_500_000L), fixture())
        assertEquals(7_500_000L, clip(result, "O1").startMicros)
        assertEquals(7_750_000L, clip(result, "O3").startMicros)
    }

    /** Detaching while moving places the overlay at the given start. */
    @Test
    fun moveOverlayDetached() {
        val result = applied(MoveOverlay("O2", null, 8 * SECOND), fixture())
        assertNull(clip(result, "O2").anchor)
        assertEquals(8 * SECOND, clip(result, "O2").startMicros)
    }

    /** Anchoring an overlay to its own descendant is rejected as a cycle. */
    @Test
    fun anchorCycleIsRejected() {
        val reason = rejection(MoveOverlay("O1", ClipAnchor("O3", 0L), 1_250_000L), fixture())
        assertTrue(reason is CommandRejection.ModelInvariant)
        assertTrue((reason as CommandRejection.ModelInvariant).detail.contains("cycle"))
    }

    /** Trimming an overlay's start moves its anchor offset, and its child follows. */
    @Test
    fun trimAnchoredStartMovesOffset() {
        val result = applied(TrimClip("O1", 1_500_000L, SECOND / 2, 1_500_000L), fixture())
        assertEquals(ClipAnchor("A", 1_500_000L), clip(result, "O1").anchor)
        assertEquals(1_750_000L, clip(result, "O3").startMicros)
    }

    /** Re-anchoring keeps the overlay in place and derives the new offset. */
    @Test
    fun reanchorKeepsPosition() {
        val result = applied(ReanchorOverlay("O2", "A"), fixture())
        assertEquals(ClipAnchor("A", 6_500_000L), clip(result, "O2").anchor)
        assertEquals(6_500_000L, clip(result, "O2").startMicros)
        val detached = applied(ReanchorOverlay("O2", null), fixture())
        assertNull(clip(detached, "O2").anchor)
    }

    /** Placement changes round-trip; out-of-range placements cannot be built. */
    @Test
    fun transformRoundTripsAndValidates() {
        val moved =
            OverlayTransform(centerX = 0.5f, centerY = -0.25f, scale = 0.6f, rotationDegrees = 15f, opacity = 0.8f)
        val result = applied(SetOverlayTransform("O1", moved), fixture())
        assertEquals(moved, clip(result, "O1").overlayTransform)
        assertTrue(rejection(SetOverlayTransform("A", moved), fixture()) is CommandRejection.ModelInvariant)
        assertThrows<IllegalArgumentException> { OverlayTransform(scale = 0f) }
        assertThrows<IllegalArgumentException> { OverlayTransform(opacity = 1.5f) }
        assertThrows<IllegalArgumentException> { OverlayTransform(centerX = Float.NaN) }
    }

    /** Adding the first overlay creates the lane and asset; undo removes all three. */
    @Test
    fun addOverlayCreatesLaneAndAsset() {
        val baseClip = Clip("A", "video", 0L, 0L, 6 * SECOND)
        val base =
            Project(
                id = "project",
                name = "Trip",
                frameRate = FrameRate.FPS_30,
                assets = listOf(video),
                tracks = listOf(Track("T0", TrackKind.VIDEO, true, listOf(baseClip))),
            )
        val added = overlay("O1", "A", SECOND, SECOND, 2 * SECOND)
        val result = applied(AddOverlayClip("lane", added, pip), base)
        assertEquals(listOf(TrackKind.OVERLAY, TrackKind.VIDEO), result.tracks.map { it.kind })
        assertEquals(listOf("video", "pip"), result.assets.map { it.id })
    }

    /** Removing an overlay keeps a shared asset and detaches its children; undo restores everything. */
    @Test
    fun removeOverlayKeepsSharedAsset() {
        val result = applied(RemoveOverlayClip("O1"), fixture())
        assertEquals(listOf("video", "pip"), result.assets.map { it.id })
        assertNull(clip(result, "O3").anchor)
        assertTrue(rejection(RemoveOverlayClip("A"), fixture()) is CommandRejection.ModelInvariant)
    }

    /** Removing the last clip of a lane drops the lane; undo puts it back at its old position. */
    @Test
    fun removeLastClipRestoresLanePosition() {
        val result = applied(RemoveOverlayClip("O3"), fixture())
        assertEquals(listOf("T0", "T1"), result.tracks.map { it.id })
    }

    /** Every overlay command and an anchored clip survive a JSON round-trip; schema 1 clips decode without anchors. */
    @Test
    fun jsonRoundTrips() {
        val restorations = listOf(AnchorRestoration("O3", ClipAnchor("O1", 1L)))
        val commands: List<EditCommand> =
            listOf(
                AddOverlayClip("T1", clip(fixture(), "O1"), pip, restorations),
                RemoveOverlayClip("O1"),
                MoveOverlay("O1", ClipAnchor("B", 2L), 3L),
                MoveOverlay("O1", null, 3L),
                SetOverlayTransform("O1", OverlayTransform.DEFAULT),
                ReanchorOverlay("O1", null),
                AddClip("T0", Clip("A", "video", 0L, 0L, SECOND), restorations),
            )
        commands.forEach { command ->
            val encoded = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), encoded))
        }
        val schemaOne = """{"id":"a","assetId":"v","startMicros":0,"trimStartMicros":0,"durationMicros":1}"""
        val decoded = json.decodeFromString(Clip.serializer(), schemaOne)
        assertNull(decoded.anchor)
        assertNull(decoded.overlayTransform)
    }
}

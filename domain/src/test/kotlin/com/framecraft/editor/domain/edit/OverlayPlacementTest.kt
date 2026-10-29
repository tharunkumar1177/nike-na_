/*
 * Unit tests for where new overlays go and which times an overlay drag snaps to.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.MoveOverlay
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies [OverlayPlacement] and [SnapPoints.ofOverlay] on a base track A [0, 6 s) and B [6 s, 10 s). */
class OverlayPlacementTest {
    private val video = asset("video", MediaKind.VIDEO, 10 * SECOND)
    private val photo = asset("photo", MediaKind.IMAGE, null)

    private val base =
        Track(
            "T0",
            TrackKind.VIDEO,
            true,
            listOf(Clip("A", "video", 0L, 0L, 6 * SECOND), Clip("B", "video", 6 * SECOND, 0L, 4 * SECOND)),
        )

    /** Returns an asset [id] of [kind] lasting [duration]. */
    private fun asset(
        id: String,
        kind: MediaKind,
        duration: Long?,
    ) = MediaAsset(
        id = id,
        uri = "content://media/$id",
        kind = kind,
        durationMicros = duration,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        hasAudio = kind == MediaKind.VIDEO,
        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
    )

    /** Returns a project with [tracks]. */
    private fun project(vararg tracks: Track) =
        Project("project", "Trip", FrameRate.FPS_30, listOf(video), tracks.toList())

    /** Returns ids "id-1", "id-2", and so on. */
    private fun ids(): IdGenerator {
        var count = 0
        return IdGenerator { "id-${++count}" }
    }

    /** A photo at 7 s attaches to B at +1 s, shows for the default three seconds, and creates a lane. */
    @Test
    fun placesAtPlayheadOnParent() {
        val command = requireNotNull(OverlayPlacement.propose(project(base), 7 * SECOND, photo, ids()))
        assertEquals("id-2", command.trackId)
        assertEquals(7 * SECOND, command.clip.startMicros)
        assertEquals(3 * SECOND, command.clip.durationMicros)
        assertEquals(ClipAnchor("B", SECOND), command.clip.anchor)
        assertEquals(OverlayTransform.DEFAULT, command.clip.overlayTransform)
    }

    /** The overlay ends at its parent's end and before the next overlay on the existing lane. */
    @Test
    fun clampsToParentAndNextOverlay() {
        val atEnd = requireNotNull(OverlayPlacement.propose(project(base), 8 * SECOND, video, ids()))
        assertEquals(2 * SECOND, atEnd.clip.durationMicros)
        val next = Clip("O", "video", 3 * SECOND, 0L, SECOND, ClipAnchor("A", 3 * SECOND), OverlayTransform.DEFAULT)
        val lane = Track("lane", TrackKind.OVERLAY, false, listOf(next))
        val before = requireNotNull(OverlayPlacement.propose(project(base, lane), SECOND, photo, ids()))
        assertEquals("lane", before.trackId)
        assertEquals(2 * SECOND, before.clip.durationMicros)
    }

    /** Past the end, the overlay attaches to the last clip and starts one frame before it ends. */
    @Test
    fun pastEndUsesNearestClip() {
        val command = requireNotNull(OverlayPlacement.propose(project(base), 12 * SECOND, photo, ids()))
        assertEquals(ClipAnchor("B", 3_966_666L), command.clip.anchor)
        assertEquals(33_334L, command.clip.durationMicros)
    }

    /** Without base clips there is nothing to attach to. */
    @Test
    fun noBaseClipsGivesNull() {
        val empty = Track("T0", TrackKind.VIDEO, true, emptyList())
        assertNull(OverlayPlacement.propose(project(empty), 0L, photo, ids()))
    }

    /** Dragging lands on a frame, snaps within the threshold, re-attaches to the clip under it, or detaches. */
    @Test
    fun dragReanchorsSnapsAndDetaches() {
        val clip = Clip("O", "video", SECOND, 0L, SECOND, ClipAnchor("A", SECOND), OverlayTransform.DEFAULT)
        val lane = Track("lane", TrackKind.OVERLAY, false, listOf(clip))
        val project = project(lane, base)
        val targets = listOf(0L, 6 * SECOND, 10 * SECOND)
        val free = OverlayPlacement.drag(project, clip, 5_500_000L, targets, 100_000L)
        assertEquals(MoveOverlay("O", ClipAnchor("B", 500_000L), 6_500_000L), free.command)
        assertNull(free.snappedMicros)
        val snapped = OverlayPlacement.drag(project, clip, 4_950_000L, targets, 100_000L)
        assertEquals(MoveOverlay("O", ClipAnchor("B", 0L), 6 * SECOND), snapped.command)
        assertEquals(6 * SECOND, snapped.snappedMicros)
        val endSnap = OverlayPlacement.drag(project, clip, 3_950_000L, targets, 100_000L)
        assertEquals(5 * SECOND, endSnap.command.startMicros)
        val detached = OverlayPlacement.drag(project, clip, 9_500_000L, targets, 100_000L)
        assertEquals(MoveOverlay("O", null, 10_500_000L), detached.command)
    }

    /** Overlay drags snap to the playhead, every base edge, and other overlays' edges. */
    @Test
    fun overlaySnapTargets() {
        val dragged = Clip("O1", "video", SECOND, 0L, SECOND, ClipAnchor("A", SECOND), null)
        val other = Clip("O2", "video", 7 * SECOND, 0L, SECOND, ClipAnchor("B", SECOND), null)
        val lane = Track("lane", TrackKind.OVERLAY, false, listOf(dragged, other))
        val targets = SnapPoints.ofOverlay(base, lane, 2_500_000L, dragged)
        assertEquals(listOf(0L, 2_500_000L, 6 * SECOND, 7 * SECOND, 8 * SECOND, 10 * SECOND), targets)
    }
}

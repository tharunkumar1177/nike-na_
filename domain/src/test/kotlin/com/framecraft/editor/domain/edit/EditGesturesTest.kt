/*
 * Unit tests for trim-drag clamping and snapping, snap targets, and reorder slots.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** One frame at 30 fps. */
private const val FRAME = 33_334L

/** Verifies the pure math behind timeline edit gestures. */
class EditGesturesTest {
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

    /** Returns a video clip [id] at [start] lasting [duration], reading from [trimStart]. */
    private fun clip(
        id: String,
        start: Long,
        duration: Long,
        trimStart: Long = 0L,
    ) = Clip(id = id, assetId = "video", startMicros = start, trimStartMicros = trimStart, durationMicros = duration)

    /** Returns a track holding [clips]. */
    private fun track(
        magnetic: Boolean,
        vararg clips: Clip,
    ) = Track("track", TrackKind.VIDEO, magnetic, clips.toList())

    /** Returns the proposal for dragging the [edge] handle of clip [clipId] on [track] by [delta]. */
    private fun drag(
        track: Track,
        clipId: String,
        edge: TrimEdge,
        delta: Long,
    ): TrimProposal {
        val clip = track.clips.first { it.id == clipId }
        return TrimController.forClip(track, clip, video, FrameRate.FPS_30, edge).propose(delta, emptyList(), 0L)
    }

    /** The end handle stops at the end of the source and at one frame. */
    @Test
    fun endHandleClampsToSourceAndOneFrame() {
        val lane = track(true, clip("a", 0, 2 * SECOND), clip("b", 2 * SECOND, SECOND))
        assertEquals(10 * SECOND, drag(lane, "a", TrimEdge.END, 20 * SECOND).durationMicros)
        assertEquals(FRAME, drag(lane, "a", TrimEdge.END, -5 * SECOND).durationMicros)
    }

    /** On a free track the end handle stops at the next clip. */
    @Test
    fun endHandleStopsAtNextClipOnFreeTrack() {
        val lane = track(false, clip("a", 0, SECOND), clip("b", 2 * SECOND, SECOND))
        assertEquals(2 * SECOND, drag(lane, "a", TrimEdge.END, 5 * SECOND).durationMicros)
    }

    /** On a magnetic track the start handle keeps the start and reveals or hides source. */
    @Test
    fun startHandleOnMagneticTrack() {
        val lane = track(true, clip("a", 0, 2 * SECOND, trimStart = SECOND))
        val revealed = drag(lane, "a", TrimEdge.START, -5 * SECOND)
        assertEquals(TrimProposal(0L, 0L, 3 * SECOND, null), revealed)
        val hidden = drag(lane, "a", TrimEdge.START, SECOND)
        assertEquals(TrimProposal(0L, 2 * SECOND, SECOND, null), hidden)
    }

    /** On a free track the start handle moves the start but not past the previous clip or the source start. */
    @Test
    fun startHandleOnFreeTrack() {
        val lane = track(false, clip("p", 0, 800_000L), clip("a", SECOND, 2 * SECOND, trimStart = SECOND / 2))
        val proposal = drag(lane, "a", TrimEdge.START, -5 * SECOND)
        assertEquals(TrimProposal(800_000L, 300_000L, 2_200_000L, null), proposal)
    }

    /** Without a nearby target the moving edge lands on the nearest frame boundary. */
    @Test
    fun edgeSnapsToFrames() {
        val lane = track(true, clip("a", 0, 2 * SECOND))
        assertEquals(2_500_000L, drag(lane, "a", TrimEdge.END, 510_000L).durationMicros)
    }

    /** A target within the threshold wins over frame snapping and is reported. */
    @Test
    fun edgeSnapsToTargetsWithinThreshold() {
        val lane = track(true, clip("a", 0, 2 * SECOND))
        val controller = TrimController.forClip(lane, lane.clips.single(), video, FrameRate.FPS_30, TrimEdge.END)
        val snapped = controller.propose(510_000L, listOf(2_600_000L), 200_000L)
        assertEquals(2_600_000L, snapped.durationMicros)
        assertEquals(2_600_000L, snapped.snappedMicros)
        assertNull(controller.propose(510_000L, listOf(3 * SECOND), 200_000L).snappedMicros)
    }

    /** A proposal's command applies cleanly to the project. */
    @Test
    fun proposalCommandApplies() {
        val lane = track(true, clip("a", 0, 2 * SECOND), clip("b", 2 * SECOND, SECOND))
        val project = Project("p", "Trip", FrameRate.FPS_30, listOf(video), listOf(lane))
        val controller = TrimController.forClip(lane, lane.clips.first(), video, FrameRate.FPS_30, TrimEdge.START)
        val command = controller.commandFor(controller.propose(SECOND / 2, emptyList(), 0L))
        assertTrue(command.applyTo(project) is CommandOutcome.Applied)
    }

    /** Snap targets skip the dragged clip and, on magnetic tracks, the clips after it. */
    @Test
    fun snapPointsSkipMovingClips() {
        val clips =
            arrayOf(
                clip("a", 0, 2 * SECOND),
                clip("b", 2 * SECOND, 3 * SECOND),
                clip("c", 5 * SECOND, SECOND),
            )
        assertEquals(listOf(0L, 2 * SECOND, 7 * SECOND), SnapPoints.of(track(true, *clips), 7 * SECOND, clips[1]))
        val free = SnapPoints.of(track(false, *clips), 7 * SECOND, clips[1])
        assertEquals(listOf(0L, 2 * SECOND, 5 * SECOND, 6 * SECOND, 7 * SECOND), free)
        assertEquals(2 * SECOND, SnapPoints.nearest(free, 2_100_000L, 200_000L))
        assertNull(SnapPoints.nearest(free, 2_500_000L, 200_000L))
    }

    /** Reorder slots are the boundaries of the track without the dragged clip. */
    @Test
    fun reorderSlots() {
        val lane =
            track(
                true,
                clip("a", 0, 2 * SECOND),
                clip("b", 2 * SECOND, 3 * SECOND),
                clip("c", 5 * SECOND, 4 * SECOND),
            )
        val slots = ReorderSlots.of(lane, "a")
        assertEquals(listOf(0L, 3 * SECOND, 7 * SECOND), slots)
        assertEquals(3 * SECOND, ReorderSlots.nearest(slots, 4 * SECOND))
    }
}

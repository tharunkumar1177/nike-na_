/*
 * Unit tests for the timeline's cut badges: which cuts get one, their state, and which badge a touch reaches.
 */
package com.framecraft.editor.timeline

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/**
 * Verifies badges on a base track of A [0, 4 s) with a dissolve, B [4 s, 8 s), C [8 s, 10 s), D [10 s, 11 s) read
 * from the start of its video, and E after a gap, over an overlay track of touching clips.
 */
class TimelineCutsTest {
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
                        "base",
                        TrackKind.VIDEO,
                        false,
                        listOf(
                            Clip(
                                "A",
                                "video",
                                0L,
                                0L,
                                4 * SECOND,
                                outTransition = ClipTransition(TransitionKind.DISSOLVE, SECOND),
                            ),
                            Clip("B", "video", 4 * SECOND, 5 * SECOND, 4 * SECOND),
                            Clip("C", "video", 8 * SECOND, 1 * SECOND, 2 * SECOND),
                            Clip("D", "video", 10 * SECOND, 0L, SECOND),
                            Clip("E", "video", 12 * SECOND, 0L, SECOND),
                        ),
                    ),
                    Track(
                        "overlay",
                        TrackKind.OVERLAY,
                        false,
                        listOf(
                            Clip("O1", "video", 0L, 0L, SECOND),
                            Clip("O2", "video", SECOND, 0L, SECOND),
                        ),
                    ),
                ),
        )

    /** Only touching video clips make cuts: a dissolve plays, a cut with hidden source is open, one without is not. */
    @Test
    fun cutsFollowTrackKindAndHandles() {
        val cuts = TimelineCuts.of(project)
        assertEquals(listOf("A", "B", "C"), cuts.map { it.left.id })
        assertEquals(listOf("B", "C", "D"), cuts.map { it.right.id })
        assertEquals(listOf(CutState.SET, CutState.NONE, CutState.UNAVAILABLE), cuts.map { it.state })
        assertEquals(listOf(0, 0, 0), cuts.map { it.trackIndex })
    }

    /** A touch reaches the nearest badge on its row only within the tolerance. */
    @Test
    fun nearFindsBadgeWithinTolerance() {
        val cuts = TimelineCuts.of(project)
        assertEquals("A", TimelineCuts.near(cuts, 0, 4_100_000L, 200_000L)?.left?.id)
        assertEquals("B", TimelineCuts.near(cuts, 0, 7_900_000L, 200_000L)?.left?.id)
        assertNull(TimelineCuts.near(cuts, 0, 5 * SECOND, 200_000L))
        assertNull(TimelineCuts.near(cuts, 1, SECOND, 200_000L))
    }
}

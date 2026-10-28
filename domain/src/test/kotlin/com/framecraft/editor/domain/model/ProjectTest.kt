/*
 * Unit tests for project, track, and clip invariants and for building new projects from assets.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Verifies model validation and ProjectFactory placement. */
class ProjectTest {
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    /** Returns a video asset with [durationMicros]. */
    private fun video(
        id: String,
        durationMicros: Long,
    ) = MediaAsset(
        id = id,
        uri = "content://media/$id",
        kind = MediaKind.VIDEO,
        durationMicros = durationMicros,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        hasAudio = true,
        fingerprint = SourceFingerprint(sizeBytes = 1_000L, lastModifiedMillis = null),
    )

    /** Returns an image asset. */
    private fun image(id: String) =
        MediaAsset(
            id = id,
            uri = "content://media/$id",
            kind = MediaKind.IMAGE,
            durationMicros = null,
            width = 1080,
            height = 1920,
            rotationDegrees = 0,
            hasAudio = false,
            fingerprint = SourceFingerprint(sizeBytes = 500L, lastModifiedMillis = null),
        )

    /** The factory places videos and images back to back with the default image duration. */
    @Test
    fun factoryPlacesClipsBackToBack() {
        val project = ProjectFactory.create("Trip", listOf(video("a", 2_000_000L), image("b")), ids)
        val clips = project.tracks.single().clips
        assertEquals(listOf(0L, 2_000_000L), clips.map { it.startMicros })
        assertEquals(ProjectFactory.DEFAULT_IMAGE_DURATION_MICROS, clips[1].durationMicros)
        assertEquals(5_000_000L, project.durationMicros)
        assertEquals(FrameRate.FPS_30, project.frameRate)
    }

    /** Overlapping clips on one track are rejected. */
    @Test
    fun rejectsOverlap() {
        assertThrows<IllegalArgumentException> {
            Track(
                id = "t",
                kind = TrackKind.VIDEO,
                magnetic = false,
                clips = listOf(Clip("c1", "a", 0, 0, 1_000), Clip("c2", "a", 500, 0, 1_000)),
            )
        }
    }

    /** Magnetic tracks reject gaps; non-magnetic tracks allow them. */
    @Test
    fun magneticTracksRejectGaps() {
        val gapped = listOf(Clip("c1", "a", 0, 0, 1_000), Clip("c2", "a", 2_000, 0, 1_000))
        assertThrows<IllegalArgumentException> { Track("t", TrackKind.VIDEO, magnetic = true, clips = gapped) }
        assertEquals(3_000L, Track("t", TrackKind.VIDEO, magnetic = false, clips = gapped).endMicros)
    }

    /** Clips must reference known assets and stay within the source duration. */
    @Test
    fun rejectsInvalidAssetReferences() {
        val track = Track("t", TrackKind.VIDEO, magnetic = true, clips = listOf(Clip("c", "missing", 0, 0, 1_000)))
        assertThrows<IllegalArgumentException> { Project("p", "P", FrameRate.FPS_30, emptyList(), listOf(track)) }
        val tooLong = Track("t", TrackKind.VIDEO, magnetic = true, clips = listOf(Clip("c", "a", 0, 500, 1_000)))
        assertThrows<IllegalArgumentException> {
            Project("p", "P", FrameRate.FPS_30, listOf(video("a", 1_000)), listOf(tooLong))
        }
    }

    /** Duplicate identifiers anywhere in the project are rejected. */
    @Test
    fun rejectsDuplicateIds() {
        assertThrows<IllegalArgumentException> {
            Project("a", "P", FrameRate.FPS_30, listOf(video("a", 1_000)), emptyList())
        }
    }

    /** Rotated assets report their displayed aspect ratio. */
    @Test
    fun displayAspectRatioAppliesRotation() {
        assertEquals(1080f / 1920f, video("a", 1_000).copy(rotationDegrees = 90).displayAspectRatio)
    }
}

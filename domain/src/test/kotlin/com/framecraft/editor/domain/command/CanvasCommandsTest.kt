/*
 * Unit tests for the canvas: its settings and placement rules, the canvas commands and their inverses, and their JSON.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.CanvasAspect
import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.CanvasSettings
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

/** Opaque orange. */
private const val ORANGE = 0xFFFF8800.toInt()

/** Verifies the canvas against a base track of A [0, 4 s) and B [4 s, 8 s) under an overlay O anchored to A. */
class CanvasCommandsTest {
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

    private val fixture =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video),
            tracks =
                listOf(
                    Track(
                        "lane",
                        TrackKind.OVERLAY,
                        false,
                        listOf(Clip("O", "video", 0L, 0L, SECOND, ClipAnchor("A", 0L), OverlayTransform.DEFAULT)),
                    ),
                    Track(
                        "base",
                        TrackKind.VIDEO,
                        true,
                        listOf(Clip("A", "video", 0L, 0L, 4 * SECOND), Clip("B", "video", 4 * SECOND, 0L, 4 * SECOND)),
                    ),
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

    /** Returns base clip [id] of [project]. */
    private fun baseClip(
        project: Project,
        id: String,
    ): Clip =
        project.tracks
            .last()
            .clips
            .single { it.id == id }

    /** A new project has an original-shape canvas on black, and ratios read as width over height. */
    @Test
    fun defaultsKeepTheOriginalShape() {
        assertEquals(CanvasSettings(CanvasAspect.ORIGINAL, CanvasBackground.Black), fixture.canvas)
        assertNull(CanvasAspect.ORIGINAL.ratio)
        assertEquals(9f / 16f, CanvasAspect.PORTRAIT_9_16.ratio)
        assertEquals(1f, CanvasAspect.SQUARE.ratio)
    }

    /** Changing the shape keeps every clip and its inverse restores the previous shape. */
    @Test
    fun aspectChangesAndUndoes() {
        val result = applied(SetCanvasAspect(CanvasAspect.PORTRAIT_9_16))
        assertEquals(CanvasAspect.PORTRAIT_9_16, result.project.canvas.aspect)
        assertEquals(fixture.tracks, result.project.tracks)
        assertEquals(fixture, applied(result.inverse, result.project).project)
    }

    /** Changing the background keeps the shape and its inverse restores the previous background. */
    @Test
    fun backgroundChangesAndUndoes() {
        val portrait = applied(SetCanvasAspect(CanvasAspect.PORTRAIT_4_5)).project
        val result = applied(SetCanvasBackground(CanvasBackground.Solid(ORANGE)), portrait)
        val expected = CanvasSettings(CanvasAspect.PORTRAIT_4_5, CanvasBackground.Solid(ORANGE))
        assertEquals(expected, result.project.canvas)
        assertEquals(portrait, applied(result.inverse, result.project).project)
    }

    /** A base clip takes a placement that later edits keep, and the inverse removes it again. */
    @Test
    fun basePlacementAppliesAndUndoes() {
        val placement = CanvasPlacement(CanvasFit.FILL, centerX = 0.5f, scale = 1.5f, rotationDegrees = 90f)
        val result = applied(SetBaseClipPlacement("A", placement))
        assertEquals(placement, baseClip(result.project, "A").canvasPlacement)
        assertNull(baseClip(result.project, "B").canvasPlacement)
        assertEquals(fixture, applied(result.inverse, result.project).project)
        val split = applied(SplitClip("A", 2 * SECOND, "A2"), result.project).project
        assertEquals(placement, baseClip(split, "A2").canvasPlacement)
    }

    /** Placements are refused off the base track, for unknown clips, and outside their ranges. */
    @Test
    fun rejectsPlacementOffTheBase() {
        val rejected = SetBaseClipPlacement("O", CanvasPlacement()).applyTo(fixture)
        assertTrue((rejected as CommandOutcome.Rejected).reason is CommandRejection.ModelInvariant)
        val missing = SetBaseClipPlacement("Z", CanvasPlacement()).applyTo(fixture)
        assertEquals(CommandRejection.ClipNotFound("Z"), (missing as CommandOutcome.Rejected).reason)
        assertThrows<IllegalArgumentException> { CanvasPlacement(scale = 0f) }
        assertThrows<IllegalArgumentException> { CanvasPlacement(centerY = 3f) }
        assertThrows<IllegalArgumentException> { CanvasBackground.Solid(0x00FF8800) }
    }

    /** Clips placed differently do not merge. */
    @Test
    fun mergeNeedsMatchingPlacements() {
        val split = applied(SplitClip("B", 6 * SECOND, "B2")).project
        val moved = applied(SetBaseClipPlacement("B2", CanvasPlacement(scale = 2f)), split).project
        val outcome = MergeClips("B", "B2").applyTo(moved)
        assertTrue(outcome is CommandOutcome.Rejected)
    }

    /** Every canvas command round-trips through JSON. */
    @Test
    fun canvasCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                SetCanvasAspect(CanvasAspect.LANDSCAPE_4_3),
                SetCanvasBackground(CanvasBackground.Blur),
                SetCanvasBackground(CanvasBackground.Solid(ORANGE)),
                SetBaseClipPlacement("A", CanvasPlacement(CanvasFit.FILL, centerY = -0.25f)),
                SetBaseClipPlacement("A", null),
            )
        commands.forEach { command ->
            val text = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), text))
        }
    }
}

/*
 * Unit tests for auto-captions in the edit model: captions following their speech through trims and splits, the
 * hand-edit locks, retyping, deleting the transcribed clip, replacing captions, and the JSON of the caption commands.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.CaptionContent
import com.framecraft.editor.domain.model.CaptionWord
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.TEXT_ASSET
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TextStyle
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

/** One millisecond in microseconds. */
private const val MS = 1_000L

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/**
 * Verifies captions against base video clip A [0, 6 s) reading its source from zero, with caption C1 "Hello world"
 * (words at 0.5 s and 1.0 s, held to 1.6 s) and caption C2 "Again" (3.0 s, held to 4.0 s) on caption lane L.
 */
class CaptionCommandsTest {
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

    private val first =
        CaptionContent(
            listOf(CaptionWord("Hello", 500 * MS, 900 * MS), CaptionWord("world", SECOND, 1_400 * MS)),
            1_600 * MS,
        )

    private val second = CaptionContent(listOf(CaptionWord("Again", 3 * SECOND, 3_500 * MS)), 4 * SECOND)

    private val captionOne = caption("C1", "Hello world", first, 500 * MS, 1_100 * MS)

    private val captionTwo = caption("C2", "Again", second, 3 * SECOND, SECOND)

    /** Base video clip A [0, 6 s). */
    private val baseClip = Clip("A", "video", 0L, 0L, 6 * SECOND)

    /** Returns caption [id] showing [text] for [content], placed at [startMicros] for [durationMicros] on A. */
    private fun caption(
        id: String,
        text: String,
        content: CaptionContent,
        startMicros: Long,
        durationMicros: Long,
    ): Clip =
        Clip(
            id = id,
            assetId = TEXT_ASSET_ID,
            startMicros = startMicros,
            trimStartMicros = 0L,
            durationMicros = durationMicros,
            anchor = ClipAnchor("A", startMicros),
            overlayTransform = OverlayTransform(),
            text = TextContent(text, caption = content),
        )

    /** Returns the fixture project, with caption lane L holding [captions] when any are given. */
    private fun fixture(vararg captions: Clip): Project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video) + if (captions.isEmpty()) emptyList() else listOf(TEXT_ASSET),
            tracks =
                listOfNotNull(
                    captions.takeIf { it.isNotEmpty() }?.let { Track("L", TrackKind.TEXT, false, it.toList()) },
                    Track("T0", TrackKind.VIDEO, true, listOf(baseClip)),
                ),
        )

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

    /** Returns clip [id] in [project], or null when it is gone. */
    private fun clipOf(
        project: Project,
        id: String,
    ): Clip? = project.tracks.flatMap { it.clips }.firstOrNull { it.id == id }

    /** A caption must sit exactly where its clip plays its words and say exactly those words. */
    @Test
    fun captionsMustMatchTheirSpeech() {
        fixture(captionOne, captionTwo)
        assertThrows<IllegalArgumentException> { fixture(captionOne.copy(startMicros = 400 * MS)) }
        val retold = captionOne.copy(text = TextContent("Hi world", caption = first))
        assertThrows<IllegalArgumentException> { fixture(retold) }
        assertThrows<IllegalArgumentException> { CaptionContent(first.words.reversed(), 1_600 * MS) }
        assertThrows<IllegalArgumentException> { CaptionWord("two words", 0L, 1L) }
    }

    /** Trimming the start of the clip moves each caption with its words and drops the words no longer played. */
    @Test
    fun trimMovesCaptionsWithTheirWords() {
        val trimmed = applied(TrimClip("A", 0L, 950 * MS, 5_050 * MS), fixture(captionOne, captionTwo))
        val one = clipOf(trimmed, "C1")
        assertEquals(50 * MS, one?.startMicros)
        assertEquals(600 * MS, one?.durationMicros)
        assertEquals("world", one?.text?.text)
        assertEquals(2_050 * MS, clipOf(trimmed, "C2")?.startMicros)
    }

    /** Splitting the clip hands each caption to the part that plays its first word. */
    @Test
    fun splitHandsCaptionsToTheirPart() {
        val split = applied(SplitClip("A", 2 * SECOND, "B"), fixture(captionOne, captionTwo))
        assertEquals("A", clipOf(split, "C1")?.anchor?.parentClipId)
        assertEquals(ClipAnchor("B", SECOND), clipOf(split, "C2")?.anchor)
        assertEquals(3 * SECOND, clipOf(split, "C2")?.startMicros)
    }

    /** Captions cannot be trimmed, split, moved, or re-anchored by hand. */
    @Test
    fun captionTimingIsLocked() {
        val start = fixture(captionOne, captionTwo)
        val locked = CommandOutcome.Rejected(CommandRejection.CaptionLocked("C1"))
        assertEquals(locked, TrimClip("C1", 500 * MS, 0L, 500 * MS).applyTo(start))
        assertEquals(locked, SplitClip("C1", SECOND, "X").applyTo(start))
        assertEquals(locked, MoveClip("C1", 2 * SECOND).applyTo(start))
        assertEquals(locked, MoveOverlay("C1", null, 2 * SECOND).applyTo(start))
        assertEquals(locked, ReanchorOverlay("C1", null).applyTo(start))
    }

    /** Restyling keeps a caption's speech; retyping its words turns it into plain text. */
    @Test
    fun retypingTurnsACaptionIntoText() {
        val start = fixture(captionOne, captionTwo)
        val styled = captionOne.text?.copy(style = TextStyle(highlightArgb = 0x7FFF0000))
        assertEquals(first, clipOf(applied(SetTextContent("C1", requireNotNull(styled)), start), "C1")?.text?.caption)
        val retyped = applied(SetTextContent("C1", requireNotNull(captionOne.text).copy(text = "Hi")), start)
        assertNull(clipOf(retyped, "C1")?.text?.caption)
        assertEquals("Hi", clipOf(retyped, "C1")?.text?.text)
    }

    /** Deleting the transcribed clip leaves its captions where they stand, detached. */
    @Test
    fun deletingTheClipDetachesItsCaptions() {
        val deleted = applied(DeleteClip("A"), fixture(captionOne, captionTwo))
        assertNull(clipOf(deleted, "C1")?.anchor)
        assertEquals(500 * MS, clipOf(deleted, "C1")?.startMicros)
    }

    /** Replacing captions builds their lane and the text asset, and removing them all takes both away again. */
    @Test
    fun replaceCaptionsAddsAndRemovesLanes() {
        val empty = fixture()
        val added = applied(ReplaceCaptions(listOf(CaptionLaneChange("L", 0, emptyList(), listOf(captionOne)))), empty)
        assertEquals(listOf("L", "T0"), added.tracks.map { it.id })
        assertTrue(added.assets.contains(TEXT_ASSET))
        val swap = CaptionLaneChange("L", 0, listOf("C1"), listOf(captionTwo))
        val swapped = applied(ReplaceCaptions(listOf(swap)), added).tracks.first()
        assertEquals(listOf("C2"), swapped.clips.map { it.id })
        val removed = applied(ReplaceCaptions(listOf(CaptionLaneChange("L", 0, listOf("C1"), emptyList()))), added)
        assertEquals(empty, removed)
        val plain = captionOne.copy(id = "P", text = TextContent("Plain"))
        val notCaption = ReplaceCaptions(listOf(CaptionLaneChange("L", 0, emptyList(), listOf(plain))))
        assertFalse(notCaption.applyTo(empty) is CommandOutcome.Applied)
    }

    /** The caption commands and captioned clips survive a JSON round-trip. */
    @Test
    fun captionCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                ReplaceCaptions(listOf(CaptionLaneChange("L", 0, listOf("C0"), listOf(captionOne)))),
                TrimClip("A", 0L, SECOND, 5 * SECOND, restoredCaptions = listOf(LaneClip("L", captionTwo))),
            )
        commands.forEach { command ->
            val encoded = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), encoded))
        }
        val project = fixture(captionOne, captionTwo)
        val encoded = json.encodeToString(Project.serializer(), project)
        assertEquals(project, json.decodeFromString(Project.serializer(), encoded))
    }
}

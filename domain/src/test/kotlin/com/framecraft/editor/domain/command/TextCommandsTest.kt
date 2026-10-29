/*
 * Unit tests for text editing: adding and removing text clips, changing their content, the animation rules of trim,
 * split, and merge, the text model rules, and the JSON of the text commands.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.TEXT_ASSET
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.TextAnimation
import com.framecraft.editor.domain.model.TextAnimationKind
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TextOutline
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

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies text editing against a base track with video clip A [0, 6 s) and text clip T [1 s, 4 s) attached to A. */
class TextCommandsTest {
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

    private val content =
        TextContent(
            text = "Hello",
            animationIn = TextAnimation(TextAnimationKind.FADE, SECOND),
            animationOut = TextAnimation(TextAnimationKind.POP, SECOND),
        )

    /** Text clip T [1 s, 4 s), attached to A one second in. */
    private val title =
        Clip(
            id = "T",
            assetId = TEXT_ASSET_ID,
            startMicros = SECOND,
            trimStartMicros = 0L,
            durationMicros = 3 * SECOND,
            anchor = ClipAnchor("A", SECOND),
            overlayTransform = OverlayTransform(),
            text = content,
        )

    /** Base video clip A [0, 6 s). */
    private val baseClip = Clip("A", "video", 0L, 0L, 6 * SECOND)

    /** Returns the fixture project with [text] lanes above the base track. */
    private fun fixture(vararg text: Track): Project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video) + if (text.isEmpty()) emptyList() else listOf(TEXT_ASSET),
            tracks = text.toList() + Track("T0", TrackKind.VIDEO, true, listOf(baseClip)),
        )

    /** Returns a text lane [id] holding [clips]. */
    private fun lane(
        id: String,
        vararg clips: Clip,
    ) = Track(id, TrackKind.TEXT, false, clips.toList())

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

    /** Returns true when [command] is rejected on [project]. */
    private fun rejected(
        command: EditCommand,
        project: Project,
    ): Boolean = command.applyTo(project) is CommandOutcome.Rejected

    /** Returns the text of clip [id] in [project]. */
    private fun textOf(
        project: Project,
        id: String,
    ): TextContent? =
        project.tracks
            .flatMap { it.clips }
            .single { it.id == id }
            .text

    /** Returns text clip T in [project]. */
    private fun textClip(project: Project): Clip = project.tracks.flatMap { it.clips }.single { it.id == "T" }

    /** Adding text creates its lane first in the list and the shared asset; removing the last one drops both. */
    @Test
    fun addAndRemoveTextLaneAndAsset() {
        val added = applied(AddTextClip("L", title), fixture())
        assertEquals(listOf("L", "T0"), added.tracks.map { it.id })
        assertTrue(added.assets.contains(TEXT_ASSET))
        val second = title.copy(id = "T2", startMicros = 4 * SECOND, anchor = ClipAnchor("A", 4 * SECOND))
        val both = applied(AddTextClip("L", second), added)
        val removed = applied(RemoveTextClip("T"), both)
        assertTrue(removed.assets.contains(TEXT_ASSET))
        val empty = applied(RemoveTextClip("T2"), removed)
        assertEquals(fixture(), empty)
        assertTrue(rejected(RemoveTextClip("A"), both))
    }

    /** New content replaces the old in one undoable step, and animations longer than the clip are shortened. */
    @Test
    fun setTextContentChangesAndRestores() {
        val start = fixture(lane("L", title))
        val styled =
            TextContent(
                text = "Bye",
                style = TextStyle(outline = TextOutline()),
                animationIn = TextAnimation(TextAnimationKind.TYPEWRITER, 5 * SECOND),
            )
        val changed = textOf(applied(SetTextContent("T", styled), start), "T")
        assertEquals("Bye", changed?.text)
        assertEquals(3 * SECOND, changed?.animationIn?.durationMicros)
        assertNull(changed?.animationOut)
        assertTrue(rejected(SetTextContent("A", styled), start))
        assertThrows<IllegalArgumentException> { TextContent(" ") }
    }

    /** Trimming shortens each animation on its own and the inverse restores the originals exactly. */
    @Test
    fun trimClampsAnimationsWithExactInverse() {
        val start = fixture(lane("L", title))
        val trimmed = textOf(applied(TrimClip("T", SECOND, 0L, SECOND / 2), start), "T")
        assertEquals(SECOND / 2, trimmed?.animationIn?.durationMicros)
        assertEquals(SECOND / 2, trimmed?.animationOut?.durationMicros)
        assertTrue(rejected(TrimClip("T", SECOND, SECOND, 2 * SECOND), start))
    }

    /** Splitting keeps the entrance on the left and the exit on the right; merging joins them back. */
    @Test
    fun splitDividesAnimationsAndMergeJoins() {
        val start = fixture(lane("L", title))
        val split = applied(SplitClip("T", 2 * SECOND, "R"), start)
        assertEquals(content.animationIn, textOf(split, "T")?.animationIn)
        assertNull(textOf(split, "T")?.animationOut)
        assertNull(textOf(split, "R")?.animationIn)
        assertEquals(content.animationOut, textOf(split, "R")?.animationOut)
        val edited = applied(SetTextContent("R", content.copy(text = "Other", animationIn = null)), split)
        assertTrue(rejected(MergeClips("T", "R"), edited))
    }

    /** Text sits only on text lanes, has no sound or source offset, and is placed like an overlay. */
    @Test
    fun textModelRules() {
        assertThrows<IllegalArgumentException> { fixture(Track("O", TrackKind.OVERLAY, false, listOf(title))) }
        assertThrows<IllegalArgumentException> { fixture(lane("L", title.copy(text = null))) }
        assertThrows<IllegalArgumentException> { fixture(lane("L", title.copy(volumePercent = 50))) }
        assertThrows<IllegalArgumentException> { title.copy(durationMicros = SECOND / 2) }
        val start = fixture(lane("L", title))
        assertTrue(rejected(SetClipAudio("T", 50, 0L, 0L), start))
        val moved = applied(SetOverlayTransform("T", OverlayTransform(centerY = 0.5f)), start)
        assertEquals(0.5f, textClip(moved).overlayTransform?.centerY)
        assertFalse(TEXT_ASSET.isTimed)
    }

    /** The text commands and the text fields of trim and merge survive a JSON round-trip. */
    @Test
    fun textCommandsRoundTripThroughJson() {
        val commands: List<EditCommand> =
            listOf(
                AddTextClip("L", title, trackIndex = 1),
                RemoveTextClip("T"),
                SetTextContent("T", content.copy(style = TextStyle(backgroundArgb = 0x66000000))),
                TrimClip("T", SECOND, 0L, SECOND, animationInMicros = SECOND, animationOutMicros = null),
                MergeClips("T", "R", animationIn = content.animationIn, animationOut = content.animationOut),
            )
        commands.forEach { command ->
            val encoded = json.encodeToString(EditCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(EditCommand.serializer(), encoded))
        }
    }
}

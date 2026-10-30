/*
 * Unit tests for caption planning: how transcripts break into captions and how captions are planned onto lanes.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.CaptionWord
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One millisecond in microseconds. */
private const val MS = 1_000L

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Verifies caption chunking and planning against base video clip A [0, 6 s) and a muted voiceover V [1 s, 4 s). */
class CaptionPlanningTest {
    private var issued = 0

    private val ids = IdGenerator { "id${++issued}" }

    private val project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets = listOf(asset("video", MediaKind.VIDEO), asset("voice", MediaKind.AUDIO)),
            tracks =
                listOf(
                    Track("T0", TrackKind.VIDEO, true, listOf(Clip("A", "video", 0L, 0L, 6 * SECOND))),
                    Track(
                        "VO",
                        TrackKind.AUDIO,
                        false,
                        listOf(Clip("V", "voice", SECOND, 0L, 3 * SECOND, volumePercent = 0, voiceover = true)),
                    ),
                ),
        )

    /** Returns a ten-second asset [id] of [kind] with sound. */
    private fun asset(
        id: String,
        kind: MediaKind,
    ) = MediaAsset(
        id = id,
        uri = "content://media/$id",
        kind = kind,
        durationMicros = 10 * SECOND,
        width = if (kind == MediaKind.VIDEO) 1920 else 1,
        height = if (kind == MediaKind.VIDEO) 1080 else 1,
        rotationDegrees = 0,
        hasAudio = true,
        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
    )

    /** Returns [count] words a quarter second apart from [fromMicros], each lasting 200 ms. */
    private fun words(
        count: Int,
        fromMicros: Long = 0L,
    ): List<CaptionWord> =
        (0 until count).map { CaptionWord("w$it", fromMicros + it * 250 * MS, fromMicros + it * 250 * MS + 200 * MS) }

    /** Returns the ids of the clips [scope] covers, with [selected] selected and muted clips when [muted]. */
    private fun sourceIds(
        scope: CaptionScope,
        selected: String? = null,
        muted: Boolean = false,
    ): List<String> = CaptionPlanner.sources(project, scope, selected, muted).map { it.clip.id }

    /** Returns [project] after [command], which must apply. */
    private fun appliedTo(
        project: Project,
        command: EditCommand,
    ): Project = (command.applyTo(project) as CommandOutcome.Applied).project

    /** A caption holds at most six words, breaks at a pause, and is held at least a second. */
    @Test
    fun chunksBreakOnLengthAndPauses() {
        val chunks = CaptionChunker.chunk(words(8) + words(2, 5 * SECOND))
        assertEquals(listOf(6, 2, 2), chunks.map { it.words.size })
        assertEquals("W6", chunks[1].words.first().text)
        assertEquals(2_500 * MS, chunks[1].sourceEndMicros)
    }

    /** Captions start with a capital, capitalize "i", drop noise, and never run into the next caption. */
    @Test
    fun chunksAreCasedCleanedAndHeld() {
        val heard =
            listOf(
                CaptionWord("i", 0L, 100 * MS),
                CaptionWord("[unk]", 150 * MS, 200 * MS),
                CaptionWord("think", 200 * MS, 300 * MS),
                CaptionWord("late", 200 * MS, 300 * MS),
                CaptionWord("so", 2 * SECOND, 2_100 * MS),
            )
        val chunks = CaptionChunker.chunk(heard)
        assertEquals(listOf(listOf("I", "think"), listOf("So")), chunks.map { chunk -> chunk.words.map { it.text } })
        assertEquals(SECOND, chunks[0].sourceEndMicros)
        assertEquals(3 * SECOND, chunks[1].sourceEndMicros)
        val crowded = listOf(CaptionWord("a", 0L, 100 * MS), CaptionWord("b", 800 * MS, 900 * MS))
        assertEquals(720 * MS, CaptionChunker.chunk(crowded)[0].sourceEndMicros)
    }

    /** Sources follow the scope and skip muted clips unless asked. */
    @Test
    fun sourcesFollowScopeAndSound() {
        assertEquals(listOf("A"), sourceIds(CaptionScope.ALL))
        assertEquals(listOf("A", "V"), sourceIds(CaptionScope.ALL, muted = true))
        assertEquals(listOf("V"), sourceIds(CaptionScope.VOICEOVERS, muted = true))
        assertEquals(listOf("A"), sourceIds(CaptionScope.SELECTED, selected = "A"))
    }

    /** A plan puts each track's captions on its own lane, and regenerating replaces them in one edit. */
    @Test
    fun planPlacesAndReplacesCaptions() {
        val transcripts = mapOf("A" to words(3), "V" to words(2, 500 * MS))
        val plan = requireNotNull(CaptionPlanner.plan(project, transcripts, CaptionPlanner.DEFAULT_STYLE, ids))
        assertEquals(2, plan.captionCount)
        val captioned = appliedTo(project, plan.command)
        val lanes = captioned.tracks.filter { it.kind == TrackKind.TEXT }
        assertEquals(2, lanes.size)
        assertEquals(listOf(0L, 1_500 * MS), lanes.flatMap { it.clips }.map { it.startMicros }.sorted())
        assertTrue(CaptionPlanner.hasCaptions(captioned))
        val again = CaptionPlanner.plan(captioned, mapOf("A" to words(1)), CaptionPlanner.DEFAULT_STYLE, ids)
        val replaced = appliedTo(captioned, requireNotNull(again).command)
        assertEquals(1, replaced.tracks.count { it.kind == TrackKind.TEXT })
        assertEquals(project, appliedTo(replaced, requireNotNull(CaptionPlanner.removeAll(replaced))))
        assertFalse(CaptionPlanner.hasCaptions(project))
        assertNull(CaptionPlanner.removeAll(project))
    }
}

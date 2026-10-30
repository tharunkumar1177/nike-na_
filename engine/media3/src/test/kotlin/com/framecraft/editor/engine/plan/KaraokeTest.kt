/*
 * Unit tests for caption word timing: where each word starts on the timeline and which word is being spoken.
 */
package com.framecraft.editor.engine.plan

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies karaoke timing for caption C, which shows "Hello world" from base clip A read one second in. */
class KaraokeTest {
    private val caption =
        Clip(
            id = "C",
            assetId = TEXT_ASSET_ID,
            startMicros = 0L,
            trimStartMicros = 0L,
            durationMicros = 900_000L,
            anchor = ClipAnchor("A", 0L),
            overlayTransform = OverlayTransform(),
            text =
                TextContent(
                    "world again",
                    TextStyle(highlightArgb = 0x7FFFFF00),
                    caption =
                        CaptionContent(
                            listOf(
                                CaptionWord("Hello", 500_000L, 900_000L),
                                CaptionWord("world", 1_000_000L, 1_400_000L),
                                CaptionWord("again", 1_500_000L, 1_800_000L),
                            ),
                            1_900_000L,
                        ),
                ),
        )

    private val project =
        Project(
            id = "project",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets =
                listOf(
                    MediaAsset(
                        id = "video",
                        uri = "content://media/video",
                        kind = MediaKind.VIDEO,
                        durationMicros = 10_000_000L,
                        width = 1920,
                        height = 1080,
                        rotationDegrees = 0,
                        hasAudio = true,
                        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
                    ),
                    TEXT_ASSET,
                ),
            tracks =
                listOf(
                    Track("L", TrackKind.TEXT, false, listOf(caption)),
                    Track("T0", TrackKind.VIDEO, true, listOf(Clip("A", "video", 0L, 1_000_000L, 4_000_000L))),
                ),
        )

    /** Words the clip still plays start where it plays them; trimmed-off words are not shown. */
    @Test
    fun wordStartsFollowTheClip() {
        assertEquals(listOf(0L, 500_000L), Karaoke.wordStarts(project, caption))
        assertEquals(emptyList<Long>(), Karaoke.wordStarts(project, caption.copy(anchor = null)))
        assertEquals(listOf(0L, 500_000L), PlaybackPlanner.plan(project).textSpans.single().wordStartsMicros)
    }

    /** The spoken word is the last to have started, and its characters are found between the spaces. */
    @Test
    fun spokenWordAndItsCharacters() {
        val starts = listOf(0L, 500_000L)
        assertEquals(-1, Karaoke.wordAt(starts, -1L))
        assertEquals(0, Karaoke.wordAt(starts, 499_999L))
        assertEquals(1, Karaoke.wordAt(starts, 500_000L))
        assertEquals(6..10, Karaoke.rangeOf("world again", 1))
        assertNull(Karaoke.rangeOf("world again", 2))
    }
}

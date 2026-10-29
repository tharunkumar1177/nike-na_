/*
 * JVM UI tests (Robolectric) for timeline scrubbing, selection, trimming, audio and text lanes, and the zoom and snap
 * controls.
 */
package com.framecraft.editor.timeline

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.TrimClip
import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.edit.TextPlacement
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.engine.ThumbnailKey
import com.framecraft.editor.engine.ThumbnailSource
import com.framecraft.editor.engine.Waveform
import com.framecraft.editor.engine.WaveformSource
import com.framecraft.editor.ui.theme.FramecraftTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real timeline strip with touch input. */
@RunWith(AndroidJUnit4::class)
class TimelineStripTest {
    /** Compose rule hosting the strip in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private var counter = 0
    private val project =
        ProjectFactory.create(
            name = "Trip",
            assets =
                listOf(
                    MediaAsset(
                        id = "asset",
                        uri = "content://media/asset",
                        kind = MediaKind.VIDEO,
                        durationMicros = 20_000_000L,
                        width = 1920,
                        height = 1080,
                        rotationDegrees = 0,
                        hasAudio = true,
                        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
                    ),
                ),
            idGenerator = { "id-${counter++}" },
        )
    private val events = mutableListOf<String>()
    private val seeks = mutableListOf<Long>()
    private val requested = mutableListOf<ThumbnailKey>()
    private val selections = mutableListOf<String?>()

    /** Thumbnail source that records requests and never loads a bitmap. */
    private val thumbnails =
        object : ThumbnailSource {
            override val version: StateFlow<Int> = MutableStateFlow(0)

            /** Returns no bitmap; nothing is loaded in these tests. */
            override fun get(key: ThumbnailKey): Bitmap? = null

            /** Reports no failures. */
            override fun hasFailed(key: ThumbnailKey): Boolean = false

            /** Records the requested keys. */
            override fun request(keys: Collection<ThumbnailKey>) {
                requested += keys
            }
        }

    private val waveformRequests = mutableListOf<String>()

    /** Waveform source that records requests and never loads a waveform. */
    private val waveforms =
        object : WaveformSource {
            override val version: StateFlow<Int> = MutableStateFlow(0)

            /** Returns no waveform; nothing is decoded in these tests. */
            override fun get(uri: String): Waveform? = null

            /** Reports no failures. */
            override fun hasFailed(uri: String): Boolean = false

            /** Records the requested sources. */
            override fun request(uris: Collection<String>) {
                waveformRequests += uris
            }
        }

    private val edits = mutableListOf<EditCommand>()

    /** Shows [shown] with the playhead at zero and [selectedClipId] selected. */
    private fun showStrip(
        selectedClipId: String? = null,
        shown: Project = project,
    ) {
        composeRule.setContent {
            FramecraftTheme {
                TimelineStrip(
                    project = shown,
                    positionMicros = { 0L },
                    thumbnails = thumbnails,
                    waveforms = waveforms,
                    callbacks =
                        ScrubCallbacks(
                            onStart = { events += "start" },
                            onSeek = { seeks += it },
                            onEnd = { events += "end" },
                        ),
                    selectedClipId = selectedClipId,
                    onClipSelected = { selections += it },
                    onEdit = { edits += it },
                    onHaptic = {},
                )
            }
        }
    }

    /** Dragging the start handle of the selected clip saves exactly one trim that cuts its beginning. */
    @Test
    fun startHandleDragTrimsOnce() {
        val track = project.tracks.single()
        val clip = track.clips.single()
        showStrip(selectedClipId = clip.id)
        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput {
            down(Offset(width / 2f, 72.dp.toPx()))
            moveBy(Offset(100.dp.toPx(), 0f))
            up()
        }
        composeRule.waitForIdle()
        val trim = edits.single() as TrimClip
        assertEquals(clip.id, trim.clipId)
        assertEquals(clip.startMicros, trim.startMicros)
        assertTrue(trim.trimStartMicros > 0L)
        assertEquals(clip.durationMicros - trim.trimStartMicros, trim.durationMicros)
        assertTrue(selections.isEmpty())
    }

    /** Snapping starts on and turns off with its toggle. */
    @Test
    fun snapToggles() {
        showStrip()
        composeRule.onNodeWithTag(SNAP_TOGGLE_TAG).assertIsOn().performClick()
        composeRule.onNodeWithTag(SNAP_TOGGLE_TAG).assertIsOff()
    }

    /** Zooming to the playhead starts off and turns on with its toggle. */
    @Test
    fun zoomToPlayheadToggles() {
        showStrip()
        composeRule.onNodeWithTag(ZOOM_PLAYHEAD_TAG).assertIsOff().performClick()
        composeRule.onNodeWithTag(ZOOM_PLAYHEAD_TAG).assertIsOn()
    }

    /** Swiping left pauses, seeks forward to frame boundaries, then ends the scrub. */
    @Test
    fun swipeScrubsForward() {
        showStrip()
        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(listOf("start", "end"), events)
        assertTrue(seeks.isNotEmpty())
        assertTrue(seeks.zipWithNext().all { (a, b) -> b > a })
        assertTrue(seeks.all { project.frameRate.snapToNearestFrame(it) == it })
    }

    /** The zoom to fit action is available. */
    @Test
    fun showsZoomToFit() {
        showStrip()
        composeRule.onNodeWithContentDescription("Zoom to fit timeline").assertIsDisplayed()
    }

    /** Tapping the clip right of the playhead selects it; swiping does not select. */
    @Test
    fun tapSelectsClip() {
        showStrip()
        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 0.75f, 72.dp.toPx())) }
        composeRule.waitForIdle()
        val track = project.tracks.single()
        assertEquals(listOf<String?>(track.clips.single().id), selections)
    }

    /** Tapping left of the playhead, before time zero, clears the selection. */
    @Test
    fun tapOnEmptySpaceClearsSelection() {
        showStrip()
        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 0.25f, 72.dp.toPx())) }
        composeRule.waitForIdle()
        assertEquals(listOf<String?>(null), selections)
    }

    /** Thumbnails for the visible part of the clip are requested, starting at the clip's first frame. */
    @Test
    fun requestsVisibleThumbnails() {
        showStrip()
        composeRule.waitForIdle()
        assertTrue(requested.all { it.uri == "content://media/asset" && it.kind == MediaKind.VIDEO })
        assertTrue(requested.any { it.sourceMicros == 0L })
    }

    /**
     * An extracted audio lane asks for its sound's waveform, and tapping the lane selects the audio clip.
     */
    @Test
    fun audioLaneRequestsWaveformAndSelects() {
        val base = project.tracks.single()
        val video = base.clips.single()
        var next = 0
        val extract = AudioPlacement.proposeExtract(project, video.id, IdGenerator { "audio-${++next}" })!!
        val withAudio = (extract.applyTo(project) as CommandOutcome.Applied).project
        showStrip(shown = withAudio)
        composeRule.waitForIdle()
        assertEquals(listOf("content://media/asset"), waveformRequests.distinct())
        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 0.75f, 144.dp.toPx())) }
        composeRule.waitForIdle()
        assertEquals(listOf<String?>(extract.clip.id), selections)
    }

    /** A text lane above the base asks for no thumbnails, and tapping it selects the text clip. */
    @Test
    fun textLaneSelectsWithoutThumbnails() {
        var next = 0
        val add = TextPlacement.propose(project, 0L, TextContent("Hello"), IdGenerator { "text-${++next}" })!!
        val withText = (add.applyTo(project) as CommandOutcome.Applied).project
        showStrip(shown = withText)
        composeRule.waitForIdle()
        assertTrue(requested.none { it.kind == MediaKind.TEXT })
        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput {
            click(Offset(width / 2f + 8.dp.toPx(), 72.dp.toPx()))
        }
        composeRule.waitForIdle()
        assertEquals(listOf<String?>(add.clip.id), selections)
    }
}

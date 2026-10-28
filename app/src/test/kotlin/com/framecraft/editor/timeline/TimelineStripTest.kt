/*
 * JVM UI tests (Robolectric) for timeline scrubbing and zoom to fit.
 */
package com.framecraft.editor.timeline

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.engine.ThumbnailKey
import com.framecraft.editor.engine.ThumbnailSource
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

    /** Shows the strip with the playhead at zero. */
    private fun showStrip() {
        composeRule.setContent {
            FramecraftTheme {
                TimelineStrip(
                    project = project,
                    positionMicros = { 0L },
                    thumbnails = thumbnails,
                    callbacks =
                        ScrubCallbacks(
                            onStart = { events += "start" },
                            onSeek = { seeks += it },
                            onEnd = { events += "end" },
                        ),
                    selectedClipId = null,
                    onClipSelected = { selections += it },
                )
            }
        }
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
        val clip = project.tracks.single().clips.single()
        assertEquals(listOf<String?>(clip.id), selections)
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
}

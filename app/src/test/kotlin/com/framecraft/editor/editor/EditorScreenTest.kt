/*
 * JVM UI tests (Robolectric) for the editor screen's playback bar and discard confirmation.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.engine.PreviewState
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders [EditorScreen] with real state values and checks its interactions. */
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {
    /** Compose rule hosting the screen in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private var playPauseClicks = 0
    private var discarded = false

    /** Sets the editor content with a paused preview at 0:05 of 1:23. */
    private fun showEditor(isPlaying: Boolean = false) {
        composeRule.setContent {
            FramecraftTheme {
                EditorScreen(
                    projectName = "New project",
                    previewState =
                        PreviewState(isPlaying = isPlaying, positionMicros = 5_000_000L, durationMicros = 83_000_000L),
                    aspectRatio = 16f / 9f,
                    skippedCount = 0,
                    onSkippedShown = {},
                    onPlayPause = { playPauseClicks++ },
                    onDiscard = { discarded = true },
                    previewSurface = { modifier -> Box(modifier) },
                )
            }
        }
    }

    /** The playback bar shows position over duration and a play action. */
    @Test
    fun showsTimecodeAndPlay() {
        showEditor()
        composeRule.onNodeWithText("0:05 / 1:23").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play video").performClick()
        assertEquals(1, playPauseClicks)
    }

    /** While playing, the button offers pause. */
    @Test
    fun showsPauseWhilePlaying() {
        showEditor(isPlaying = true)
        composeRule.onNodeWithContentDescription("Pause video").assertIsDisplayed()
    }

    /** Navigating up asks for confirmation before discarding. */
    @Test
    fun confirmsBeforeDiscarding() {
        showEditor()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.onNodeWithText("Discard project?").assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()
        assertTrue(discarded)
    }
}

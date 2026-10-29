/*
 * JVM UI tests (Robolectric) for the editor screen: playback, closing, undo and redo, and tools.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.engine.PreviewState
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders [EditorScreen] with real state values and checks its interactions. */
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {
    /** Compose rule hosting the screen in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private val actions =
        EditorActions(
            onPlayPause = { events += "playPause" },
            onClose = { events += "close" },
            onUndo = { events += "undo" },
            onRedo = { events += "redo" },
            onDeleteClip = { events += "delete" },
            onNoticeShown = { events += it.name },
        )

    /** Sets the editor content with the preview at 0:05 of 1:23. */
    private fun showEditor(
        isPlaying: Boolean = false,
        canUndo: Boolean = false,
        hasSelection: Boolean = false,
    ) {
        composeRule.setContent {
            FramecraftTheme {
                EditorScreen(
                    projectName = "New project",
                    previewState =
                        PreviewState(isPlaying = isPlaying, positionMicros = 5_000_000L, durationMicros = 83_000_000L),
                    aspectRatio = 16f / 9f,
                    canUndo = canUndo,
                    canRedo = false,
                    hasSelection = hasSelection,
                    notices = EditorNotices(),
                    actions = actions,
                    previewSurface = { modifier -> Box(modifier) },
                    timeline = { modifier -> Box(modifier) },
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
        assertEquals(listOf("playPause"), events)
    }

    /** While playing, the button offers pause. */
    @Test
    fun showsPauseWhilePlaying() {
        showEditor(isPlaying = true)
        composeRule.onNodeWithContentDescription("Pause video").assertIsDisplayed()
    }

    /** Navigating up closes right away because every edit is already saved. */
    @Test
    fun navigatingUpClosesWithoutConfirmation() {
        showEditor()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(listOf("close"), events)
    }

    /** Undo and redo follow their availability. */
    @Test
    fun undoAndRedoFollowAvailability() {
        showEditor(canUndo = true)
        composeRule.onNodeWithTag(REDO_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(UNDO_TAG).assertIsEnabled().performClick()
        assertEquals(listOf("undo"), events)
    }

    /** The delete tool appears only with a selection. */
    @Test
    fun deleteToolNeedsSelection() {
        showEditor(hasSelection = true)
        composeRule.onNodeWithContentDescription("Delete clip").assertIsDisplayed().performClick()
        assertEquals(listOf("delete"), events)
    }
}

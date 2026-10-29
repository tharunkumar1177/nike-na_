/*
 * JVM UI tests (Robolectric) for the editor screen: playback, frame steps, closing, undo and redo, and tools.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
            onClose = { events += "close" },
            onUndo = { events += "undo" },
            onRedo = { events += "redo" },
            onExport = { events += "export" },
            onNoticeShown = { events += it.name },
        )

    private val playback =
        PlaybackActions(
            onPlayPause = { events += "playPause" },
            onFrameBack = { events += "frameBack" },
            onFrameForward = { events += "frameForward" },
        )

    private val tools =
        ToolActions(
            onSplitClip = { events += "split" },
            onDeleteClip = { events += "delete" },
        )

    /** Sets the editor content with the preview at [positionMicros] of 1:23. */
    private fun showEditor(
        isPlaying: Boolean = false,
        canUndo: Boolean = false,
        availability: ToolAvailability = ToolAvailability(),
        positionMicros: Long = 5_000_000L,
        export: ExportIndicator = ExportIndicator(),
    ) {
        composeRule.setContent {
            FramecraftTheme {
                EditorScreen(
                    projectName = "New project",
                    previewState =
                        PreviewState(
                            isPlaying = isPlaying,
                            positionMicros = positionMicros,
                            durationMicros = 83_000_000L,
                        ),
                    aspectRatio = 16f / 9f,
                    canUndo = canUndo,
                    canRedo = false,
                    availability = availability,
                    notices = EditorNotices(),
                    actions = actions,
                    playback = playback,
                    tools = tools,
                    previewSurface = { modifier -> Box(modifier) },
                    timeline = { modifier -> Box(modifier) },
                    export = export,
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

    /** Frame steps report each direction and are disabled at the start of the timeline. */
    @Test
    fun frameStepButtons() {
        showEditor()
        composeRule.onNodeWithContentDescription("Previous frame").performClick()
        composeRule.onNodeWithContentDescription("Next frame").performClick()
        assertEquals(listOf("frameBack", "frameForward"), events)
    }

    /** At time zero there is no previous frame. */
    @Test
    fun noPreviousFrameAtStart() {
        showEditor(positionMicros = 0L)
        composeRule.onNodeWithTag(FRAME_BACK_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(FRAME_FORWARD_TAG).assertIsEnabled()
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

    /** The tools appear only with a selection. */
    @Test
    fun toolsNeedSelection() {
        showEditor()
        composeRule.onAllNodesWithTag(TOOL_DELETE_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TOOL_SPLIT_TAG).assertCountEquals(0)
    }

    /** With a selection and the playhead inside it, Split and Delete both report. */
    @Test
    fun splitAndDeleteWithSelection() {
        showEditor(availability = ToolAvailability(hasSelection = true, canSplit = true))
        composeRule.onNodeWithContentDescription("Split clip at playhead").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Delete clip").assertIsDisplayed().performClick()
        assertEquals(listOf("split", "delete"), events)
    }

    /** The export action opens the export sheet. */
    @Test
    fun exportActionReports() {
        showEditor()
        composeRule.onNodeWithContentDescription("Export video").performClick()
        assertEquals(listOf("export"), events)
    }

    /** While exporting, the export action shows progress and still reopens the sheet. */
    @Test
    fun exportShowsProgressWhileRunning() {
        showEditor(export = ExportIndicator(exporting = true, percent = 45))
        composeRule.onNodeWithContentDescription("Exporting, 45 percent complete").assertIsDisplayed()
        composeRule.onNodeWithTag(EXPORT_TOP_ICON_TAG).performClick()
        assertEquals(listOf("export"), events)
    }

    /** Split is shown but disabled when the playhead is not inside the selected clip. */
    @Test
    fun splitDisabledOutsideClip() {
        showEditor(availability = ToolAvailability(hasSelection = true, canSplit = false))
        composeRule.onNodeWithTag(TOOL_SPLIT_TAG).assertIsNotEnabled()
    }
}

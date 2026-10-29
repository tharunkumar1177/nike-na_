/*
 * JVM UI tests (Robolectric) for the export sheet bodies: settings, progress, success, and failure.
 */
package com.framecraft.editor.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.export.ExportPlanner
import com.framecraft.editor.domain.export.ExportResolution
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.engine.export.ExportFailure
import com.framecraft.editor.engine.export.ExportState
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders [ExportSheetContent] for each export state and checks what it shows and reports. */
@RunWith(AndroidJUnit4::class)
class ExportSheetContentTest {
    /** Compose rule hosting the sheet body in a test activity. */
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
                        durationMicros = 10_000_000L,
                        width = 1920,
                        height = 1080,
                        rotationDegrees = 0,
                        hasAudio = true,
                        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
                    ),
                ),
            idGenerator = { "id-${counter++}" },
        )
    private val form = ExportForm.of(project, ExportPlanner.defaultsFor(project))
    private val events = mutableListOf<String>()
    private val selections = mutableListOf<ExportSettings>()

    private val actions =
        ExportSheetActions(
            onSelect = { selections += it },
            onStart = { events += "start" },
            onCancel = { events += "cancel" },
            onShare = { events += "share $it" },
            onPlay = { events += "play $it" },
            onClose = { events += "close" },
        )

    /** Shows the sheet body for [state] with [shownForm]. */
    private fun show(
        state: ExportState,
        shownForm: ExportForm? = form,
    ) {
        composeRule.setContent {
            FramecraftTheme {
                ExportSheetContent(shownForm, state, actions)
            }
        }
    }

    /** Settings offer the project's choices, report a new choice, and start the export. */
    @Test
    fun settingsSelectAndStart() {
        show(ExportState.Idle)
        composeRule.onNodeWithText("Match (30)").assertIsDisplayed()
        composeRule.onNodeWithText("720p").performClick()
        composeRule.onNodeWithTag(EXPORT_START_TAG).performClick()
        assertEquals(listOf(form.settings.copy(resolution = ExportResolution.HD_720)), selections)
        assertEquals(listOf("start"), events)
    }

    /** A project with no clips cannot be exported and says why. */
    @Test
    fun emptyProjectCannotExport() {
        val empty = project.copy(tracks = project.tracks.map { it.copy(clips = emptyList()) })
        show(ExportState.Idle, ExportForm.of(empty, ExportPlanner.defaultsFor(empty)))
        composeRule.onNodeWithText("Add a clip before exporting.").assertIsDisplayed()
        composeRule.onNodeWithTag(EXPORT_START_TAG).assertIsNotEnabled()
    }

    /** Running shows the percent and cancels. */
    @Test
    fun runningShowsPercentAndCancels() {
        show(ExportState.Running(45))
        composeRule.onNodeWithText("45%").assertIsDisplayed()
        composeRule.onNodeWithTag(EXPORT_CANCEL_TAG).performClick()
        assertEquals(listOf("cancel"), events)
    }

    /** At 100 percent the video is being saved to the gallery. */
    @Test
    fun fullProgressSaves() {
        show(ExportState.Running(100))
        composeRule.onNodeWithText("Saving to gallery…").assertIsDisplayed()
    }

    /** Success offers share and play for the saved video. */
    @Test
    fun successSharesAndPlays() {
        show(ExportState.Succeeded("content://media/video/1", 1_000L))
        composeRule.onNodeWithText("Saved to Movies/Framecraft.").assertIsDisplayed()
        composeRule.onNodeWithTag(EXPORT_SHARE_TAG).performClick()
        composeRule.onNodeWithTag(EXPORT_PLAY_TAG).performClick()
        assertEquals(listOf("share content://media/video/1", "play content://media/video/1"), events)
    }

    /** Failure explains the reason and closes. */
    @Test
    fun failureExplainsAndCloses() {
        show(ExportState.Failed(ExportFailure.STORAGE_FULL))
        composeRule.onNodeWithText("Not enough storage space. Free up some space and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        assertEquals(listOf("close"), events)
    }

    /** Frame rates other than whole numbers show two decimals. */
    @Test
    fun formatsFractionalRates() {
        assertEquals("30", formatFps(FrameRate.FPS_30))
        assertEquals("29.97", formatFps(FrameRate.FPS_29_97))
    }
}

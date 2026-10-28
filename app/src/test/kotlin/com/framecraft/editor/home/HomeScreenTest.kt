/*
 * JVM UI tests (Robolectric) for the home project list: rows, opening, renaming, and deleting.
 */
package com.framecraft.editor.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.data.project.ProjectList
import com.framecraft.editor.data.project.ProjectSummary
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders [HomeScreen] with saved projects and checks its flows. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    /** Compose rule hosting the screen in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val now = 10_000_000_000L
    private val events = mutableListOf<String>()

    private val beach =
        ProjectSummary(
            id = "beach",
            name = "Beach day",
            createdAtEpochMillis = now - 600_000L,
            updatedAtEpochMillis = now - 300_000L,
            durationMicros = 83_000_000L,
            clipCount = 4,
            schemaVersion = 1,
        )

    /** Shows the list with [beach] and, optionally, one unreadable project. */
    private fun showHome(unreadable: List<String> = emptyList()) {
        composeRule.setContent {
            FramecraftTheme {
                HomeScreen(
                    projects = ProjectList(listOf(beach), unreadable),
                    nowMillis = now,
                    onOpenProject = { events += "open:$it" },
                    onNewProject = { events += "new" },
                    onRename = { id, name -> events += "rename:$id:$name" },
                    onDelete = { events += "delete:$it" },
                )
            }
        }
    }

    /** A row shows the name and its edited time, duration, and clip count, and opens on tap. */
    @Test
    fun rowShowsSummaryAndOpens() {
        showHome()
        composeRule.onNodeWithText("Edited 5 min ago • 1:23 • 4 clips").assertIsDisplayed()
        composeRule.onNodeWithText("Beach day").performClick()
        assertEquals(listOf("open:beach"), events)
    }

    /** Rename requires a non-blank name and reports the trimmed name. */
    @Test
    fun renamesThroughDialog() {
        showHome()
        composeRule.onNodeWithContentDescription("Project options for Beach day").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(RENAME_INPUT_TAG).performTextReplacement("   ")
        composeRule.onNodeWithTag(RENAME_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(RENAME_INPUT_TAG).performTextReplacement(" Lake day ")
        composeRule.onNodeWithTag(RENAME_CONFIRM_TAG).performClick()
        assertEquals(listOf("rename:beach:Lake day"), events)
    }

    /** Delete asks for confirmation first. */
    @Test
    fun deletesAfterConfirmation() {
        showHome()
        composeRule.onNodeWithContentDescription("Project options for Beach day").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete project?").assertIsDisplayed()
        assertTrue(events.isEmpty())
        composeRule.onNodeWithTag(DELETE_CONFIRM_TAG).performClick()
        assertEquals(listOf("delete:beach"), events)
    }

    /** Unreadable projects explain the problem and offer only Delete. */
    @Test
    fun unreadableProjectsOfferOnlyDelete() {
        showHome(unreadable = listOf("broken"))
        composeRule.onNodeWithText("Couldn't read project file").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Project options for Unreadable project").performClick()
        assertTrue(composeRule.onAllNodesWithText("Rename").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithTag(DELETE_CONFIRM_TAG).performClick()
        assertEquals(listOf("delete:broken"), events)
    }
}

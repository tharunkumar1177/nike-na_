/*
 * JVM UI test (Robolectric) verifying the app launches to the empty home screen once the project list loads.
 */
package com.framecraft.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.home.NEW_PROJECT_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches [MainActivity] and checks the empty home state. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    /** Compose rule that launches the real launcher activity. */
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** The empty-state headline and body are visible once the (empty) project list has loaded. */
    @Test
    fun showsEmptyState() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("No projects yet").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No projects yet").assertIsDisplayed()
        composeRule.onNodeWithText("Create your first project to start editing.").assertIsDisplayed()
    }

    /** The create action is available. */
    @Test
    fun newProjectIsEnabled() {
        composeRule.onNodeWithTag(NEW_PROJECT_TAG).assertIsDisplayed().assertIsEnabled()
    }
}

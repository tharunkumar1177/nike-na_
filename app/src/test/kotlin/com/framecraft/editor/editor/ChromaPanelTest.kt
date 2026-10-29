/*
 * JVM UI tests (Robolectric) for the chroma key panel: the screen presets, the eyedropper toggle and its messages,
 * the strength, softness, and spill sliders, and removing the key.
 */
package com.framecraft.editor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Shows the real chroma key panel over a clip's key and records each change it saves. */
@RunWith(AndroidJUnit4::class)
class ChromaPanelTest {
    /** Compose rule hosting the panel in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<ChromaKey?>()

    private var toggles = 0

    /** Shows the panel starting from [initial], [picking] or not, with the last pick [pickFailed] or not. */
    private fun showPanel(
        initial: ChromaKey?,
        picking: Boolean = false,
        pickFailed: Boolean = false,
    ) {
        composeRule.setContent {
            var key by remember { mutableStateOf(initial) }
            FramecraftTheme {
                ChromaPanel(
                    key = key,
                    picking = picking,
                    pickFailed = pickFailed,
                    onChange = { next ->
                        changes += next
                        key = next
                    },
                    onPickToggle = { toggles++ },
                    onDone = {},
                )
            }
        }
    }

    /** Without a key the panel asks for a color; a preset starts a key of that color with default settings. */
    @Test
    fun presetStartsKey() {
        showPanel(null)
        composeRule.onNodeWithText("Pick a color or a preset to start").assertExists()
        composeRule.onNodeWithTag(CHROMA_STRENGTH_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CHROMA_GREEN_TAG).performScrollTo().performClick()
        assertEquals(listOf<ChromaKey?>(ChromaKey(GREEN_SCREEN_ARGB)), changes)
        composeRule.onNodeWithTag(CHROMA_GREEN_TAG).assertIsSelected()
    }

    /** Switching preset keeps the key's settings and changes only its color. */
    @Test
    fun presetKeepsSettings() {
        val key = ChromaKey(GREEN_SCREEN_ARGB, tolerancePercent = 70, softnessPercent = 10, spillPercent = 0)
        showPanel(key)
        composeRule.onNodeWithTag(CHROMA_BLUE_TAG).performScrollTo().performClick()
        assertEquals(key.copy(keyColorArgb = BLUE_SCREEN_ARGB), changes.single())
    }

    /** Each finished slider drag saves one change to its own setting. */
    @Test
    fun slidersSaveTheirSetting() {
        val key = ChromaKey(BLUE_SCREEN_ARGB)
        showPanel(key)
        composeRule.onNodeWithTag(CHROMA_STRENGTH_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        composeRule.onNodeWithTag(CHROMA_SOFTNESS_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        composeRule.onNodeWithTag(CHROMA_SPILL_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        val strong = key.copy(tolerancePercent = 80)
        val sharp = strong.copy(softnessPercent = 5)
        assertEquals(listOf(strong, sharp, sharp.copy(spillPercent = 90)), changes)
    }

    /** Remove clears the key. */
    @Test
    fun removeClearsKey() {
        showPanel(ChromaKey(GREEN_SCREEN_ARGB))
        composeRule.onNodeWithTag(CHROMA_REMOVE_TAG).performScrollTo().performClick()
        assertNull(changes.single())
    }

    /** The pick chip toggles the eyedropper, which shows its instruction while waiting for a tap. */
    @Test
    fun pickToggleShowsInstruction() {
        showPanel(null, picking = true)
        composeRule.onNodeWithTag(CHROMA_PICK_TAG).assertIsSelected()
        composeRule.onNodeWithText("Tap the color to remove in the preview").assertExists()
        composeRule.onNodeWithTag(CHROMA_PICK_TAG).performScrollTo().performClick()
        assertEquals(1, toggles)
    }

    /** A failed pick says the frame could not be read. */
    @Test
    fun failedPickWarns() {
        showPanel(ChromaKey(GREEN_SCREEN_ARGB), pickFailed = true)
        composeRule.onNodeWithTag(CHROMA_PICKING_TAG).assertExists()
        composeRule.onNodeWithText("That frame could not be read. Try another spot.").assertExists()
    }
}

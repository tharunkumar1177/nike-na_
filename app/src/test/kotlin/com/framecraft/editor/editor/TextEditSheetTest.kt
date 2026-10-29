/*
 * JVM UI tests (Robolectric) for the text sheet: editing words and style, picking an animation, and blank text.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.DEFAULT_TEXT_ANIMATION_MICROS
import com.framecraft.editor.domain.model.TextAnimation
import com.framecraft.editor.domain.model.TextAnimationKind
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TextOutline
import com.framecraft.editor.domain.model.TextStyle
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the real sheet on a three-second clip saying "Text" and checks what it returns. */
@RunWith(AndroidJUnit4::class)
class TextEditSheetTest {
    /** Compose rule hosting the sheet in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val results = mutableListOf<TextContent?>()

    /** Shows the sheet opened on [tab]. */
    private fun showSheet(tab: TextSheetTab) {
        composeRule.setContent {
            FramecraftTheme {
                TextEditSheet(
                    content = TextContent("Text"),
                    clipDurationMicros = 3_000_000L,
                    startTab = tab,
                    onClose = { results += it },
                )
            }
        }
    }

    /** New words and an outline come back together once, when Done is pressed. */
    @Test
    fun doneReturnsWordsAndStyle() {
        showSheet(TextSheetTab.STYLE)
        composeRule.onNodeWithTag(TEXT_FIELD_TAG).performTextReplacement("Hello")
        composeRule.onNodeWithText("Outline").performScrollTo().performClick()
        composeRule.onNodeWithTag(TEXT_DONE_TAG).performClick()
        assertEquals(listOf(TextContent("Hello", TextStyle(outline = TextOutline()))), results)
    }

    /** Picking a preset on the animation tab sets the entrance at the default length. */
    @Test
    fun animationTabSetsEntrance() {
        showSheet(TextSheetTab.ANIMATION)
        composeRule.onNodeWithText("Fade").performScrollTo().performClick()
        composeRule.onNodeWithTag(TEXT_DURATION_SLIDER_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TEXT_DONE_TAG).performClick()
        val fade = TextAnimation(TextAnimationKind.FADE, DEFAULT_TEXT_ANIMATION_MICROS)
        assertEquals(listOf(TextContent("Text", animationIn = fade)), results)
    }

    /** Blank words disable Done and explain why. */
    @Test
    fun blankTextDisablesDone() {
        showSheet(TextSheetTab.STYLE)
        composeRule.onNodeWithTag(TEXT_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithText("Text cannot be empty").assertIsDisplayed()
        composeRule.onNodeWithTag(TEXT_DONE_TAG).assertIsNotEnabled()
        assertEquals(emptyList<TextContent?>(), results)
    }
}

/*
 * JVM UI tests (Robolectric) for the canvas sheet: picking an aspect ratio and a background, each sent at once.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.CanvasAspect
import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasSettings
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the real sheet on a square, black canvas and records what each tap sends. */
@RunWith(AndroidJUnit4::class)
class CanvasSheetTest {
    /** Compose rule hosting the sheet in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val picks = mutableListOf<Any>()

    /** Shows the sheet for a square canvas with the default background. */
    private fun showSheet() {
        composeRule.setContent {
            FramecraftTheme {
                CanvasSheet(
                    canvas = CanvasSettings(aspect = CanvasAspect.SQUARE),
                    onAspect = { picks += it },
                    onBackground = { picks += it },
                    onDismiss = { picks += "dismiss" },
                )
            }
        }
    }

    /** The current shape is chosen and tapping another sends it. */
    @Test
    fun aspectChipSendsShape() {
        showSheet()
        composeRule.onNodeWithTag(CANVAS_ASPECT_TAG_PREFIX + "1_1").assertIsSelected()
        composeRule.onNodeWithTag(CANVAS_ASPECT_TAG_PREFIX + "9_16").performScrollTo().performClick()
        assertEquals(listOf<Any>(CanvasAspect.PORTRAIT_9_16), picks)
    }

    /** The background tab rings black and sends blur or a plain color when tapped. */
    @Test
    fun backgroundSwatchSendsBackground() {
        showSheet()
        composeRule.onNodeWithTag(CANVAS_BACKGROUND_TAB_TAG).performClick()
        composeRule.onNodeWithTag(CANVAS_BACKGROUND_TAG_PREFIX + "black").assertIsSelected()
        composeRule.onNodeWithTag(CANVAS_BACKGROUND_TAG_PREFIX + "blur").performClick()
        composeRule.onNodeWithTag(CANVAS_BACKGROUND_TAG_PREFIX + "red").performScrollTo().performClick()
        assertEquals(listOf(CanvasBackground.Blur, CanvasBackground.Solid(0xFFE53935.toInt())), picks)
    }

    /** Every shape has its own chip tag. */
    @Test
    fun aspectTagsAreDistinct() {
        val tags = CanvasAspect.entries.map(::aspectTagOf)
        assertEquals(CanvasAspect.entries.size, tags.toSet().size)
        assertEquals("original", aspectTagOf(CanvasAspect.ORIGINAL))
    }
}

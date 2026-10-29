/*
 * JVM UI tests (Robolectric) for the mask panel: choosing, switching, and removing a shape, inverting it, and the
 * feather and corner sliders.
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
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MaskShape
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Shows the real mask panel over a clip's mask and records each change it saves. */
@RunWith(AndroidJUnit4::class)
class MaskPanelTest {
    /** Compose rule hosting the panel in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<ClipMask?>()

    private var done = 0

    /** Shows the panel starting from [initial], with the playhead [onClip] or not; saved changes feed back in. */
    private fun showPanel(
        initial: ClipMask?,
        onClip: Boolean = true,
    ) {
        composeRule.setContent {
            var mask by remember { mutableStateOf(initial) }
            FramecraftTheme {
                MaskPanel(
                    mask = mask,
                    onClip = onClip,
                    onChange = { next ->
                        changes += next
                        mask = next
                    },
                    onDone = { done++ },
                )
            }
        }
    }

    /** Returns the tag of the chip for [shape], or for no mask when null. */
    private fun chipTag(shape: MaskShape?): String = MASK_SHAPE_TAG_PREFIX + (shape?.name?.lowercase() ?: "none")

    /** Without a mask the none chip is chosen; picking a shape saves that shape's starting mask. */
    @Test
    fun pickingShapeSavesDefault() {
        showPanel(null)
        composeRule.onNodeWithTag(chipTag(null)).assertIsSelected()
        composeRule.onNodeWithTag(chipTag(MaskShape.CIRCLE)).performScrollTo().performClick()
        assertEquals(listOf(ClipMask.defaultFor(MaskShape.CIRCLE)), changes)
        composeRule.onNodeWithTag(chipTag(MaskShape.CIRCLE)).assertIsSelected()
        composeRule.onNodeWithText("Drag the mask on the preview", substring = true).assertExists()
    }

    /** Tapping the chosen shape again saves nothing; switching keeps the placement and none removes the mask. */
    @Test
    fun switchingAndRemovingShape() {
        val circle = ClipMask.defaultFor(MaskShape.CIRCLE).copy(centerX = 0.4f, invert = true)
        showPanel(circle)
        composeRule.onNodeWithTag(chipTag(MaskShape.CIRCLE)).performScrollTo().performClick()
        assertEquals(emptyList<ClipMask?>(), changes)
        composeRule.onNodeWithTag(chipTag(MaskShape.STAR)).performScrollTo().performClick()
        assertEquals(ClipMask.defaultFor(MaskShape.STAR).copy(centerX = 0.4f, invert = true), changes.single())
        composeRule.onNodeWithTag(chipTag(null)).performScrollTo().performClick()
        assertNull(changes.last())
    }

    /** The invert switch and a finished feather drag each save one change. */
    @Test
    fun invertAndFeatherSave() {
        val linear = ClipMask.defaultFor(MaskShape.LINEAR)
        showPanel(linear)
        composeRule.onNodeWithTag(MASK_INVERT_TAG).performClick()
        composeRule.onNodeWithTag(MASK_FEATHER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        assertEquals(
            listOf(linear.copy(invert = true), linear.copy(invert = true, featherPercent = 60)),
            changes,
        )
    }

    /** Only rectangles offer corner rounding, and its drag saves the rounding. */
    @Test
    fun cornerSliderOnRectanglesOnly() {
        showPanel(ClipMask.defaultFor(MaskShape.CIRCLE))
        composeRule.onNodeWithTag(MASK_CORNER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(chipTag(MaskShape.RECTANGLE)).performScrollTo().performClick()
        composeRule.onNodeWithTag(MASK_CORNER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        assertEquals(45, changes.last()?.cornerPercent)
    }

    /** Off the clip the panel says the handles need the playhead on it, and Done closes the panel. */
    @Test
    fun outsideHintAndDone() {
        showPanel(ClipMask.defaultFor(MaskShape.HEART), onClip = false)
        composeRule.onNodeWithText("Move the playhead over this clip to place its mask.").assertExists()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, done)
    }
}

/*
 * JVM UI tests (Robolectric) for the look sheet: filter and category chips, the intensity slider, the adjustment
 * sliders with their detent and resets, and apply to all and reset all.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the real sheet and records each look and apply to all it sends. */
@RunWith(AndroidJUnit4::class)
class LookSheetTest {
    /** Compose rule hosting the sheet in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<ClipLook>()

    private val appliedAll = mutableListOf<ClipLook>()

    private val warm = ClipLook(filterId = "honey", filterIntensity = 60, brightness = 10, temperature = 25)

    /** Shows the sheet for [look], having applied to [applied] clips. */
    private fun showSheet(
        look: ClipLook,
        applied: Int? = null,
    ) {
        composeRule.setContent {
            FramecraftTheme {
                LookSheet(
                    look = look,
                    applied = applied,
                    onChange = { changes += it },
                    onApplyAll = { appliedAll += it },
                    onDismiss = {},
                )
            }
        }
    }

    /** Opens the adjust tab. */
    private fun openAdjust() {
        composeRule.onNodeWithTag(LOOK_ADJUST_TAB_TAG).performClick()
    }

    /** A plain clip picks None and hides intensity; a filter chip sends that filter at full intensity. */
    @Test
    fun filterChipSendsAtFullIntensity() {
        showSheet(ClipLook())
        composeRule.onNodeWithTag(LOOK_FILTER_TAG_PREFIX + "none").assertIsSelected()
        composeRule.onAllNodesWithTag(LOOK_INTENSITY_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(LOOK_FILTER_TAG_PREFIX + "honey").performScrollTo().performClick()
        assertEquals(listOf(ClipLook(filterId = "honey")), changes)
    }

    /** Picking another filter resets the intensity to full and keeps the adjustments. */
    @Test
    fun switchingFilterResetsIntensity() {
        showSheet(warm)
        composeRule.onNodeWithTag(LOOK_FILTER_TAG_PREFIX + "honey").assertIsSelected()
        composeRule.onNodeWithTag(LOOK_FILTER_TAG_PREFIX + "noir").performScrollTo().performClick()
        assertEquals(listOf(warm.copy(filterId = "noir", filterIntensity = 100)), changes)
    }

    /** A category chip narrows the filter chips to that category. */
    @Test
    fun categoryNarrowsFilters() {
        showSheet(ClipLook())
        composeRule.onNodeWithTag(LOOK_CATEGORY_TAG_PREFIX + "all").assertIsSelected()
        composeRule.onNodeWithTag(LOOK_CATEGORY_TAG_PREFIX + "mono").performScrollTo().performClick()
        composeRule.onAllNodesWithTag(LOOK_FILTER_TAG_PREFIX + "honey").assertCountEquals(0)
        composeRule.onNodeWithTag(LOOK_FILTER_TAG_PREFIX + "noir").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Noir").assertIsDisplayed()
    }

    /** The intensity slider sends the finished value once. */
    @Test
    fun intensitySliderSends() {
        showSheet(warm)
        composeRule.onNodeWithText("Intensity 60").assertIsDisplayed()
        composeRule
            .onNodeWithTag(LOOK_INTENSITY_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(85f) }
        assertEquals(listOf(warm.copy(filterIntensity = 85)), changes)
    }

    /** An adjustment sends the finished value, and one close to 0 settles on 0. */
    @Test
    fun adjustSettlesOnNeutral() {
        showSheet(warm)
        openAdjust()
        composeRule
            .onNodeWithTag(LOOK_ADJUST_SLIDER_TAG_PREFIX + "contrast")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-40f) }
        composeRule
            .onNodeWithTag(LOOK_ADJUST_SLIDER_TAG_PREFIX + "brightness")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(listOf(warm.copy(contrast = -40), warm.copy(brightness = 0)), changes)
    }

    /** Only adjustments away from 0 offer a reset, which sends 0. */
    @Test
    fun resetClearsOneAdjustment() {
        showSheet(warm)
        openAdjust()
        composeRule.onAllNodesWithTag(LOOK_ADJUST_RESET_TAG_PREFIX + "contrast").assertCountEquals(0)
        composeRule
            .onNodeWithTag(LOOK_ADJUST_RESET_TAG_PREFIX + "temperature")
            .performScrollTo()
            .performClick()
        assertEquals(listOf(warm.copy(temperature = 0)), changes)
    }

    /** Apply to all sends the look and says how many clips it reached; reset all sends a plain look. */
    @Test
    fun applyToAllAndResetAll() {
        showSheet(warm, applied = 4)
        composeRule
            .onNodeWithTag(LOOK_APPLY_ALL_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Applied to 4 clips").assertIsDisplayed()
        composeRule.onNodeWithTag(LOOK_RESET_ALL_TAG).performScrollTo().performClick()
        assertEquals(listOf(warm), appliedAll)
        assertEquals(listOf(ClipLook()), changes)
    }

    /** A plain look has nothing to apply or reset. */
    @Test
    fun plainLookDisablesActions() {
        showSheet(ClipLook())
        composeRule.onNodeWithTag(LOOK_APPLY_ALL_TAG).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(LOOK_RESET_ALL_TAG).performScrollTo().assertIsNotEnabled()
    }

    /** Values within the detent settle on 0 and the rest pass through. */
    @Test
    fun detentSnapsNearZero() {
        assertEquals(listOf(0, 0, 0, 4, -4, 100), listOf(3, -3, 0, 4, -4, 100).map(::snapToNeutral))
    }
}

/*
 * JVM UI tests (Robolectric) for the transition sheet: kind chips, the frame-stepped duration slider, apply to all,
 * and the note shown when a cut has no hidden source to overlap.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Opens the real sheet at 30 fps and records each transition and apply to all it sends. */
@RunWith(AndroidJUnit4::class)
class TransitionSheetTest {
    /** Compose rule hosting the sheet in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<ClipTransition?>()

    private val appliedAll = mutableListOf<ClipTransition>()

    private val dissolve = ClipTransition(TransitionKind.DISSOLVE, SECOND / 2)

    /** Shows the sheet for a cut holding [transition] that allows up to [capMicros], having applied to [applied]. */
    private fun showSheet(
        transition: ClipTransition?,
        capMicros: Long = 2 * SECOND,
        applied: Int? = null,
    ) {
        composeRule.setContent {
            FramecraftTheme {
                TransitionSheet(
                    transition = transition,
                    capMicros = capMicros,
                    frameRate = FrameRate.FPS_30,
                    applied = applied,
                    onChange = { changes += it },
                    onApplyAll = { appliedAll += it },
                    onDismiss = {},
                )
            }
        }
    }

    /** A plain cut picks None; a kind chip sends that kind at the default length. */
    @Test
    fun chipSendsKindAtDefaultLength() {
        showSheet(null)
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "none").assertIsSelected()
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "dissolve").performScrollTo().performClick()
        assertEquals(listOf(dissolve), changes)
    }

    /** None clears a transition, and another kind keeps its length. */
    @Test
    fun chipsClearAndSwitchKind() {
        showSheet(dissolve)
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "dissolve").assertIsSelected()
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "zoom").performScrollTo().performClick()
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "none").performScrollTo().performClick()
        assertEquals(listOf(ClipTransition(TransitionKind.ZOOM, SECOND / 2), null), changes)
    }

    /** The slider lands on whole frames and sends the new length once. */
    @Test
    fun sliderSnapsToFrames() {
        showSheet(dissolve)
        composeRule.onNodeWithText("Duration: 0.5s").assertIsDisplayed()
        composeRule
            .onNodeWithTag(TRANSITION_SLIDER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1_010_000f) }
        assertEquals(listOf(dissolve.copy(durationMicros = 1_000_000L)), changes)
    }

    /** Apply to all sends the current transition and the sheet says how many cuts it reached. */
    @Test
    fun applyToAllSendsAndCounts() {
        showSheet(dissolve, applied = 3)
        composeRule
            .onNodeWithTag(TRANSITION_APPLY_ALL_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf(dissolve), appliedAll)
        composeRule.onNodeWithText("Applied to 3 cuts").assertIsDisplayed()
    }

    /** A cut without hidden source says why and only offers None. */
    @Test
    fun unavailableCutExplains() {
        showSheet(null, capMicros = 0L)
        composeRule.onNodeWithTag(TRANSITION_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "dissolve").assertIsNotEnabled()
        composeRule.onNodeWithTag(TRANSITION_CHIP_TAG_PREFIX + "none").assertIsEnabled()
    }
}

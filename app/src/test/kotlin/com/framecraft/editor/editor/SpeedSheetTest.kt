/*
 * JVM UI tests (Robolectric) for the speed sheet: the constant speed slider, presets, curve points, and the pitch
 * switch, each sent at once.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.SpeedPreset
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/** Opens the real sheet on a 4 s clip and records each speed and pitch it sends. */
@RunWith(AndroidJUnit4::class)
class SpeedSheetTest {
    /** Compose rule hosting the sheet in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Pair<ClipSpeed?, Boolean>>()

    private val plain = Clip("a", "v", 0L, 0L, 4 * SECOND)

    private val custom =
        Clip("a", "v", 0L, 0L, 4 * SECOND, speed = SpeedPreset.customStart, sourceDurationMicros = 4 * SECOND)

    /** Shows the sheet for [clip] with the playhead at [positionMicros]. */
    private fun showSheet(
        clip: Clip,
        positionMicros: Long = SECOND,
    ) {
        composeRule.setContent {
            FramecraftTheme {
                SpeedSheet(
                    clip = clip,
                    frameRate = FrameRate.FPS_30,
                    positionMicros = positionMicros,
                    onChange = { speed, pitch -> changes += speed to pitch },
                    onDismiss = {},
                )
            }
        }
    }

    /** A clip at normal speed opens on the Normal tab; the slider sends a constant speed and the switch the pitch. */
    @Test
    fun normalTabSendsSpeedAndPitch() {
        showSheet(plain)
        composeRule.onNodeWithTag(SPEED_NORMAL_TAB_TAG).assertIsSelected()
        composeRule.onNodeWithText("Duration: 4.0s → 4.0s").assertIsDisplayed()
        composeRule.onNodeWithTag(SPEED_SLIDER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(2f / 3f) }
        composeRule.onNodeWithTag(SPEED_PITCH_TAG).performScrollTo().performClick()
        assertEquals(listOf(ClipSpeed.Constant(10_000) to true, null to false), changes)
    }

    /** A preset chip sends its curve and the custom chip the flat starting curve. */
    @Test
    fun presetsSendCurves() {
        showSheet(plain)
        composeRule.onNodeWithTag(SPEED_CURVE_TAB_TAG).performClick()
        composeRule.onNodeWithTag(SPEED_PRESET_TAG_PREFIX + "hero").performScrollTo().performClick()
        composeRule.onNodeWithTag(SPEED_PRESET_TAG_PREFIX + "custom").performScrollTo().performClick()
        assertEquals(listOf(SpeedPreset.HERO.curve to true, SpeedPreset.customStart to true), changes)
    }

    /** Away from every point the button adds one at the playhead; Reset flattens the curve. */
    @Test
    fun curveAddsPointAtPlayhead() {
        showSheet(custom, positionMicros = 1_200_000L)
        composeRule.onNodeWithTag(SPEED_PRESET_TAG_PREFIX + "custom").assertIsSelected()
        composeRule.onNodeWithText("Add point").performScrollTo().assertIsEnabled().performClick()
        val added = changes.single().first as ClipSpeed.Curve
        assertEquals(6, added.points.size)
        assertEquals(30_000, added.points[2].position)
    }

    /** Near a point the button deletes it. */
    @Test
    fun curveDeletesPointNearPlayhead() {
        showSheet(custom, positionMicros = SECOND)
        composeRule.onNodeWithTag(SPEED_ADD_POINT_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Delete point").assertIsDisplayed()
        val deleted = changes.single().first as ClipSpeed.Curve
        assertEquals(listOf(0, 50_000, 75_000, 100_000), deleted.points.map { it.position })
    }

    /** The playhead maps to the clip's source and the point button rules match the editor's. */
    @Test
    fun playheadAndPointRules() {
        assertEquals(25_000, curvePositionAt(plain, FrameRate.FPS_30, SECOND))
        assertNull(curvePositionAt(plain, FrameRate.FPS_30, 4 * SECOND))
        val curve = SpeedPreset.customStart
        assertEquals(true to null, pointEditAt(curve, 0))
        assertEquals(false to null, pointEditAt(curve, null))
        assertEquals(true, pointEditAt(curve, 51_000).first)
    }
}

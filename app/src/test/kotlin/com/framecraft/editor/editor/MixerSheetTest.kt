/*
 * JVM UI tests (Robolectric) for the voiceover slice's controls: the mixer's lanes, sliders, and mute toggles, the
 * ducking sheet, and the recorder pill in each of its states.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.ui.theme.FramecraftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Shows the real mixer, ducking sheet, and recorder pill and records what they send. */
@RunWith(AndroidJUnit4::class)
class MixerSheetTest {
    /** Compose rule hosting the controls in a test activity. */
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private val song =
        MediaAsset(
            id = "song",
            uri = "content://media/song",
            kind = MediaKind.AUDIO,
            durationMicros = 4_000_000L,
            width = 1,
            height = 1,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    private val video =
        MediaAsset(
            id = "video",
            uri = "content://media/video",
            kind = MediaKind.VIDEO,
            durationMicros = 4_000_000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )

    private val bar =
        VoiceoverBarActions(
            onRecord = { events += "record" },
            onStop = { events += "stop" },
            onClose = { events += "close" },
        )

    /** Shows the recorder pill in [phase]. */
    private fun showBar(phase: VoiceoverPhase) {
        composeRule.setContent { FramecraftTheme { VoiceoverBar(phase, bar) } }
    }

    /** The mixer lists the main video and numbered audio lanes with clips, skipping empty lanes. */
    @Test
    fun lanesAreNamedInOrder() {
        var counter = 0
        val base = ProjectFactory.create("Mix", listOf(video), idGenerator = { "id-${counter++}" })
        val music = Clip("m", "song", 0L, 0L, 4_000_000L)
        val project =
            base.copy(
                assets = base.assets + song,
                tracks =
                    base.tracks +
                        Track("a1", TrackKind.AUDIO, false, listOf(music), volumePercent = 40) +
                        Track("a2", TrackKind.AUDIO, false, emptyList()) +
                        Track("a3", TrackKind.AUDIO, false, listOf(music.copy(id = "n"))),
            )
        val lanes = mixerLanesOf(project)
        assertEquals(
            listOf(
                MixerLane(base.tracks.first().id, R.string.mixer_main_video, 0, 100),
                MixerLane("a1", R.string.mixer_audio, 1, 40),
                MixerLane("a3", R.string.mixer_audio, 3, 100),
            ),
            lanes,
        )
    }

    /** Muting a lane commits silence, unmuting restores its volume, and a slider drag commits its value. */
    @Test
    fun lanesMuteAndSlide() {
        val lanes = listOf(MixerLane("music", R.string.mixer_audio, 1, 70))
        composeRule.setContent {
            FramecraftTheme { MixerSheet(lanes, onCommit = { id, volume -> events += "$id $volume" }, onDismiss = {}) }
        }
        composeRule.onNodeWithText("Audio 1 70%").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mute Audio 1").performClick()
        composeRule.onNodeWithContentDescription("Unmute Audio 1").performClick()
        composeRule
            .onNodeWithTag(mixerSliderTag("music"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        assertEquals(listOf("music 0", "music 70", "music 150"), events)
    }

    /** Turning ducking on commits the default depth; the depth slider stays dimmed while it is off. */
    @Test
    fun duckingTurnsOnAtDefault() {
        val audio = ClipAudio(volumePercent = 100, fadeInMicros = 0L, fadeOutMicros = 0L, durationMicros = 4_000_000L)
        composeRule.setContent {
            FramecraftTheme {
                DuckingSheet(audio, onCommit = { events += "duck ${it.autoDuckPercent}" }, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag(DUCK_SLIDER_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(DUCK_SWITCH_TAG).performClick()
        assertEquals(listOf("duck $DEFAULT_DUCK_PERCENT"), events)
    }

    /** The armed pill offers the headphones hint, record, and close. */
    @Test
    fun armedPillRecordsAndCloses() {
        showBar(VoiceoverPhase.Armed)
        composeRule.onNodeWithText("Use headphones for a clean take").assertIsDisplayed()
        composeRule.onNodeWithTag(VOICEOVER_RECORD_TAG).performClick()
        composeRule.onNodeWithTag(VOICEOVER_CLOSE_TAG).performClick()
        assertEquals(listOf("record", "close"), events)
    }

    /** The countdown shows the seconds left and can be cancelled. */
    @Test
    fun countdownShowsSecondsAndCancels() {
        showBar(VoiceoverPhase.Countdown(2))
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("stop"), events)
    }

    /** A running take shows its elapsed time and stops. */
    @Test
    fun recordingShowsTimeAndStops() {
        showBar(VoiceoverPhase.Recording(elapsedMicros = 65_000_000L, level = 0.3f))
        composeRule.onNodeWithText("1:05").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop recording").performClick()
        assertEquals(listOf("stop"), events)
    }
}

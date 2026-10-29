/*
 * JVM tests for the voiceover recorder's states: the countdown, the running take, and keeping, discarding, or
 * cutting short a take.
 */
package com.framecraft.editor.editor

import com.framecraft.editor.domain.model.AUDIO_ASSET_SIDE
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.media.CaptureStart
import com.framecraft.editor.media.CaptureTake
import com.framecraft.editor.media.VoiceoverCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives [VoiceoverRecording] on virtual time with a hand-written microphone. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceoverRecordingTest {
    private val asset =
        MediaAsset(
            id = "take",
            uri = "file:///data/projects/p/recordings/take-1.m4a",
            kind = MediaKind.AUDIO,
            durationMicros = 2_000_000L,
            width = AUDIO_ASSET_SIDE,
            height = AUDIO_ASSET_SIDE,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 10L, lastModifiedMillis = 1L),
        )

    /** A take that reports a fixed level and hands back [result] when finished. */
    private class RecordedTake(
        private val result: MediaAsset?,
    ) : CaptureTake {
        var finished = false
        var discarded = false

        /** Returns a steady half-scale level. */
        override fun level(): Float = 0.5f

        /** Records that the take was finished and returns [result]. */
        override fun finish(): MediaAsset? {
            finished = true
            return result
        }

        /** Records that the take was deleted. */
        override fun discard() {
            discarded = true
        }
    }

    /** A microphone that answers every start with [start] and remembers the projects it recorded for. */
    private class Microphone(
        private val start: CaptureStart,
    ) : VoiceoverCapture {
        val projects = mutableListOf<String>()

        /** Records [projectId] and returns [start]. */
        override fun begin(projectId: String): CaptureStart {
            projects += projectId
            return start
        }
    }

    /** Positions takes were placed at, by asset id. */
    private val placed = mutableListOf<Pair<String, Long>>()

    /** Returns a recorder on [microphone] in this test's virtual time; placing succeeds when [fits]. */
    private fun TestScope.recorder(
        microphone: VoiceoverCapture,
        fits: Boolean = true,
    ): VoiceoverRecording =
        VoiceoverRecording(
            scope = backgroundScope,
            capture = microphone,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            timeSource = testScheduler.timeSource,
        ) { take, startMicros ->
            placed += take.id to startMicros
            fits
        }

    /** Opens [recording], records for project "p" from two seconds, and runs past the countdown. */
    private fun TestScope.rollPastCountdown(recording: VoiceoverRecording) {
        recording.open()
        recording.record("p", 2_000_000L)
        advanceTimeBy(VOICEOVER_COUNTDOWN_SECONDS * 1_000L)
        runCurrent()
    }

    /** The countdown runs three, two, one before the microphone opens and the take starts metering. */
    @Test
    fun countsDownBeforeRecording() =
        runTest {
            val microphone = Microphone(CaptureStart.Started(RecordedTake(asset)))
            val recording = recorder(microphone)
            recording.open()
            assertEquals(VoiceoverPhase.Armed, recording.phase.value)
            recording.record("p", 2_000_000L)
            runCurrent()
            assertEquals(VoiceoverPhase.Countdown(3), recording.phase.value)
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(VoiceoverPhase.Countdown(2), recording.phase.value)
            assertTrue(microphone.projects.isEmpty())
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(listOf("p"), microphone.projects)
            assertEquals(VoiceoverPhase.Recording(0L, 0.5f), recording.phase.value)
            advanceTimeBy(1_500L)
            runCurrent()
            assertEquals(VoiceoverPhase.Recording(1_500_000L, 0.5f), recording.phase.value)
        }

    /** Stopping a take places it where recording was started and leaves the recorder open for another. */
    @Test
    fun stoppedTakeIsPlaced() =
        runTest {
            val take = RecordedTake(asset)
            val recording = recorder(Microphone(CaptureStart.Started(take)))
            rollPastCountdown(recording)
            advanceTimeBy(2_000L)
            recording.stop()
            assertEquals(VoiceoverPhase.Saving, recording.phase.value)
            runCurrent()
            assertTrue(take.finished)
            assertFalse(take.discarded)
            assertEquals(listOf("take" to 2_000_000L), placed)
            assertEquals(VoiceoverPhase.Armed, recording.phase.value)
            assertNull(recording.notice.value)
        }

    /** A take shorter than half a second is deleted and reported, never placed. */
    @Test
    fun shortTakeIsDiscarded() =
        runTest {
            val take = RecordedTake(asset)
            val recording = recorder(Microphone(CaptureStart.Started(take)))
            rollPastCountdown(recording)
            advanceTimeBy(300L)
            recording.stop()
            runCurrent()
            assertTrue(take.discarded)
            assertFalse(take.finished)
            assertTrue(placed.isEmpty())
            assertEquals(VoiceoverNotice.TOO_SHORT, recording.notice.value)
            recording.acknowledge()
            assertNull(recording.notice.value)
        }

    /** Stopping during the countdown cancels it before the microphone ever opens. */
    @Test
    fun stopDuringCountdownCancels() =
        runTest {
            val microphone = Microphone(CaptureStart.Started(RecordedTake(asset)))
            val recording = recorder(microphone)
            recording.open()
            recording.record("p", 0L)
            advanceTimeBy(1_000L)
            recording.stop()
            advanceTimeBy(5_000L)
            runCurrent()
            assertTrue(microphone.projects.isEmpty())
            assertEquals(VoiceoverPhase.Armed, recording.phase.value)
        }

    /** Full storage and a microphone that will not open are reported and leave the recorder armed. */
    @Test
    fun failedStartsAreReported() =
        runTest {
            val full = recorder(Microphone(CaptureStart.StorageFull))
            rollPastCountdown(full)
            assertEquals(VoiceoverNotice.STORAGE_FULL, full.notice.value)
            assertEquals(VoiceoverPhase.Armed, full.phase.value)
            val broken = recorder(Microphone(CaptureStart.Failed))
            rollPastCountdown(broken)
            assertEquals(VoiceoverNotice.MIC_FAILED, broken.notice.value)
        }

    /** Leaving the screen keeps what was recorded, reports it, and hides the recorder. */
    @Test
    fun interruptedTakeIsKept() =
        runTest {
            val take = RecordedTake(asset)
            val recording = recorder(Microphone(CaptureStart.Started(take)))
            rollPastCountdown(recording)
            advanceTimeBy(1_000L)
            recording.interrupt()
            runCurrent()
            assertEquals(listOf("take" to 2_000_000L), placed)
            assertEquals(VoiceoverNotice.SAVED, recording.notice.value)
            assertEquals(VoiceoverPhase.Closed, recording.phase.value)
        }

    /** A take with no free lane, or one the microphone could not finish, is reported and never kept. */
    @Test
    fun unplaceableTakesAreDeleted() =
        runTest {
            val crowded = RecordedTake(asset)
            val full = recorder(Microphone(CaptureStart.Started(crowded)), fits = false)
            rollPastCountdown(full)
            advanceTimeBy(1_000L)
            full.stop()
            runCurrent()
            assertTrue(crowded.discarded)
            assertEquals(VoiceoverNotice.NO_ROOM, full.notice.value)
            val unreadable = recorder(Microphone(CaptureStart.Started(RecordedTake(null))))
            rollPastCountdown(unreadable)
            advanceTimeBy(1_000L)
            unreadable.stop()
            runCurrent()
            assertEquals(VoiceoverNotice.MIC_FAILED, unreadable.notice.value)
        }

    /** Releasing deletes a running take at once and closes the recorder. */
    @Test
    fun releaseDeletesRunningTake() =
        runTest {
            val take = RecordedTake(asset)
            val recording = recorder(Microphone(CaptureStart.Started(take)))
            rollPastCountdown(recording)
            recording.release()
            assertTrue(take.discarded)
            assertEquals(VoiceoverPhase.Closed, recording.phase.value)
            runCurrent()
            assertTrue(placed.isEmpty())
        }
}

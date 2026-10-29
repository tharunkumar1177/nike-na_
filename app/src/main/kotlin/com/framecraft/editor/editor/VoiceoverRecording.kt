/*
 * State of the voiceover recorder: arming, the three-second countdown, the running take, and committing or
 * discarding it when it stops.
 */
package com.framecraft.editor.editor

import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.media.CaptureStart
import com.framecraft.editor.media.CaptureTake
import com.framecraft.editor.media.VoiceoverCapture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/** Seconds counted down before the microphone opens. */
const val VOICEOVER_COUNTDOWN_SECONDS = 3

/** Shortest take kept; shorter ones are discarded as accidental taps. */
const val MIN_TAKE_MICROS = 500_000L

/** Length of one countdown step in milliseconds. */
private const val COUNTDOWN_STEP_MILLIS = 1_000L

/** How often the elapsed time and input level refresh while recording, in milliseconds. */
private const val METER_INTERVAL_MILLIS = 100L

/** Where the recorder is. */
sealed interface VoiceoverPhase {
    /** The recorder is hidden. */
    data object Closed : VoiceoverPhase

    /** The recorder shows and waits for the record button. */
    data object Armed : VoiceoverPhase

    /** Recording starts after [secondsLeft] more seconds. */
    data class Countdown(
        val secondsLeft: Int,
    ) : VoiceoverPhase

    /** A take has run for [elapsedMicros], with the input at [level] from 0 to 1. */
    data class Recording(
        val elapsedMicros: Long,
        val level: Float,
    ) : VoiceoverPhase

    /** The take is being finished and added. */
    data object Saving : VoiceoverPhase
}

/** One-shot outcomes the editor reports about a take. */
enum class VoiceoverNotice {
    /** The take was shorter than [MIN_TAKE_MICROS] and was discarded. */
    TOO_SHORT,

    /** Too little storage was free to start recording. */
    STORAGE_FULL,

    /** The microphone could not be opened or the take could not be read. */
    MIC_FAILED,

    /** Every audio lane was busy where the take starts, so it was discarded. */
    NO_ROOM,

    /** Recording was cut short by leaving the app; what was recorded was kept. */
    SAVED,
}

/**
 * Runs voiceover takes through [capture], with file work on [ioDispatcher] and time from [timeSource]. A finished
 * take goes to [place] with the position it was started at; [place] returns false when it could not be added, and
 * the take is then deleted.
 */
class VoiceoverRecording(
    private val scope: CoroutineScope,
    private val capture: VoiceoverCapture,
    private val ioDispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val place: (MediaAsset, Long) -> Boolean,
) {
    private val mutablePhase = MutableStateFlow<VoiceoverPhase>(VoiceoverPhase.Closed)
    private val mutableNotice = MutableStateFlow<VoiceoverNotice?>(null)
    private var job: Job? = null
    private var take: CaptureTake? = null
    private var startMicros = 0L
    private var started: TimeSource.TimeMark? = null

    /** Where the recorder is now. */
    val phase: StateFlow<VoiceoverPhase> = mutablePhase.asStateFlow()

    /** The latest outcome to report, until [acknowledge] clears it. */
    val notice: StateFlow<VoiceoverNotice?> = mutableNotice.asStateFlow()

    /** Shows the recorder; ignored unless it is closed. */
    fun open() {
        if (mutablePhase.value == VoiceoverPhase.Closed) mutablePhase.value = VoiceoverPhase.Armed
    }

    /** Hides the recorder, cancelling a countdown; ignored while a take records or saves. */
    fun close() {
        if (mutablePhase.value == VoiceoverPhase.Armed || mutablePhase.value is VoiceoverPhase.Countdown) {
            job?.cancel()
            mutablePhase.value = VoiceoverPhase.Closed
        }
    }

    /**
     * Counts down, then records a take of project [projectId] that will be placed at [playheadMicros]; ignored
     * unless the recorder is armed.
     */
    fun record(
        projectId: String,
        playheadMicros: Long,
    ) {
        if (mutablePhase.value != VoiceoverPhase.Armed) return
        startMicros = playheadMicros
        job =
            scope.launch {
                for (secondsLeft in VOICEOVER_COUNTDOWN_SECONDS downTo 1) {
                    mutablePhase.value = VoiceoverPhase.Countdown(secondsLeft)
                    delay(COUNTDOWN_STEP_MILLIS)
                }
                val result = begin(projectId)
                if (result is CaptureStart.Started) {
                    meter(result.take)
                } else if (result != null) {
                    val failed = result == CaptureStart.StorageFull
                    mutableNotice.value = if (failed) VoiceoverNotice.STORAGE_FULL else VoiceoverNotice.MIC_FAILED
                    mutablePhase.value = VoiceoverPhase.Armed
                }
            }
    }

    /** Stops the take and adds it, or cancels a countdown; the recorder stays open for another take. */
    fun stop() {
        when (mutablePhase.value) {
            is VoiceoverPhase.Recording -> {
                finish(interrupted = false)
            }

            is VoiceoverPhase.Countdown -> {
                job?.cancel()
                mutablePhase.value = VoiceoverPhase.Armed
            }

            else -> {
                Unit
            }
        }
    }

    /** Keeps what a take recorded so far and hides the recorder, as when the app leaves the screen. */
    fun interrupt() {
        when (mutablePhase.value) {
            is VoiceoverPhase.Recording -> {
                finish(interrupted = true)
            }

            is VoiceoverPhase.Countdown -> {
                job?.cancel()
                mutablePhase.value = VoiceoverPhase.Closed
            }

            else -> {
                Unit
            }
        }
    }

    /** Clears the reported [notice]. */
    fun acknowledge() {
        mutableNotice.value = null
    }

    /** Stops everything at once and deletes a running take, as when the editor goes away for good. */
    fun release() {
        job?.cancel()
        take?.discard()
        take = null
        mutablePhase.value = VoiceoverPhase.Closed
    }

    /**
     * Opens the microphone for project [projectId]. Opening is not cancellable, so a take that opens after the
     * countdown was cancelled is deleted at once and null is returned.
     */
    private suspend fun begin(projectId: String): CaptureStart? {
        val result = withContext(ioDispatcher + NonCancellable) { capture.begin(projectId) }
        if (currentCoroutineContext().isActive) return result
        if (result is CaptureStart.Started) withContext(ioDispatcher + NonCancellable) { result.take.discard() }
        return null
    }

    /** Publishes the elapsed time and level of the [live] take until it stops. */
    private suspend fun meter(live: CaptureTake) {
        take = live
        val mark = timeSource.markNow()
        started = mark
        while (currentCoroutineContext().isActive) {
            mutablePhase.value = VoiceoverPhase.Recording(mark.elapsedNow().inWholeMicroseconds, live.level())
            delay(METER_INTERVAL_MILLIS)
        }
    }

    /**
     * Stops the running take and places it, discarding it when it is too short or does not fit. An [interrupted]
     * take hides the recorder and reports that it was kept.
     */
    private fun finish(interrupted: Boolean) {
        val live = take ?: return
        val elapsed = started?.elapsedNow()?.inWholeMicroseconds ?: 0L
        job?.cancel()
        take = null
        mutablePhase.value = VoiceoverPhase.Saving
        val tooShort = elapsed < MIN_TAKE_MICROS
        scope.launch {
            val asset =
                withContext(ioDispatcher) {
                    if (tooShort) {
                        live.discard()
                        null
                    } else {
                        live.finish()
                    }
                }
            val placed = asset != null && place(asset, startMicros)
            if (asset != null && !placed) withContext(ioDispatcher) { live.discard() }
            val notice =
                when {
                    tooShort -> VoiceoverNotice.TOO_SHORT
                    asset == null -> VoiceoverNotice.MIC_FAILED
                    !placed -> VoiceoverNotice.NO_ROOM
                    interrupted -> VoiceoverNotice.SAVED
                    else -> null
                }
            notice?.let { mutableNotice.value = it }
            mutablePhase.value = if (interrupted) VoiceoverPhase.Closed else VoiceoverPhase.Armed
        }
    }
}

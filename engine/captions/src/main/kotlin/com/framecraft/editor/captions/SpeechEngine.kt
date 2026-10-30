/*
 * The speech recognizer behind auto-captions: it hears 16 kHz mono samples and returns timed words, so the caption
 * pipeline and its tests never depend on a particular native engine.
 */
package com.framecraft.editor.captions

import java.io.Closeable

/** Sample rate, in hertz, of the audio every speech session reads. */
const val SPEECH_SAMPLE_RATE: Int = 16_000

/** A word the recognizer heard, timed in microseconds from the start of the audio fed to its session. */
data class HeardWord(
    val text: String,
    val startMicros: Long,
    val endMicros: Long,
) {
    init {
        require(text.isNotBlank()) { "A heard word must have text" }
        require(startMicros in 0..endMicros) { "Word times out of order: $startMicros to $endMicros" }
    }
}

/** Recognizes speech; one engine serves many sessions, one at a time. */
interface SpeechEngine : Closeable {
    /** Starts recognizing one continuous stretch of audio. */
    fun startSession(): SpeechSession
}

/** Recognition of one continuous stretch of 16 kHz mono 16-bit audio. */
interface SpeechSession : Closeable {
    /** Feeds the first [count] of [samples] and returns the words this audio completed. */
    fun accept(
        samples: ShortArray,
        count: Int,
    ): List<HeardWord>

    /** Ends the audio and returns the words still pending. */
    fun finish(): List<HeardWord>
}

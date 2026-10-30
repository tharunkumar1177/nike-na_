/*
 * The Vosk speech engine: loads a downloaded Vosk model from disk once and opens a word-timed recognizer per stretch
 * of audio. Loading the model is what first loads the native library, so nothing native runs until captions start.
 */
package com.framecraft.editor.captions

import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Recognizes speech with the Vosk model unpacked in [modelDir]. */
class VoskSpeechEngine(
    modelDir: File,
) : SpeechEngine {
    private val model: Model

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        model = Model(modelDir.absolutePath)
    }

    /** Returns a new word-timed session on the loaded model. */
    override fun startSession(): SpeechSession = VoskSession(Recognizer(model, SPEECH_SAMPLE_RATE.toFloat()))

    /** Frees the model's native memory. */
    override fun close() {
        model.close()
    }
}

/** One Vosk recognizer fed continuous audio. */
private class VoskSession(
    private val recognizer: Recognizer,
) : SpeechSession {
    init {
        recognizer.setWords(true)
    }

    /** Feeds samples and returns the words of any utterance Vosk finished with them. */
    override fun accept(
        samples: ShortArray,
        count: Int,
    ): List<HeardWord> =
        if (recognizer.acceptWaveForm(samples, count)) VoskResultParser.wordsOf(recognizer.result) else emptyList()

    /** Returns the words of the last, unfinished utterance. */
    override fun finish(): List<HeardWord> = VoskResultParser.wordsOf(recognizer.finalResult)

    /** Frees the recognizer's native memory. */
    override fun close() {
        recognizer.close()
    }
}

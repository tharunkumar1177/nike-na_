/*
 * Transcribes the sound of clips into timed words: decodes each clip's stretch of its file, feeds it to one
 * recognizer session, and times every word in the file's own timeline.
 */
package com.framecraft.editor.captions

import com.framecraft.editor.domain.model.CaptionWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Microseconds in one second. */
private const val MICROS_PER_SECOND = 1_000_000L

/** The sound of clip [clipId]: file [uri] from [fromMicros] until [untilMicros] of its own timeline. */
data class SpeechStretch(
    val clipId: String,
    val uri: String,
    val fromMicros: Long,
    val untilMicros: Long,
) {
    init {
        require(fromMicros >= 0 && fromMicros < untilMicros) { "Clip $clipId has no sound to transcribe" }
    }
}

/** Turns clip sound read by [audio] into words heard by the engine [openEngine] opens. */
class CaptionTranscriber(
    private val audio: SpeechAudioSource,
    private val openEngine: () -> SpeechEngine,
) {
    /**
     * Returns the words heard in each of [stretches], keyed by clip id and timed in each file's own timeline,
     * reporting the fraction of sound processed to [onProgress]. Clips whose sound cannot be read are left out.
     * Cancelling the calling coroutine stops the work and frees the engine.
     */
    suspend fun transcribe(
        stretches: List<SpeechStretch>,
        onProgress: (Float) -> Unit,
    ): Map<String, List<CaptionWord>> =
        withContext(Dispatchers.Default) {
            val total = stretches.sumOf { it.untilMicros - it.fromMicros }.coerceAtLeast(1L)
            var done = 0L
            openEngine().use { engine ->
                stretches.associate { stretch ->
                    val words = heard(engine, stretch) { micros -> onProgress((done + micros).toFloat() / total) }
                    done += stretch.untilMicros - stretch.fromMicros
                    onProgress(done.toFloat() / total)
                    stretch.clipId to words
                }
            }
        }

    /** Returns the words [engine] hears in [stretch], reporting the microseconds of sound fed so far to [fed]. */
    private suspend fun heard(
        engine: SpeechEngine,
        stretch: SpeechStretch,
        fed: (Long) -> Unit,
    ): List<CaptionWord> {
        val context = currentCoroutineContext()
        val words = mutableListOf<HeardWord>()
        var samples = 0L
        engine.startSession().use { session ->
            val sink = { chunk: ShortArray, count: Int ->
                words += session.accept(chunk, count)
                samples += count
                fed(samples * MICROS_PER_SECOND / SPEECH_SAMPLE_RATE)
            }
            val active = { context.isActive }
            val read = runCatching { audio.read(stretch.uri, stretch.fromMicros, stretch.untilMicros, active, sink) }
            context.ensureActive()
            if (read.isSuccess) words += session.finish() else words.clear()
        }
        return words.mapNotNull { word ->
            word.text.takeIf { text -> text.none { it.isWhitespace() } }?.let {
                CaptionWord(it, stretch.fromMicros + word.startMicros, stretch.fromMicros + word.endMicros)
            }
        }
    }
}

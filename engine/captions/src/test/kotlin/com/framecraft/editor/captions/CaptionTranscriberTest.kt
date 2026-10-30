/*
 * Unit tests for turning clip sound into timed words: resampling to the recognizer's format and timing words in
 * each file's own timeline.
 */
package com.framecraft.editor.captions

import com.framecraft.editor.domain.model.CaptionWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the resampler and the transcriber against a recognizer that hears a word per second of sound. */
class CaptionTranscriberTest {
    /** Hands over [SPEECH_SAMPLE_RATE] samples per second of the requested stretch, in half-second chunks. */
    private val audio =
        SpeechAudioSource { uri, from, until, _, sink ->
            require(uri != "broken") { "Unreadable" }
            val chunk = ShortArray(SPEECH_SAMPLE_RATE / 2)
            repeat(((until - from) / 500_000L).toInt()) { sink(chunk, chunk.size) }
        }

    /** A recognizer that hears the word "word" at the start of every full second of sound it is fed. */
    private val engine =
        object : SpeechEngine {
            override fun startSession(): SpeechSession =
                object : SpeechSession {
                    private var fed = 0L

                    /** Counts samples and hears a word each time another full second has been fed. */
                    override fun accept(
                        samples: ShortArray,
                        count: Int,
                    ): List<HeardWord> {
                        val before = fed / SPEECH_SAMPLE_RATE
                        fed += count
                        val after = fed / SPEECH_SAMPLE_RATE
                        return (before until after).map {
                            HeardWord("word", it * 1_000_000L, it * 1_000_000L + 400_000L)
                        }
                    }

                    /** Hears nothing more at the end. */
                    override fun finish(): List<HeardWord> = emptyList()

                    /** Holds nothing to free. */
                    override fun close() = Unit
                }

            /** Holds nothing to free. */
            override fun close() = Unit
        }

    /** 48 kHz stereo becomes 16 kHz mono with each output averaging the frames it covers. */
    @Test
    fun resamplerDownmixesAndAverages() {
        val resampler = SpeechResampler(48_000, 2)
        repeat(4_800) {
            resampler.add(0.5f)
            resampler.add(-0.5f)
        }
        resampler.finish()
        val out = ShortArray(4_000)
        assertEquals(1_600, resampler.drain(out))
        assertEquals(0, out.take(1_600).maxOf { kotlin.math.abs(it.toInt()) })
        val upsampled = SpeechResampler(8_000, 1)
        repeat(800) { upsampled.add(1f) }
        assertEquals(1_600, upsampled.drain(out))
        assertEquals(Short.MAX_VALUE, out[0])
    }

    /** Words are timed from each clip's stretch start, and unreadable clips yield no words. */
    @Test
    fun wordsAreTimedInTheFileTimeline() {
        val progress = mutableListOf<Float>()
        val stretches =
            listOf(
                SpeechStretch("A", "content://a", 2_000_000L, 4_000_000L),
                SpeechStretch("B", "broken", 0L, 1_000_000L),
            )
        val words = runBlocking { CaptionTranscriber(audio) { engine }.transcribe(stretches) { progress += it } }
        assertEquals(
            listOf(CaptionWord("word", 2_000_000L, 2_400_000L), CaptionWord("word", 3_000_000L, 3_400_000L)),
            words["A"],
        )
        assertEquals(emptyList<CaptionWord>(), words["B"])
        assertEquals(1f, progress.last())
    }
}

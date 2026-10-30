/*
 * Turns decoded sound of any rate and channel count into the 16 kHz mono 16-bit samples the recognizer reads.
 */
package com.framecraft.editor.captions

import kotlin.math.roundToInt

/** Full scale of a 16-bit sample. */
private const val SHORT_MAX = 32_767f

/** Output samples buffered before [SpeechResampler.drain] must be called. */
private const val OUTPUT_CAPACITY = 16_384

/** Free output slots kept so one input frame can always emit every sample it covers when upsampling. */
private const val HEADROOM = 8

/**
 * Downmixes interleaved samples with [channels] channels at [inputRate] Hz to mono and resamples them to
 * [SPEECH_SAMPLE_RATE] by averaging the input frames each output sample covers, which also keeps sound above the
 * output's Nyquist frequency from folding back into speech. Not thread-safe.
 */
class SpeechResampler(
    private val inputRate: Int,
    private val channels: Int,
) {
    private val step = inputRate.toDouble() / SPEECH_SAMPLE_RATE
    private val output = ShortArray(OUTPUT_CAPACITY)
    private var outputCount = 0
    private var channel = 0
    private var frameSum = 0f
    private var frames = 0L
    private var windowSum = 0f
    private var windowFrames = 0
    private var nextBoundary = step

    init {
        require(inputRate > 0 && channels > 0) { "Sound needs a positive rate and channel count" }
    }

    /** True when the output buffer is nearly full and must be drained before adding more. */
    val isFull: Boolean
        get() = outputCount > output.size - HEADROOM

    /** Adds one interleaved [sample] in -1 to 1; a frame is complete after one sample per channel. */
    fun add(sample: Float) {
        check(!isFull) { "Drain the resampler before adding more sound" }
        frameSum += sample
        channel++
        if (channel == channels) {
            windowSum += frameSum / channels
            windowFrames++
            frames++
            channel = 0
            frameSum = 0f
            if (frames >= nextBoundary) emit()
        }
    }

    /** Writes the average of the current window once per output sample it covers, then starts the next window. */
    private fun emit() {
        val mean = (windowSum / windowFrames).coerceIn(-1f, 1f)
        val value = (mean * SHORT_MAX).roundToInt().toShort()
        windowSum = 0f
        windowFrames = 0
        do {
            output[outputCount++] = value
            nextBoundary += step
        } while (frames >= nextBoundary && outputCount < output.size)
    }

    /** Copies the samples produced since the last drain into [target] from index 0 and returns how many there were. */
    fun drain(target: ShortArray): Int {
        val count = minOf(outputCount, target.size)
        output.copyInto(target, 0, 0, count)
        output.copyInto(output, 0, count, outputCount)
        outputCount -= count
        return count
    }

    /** Flushes a partly covered last output sample, for the end of the sound. */
    fun finish() {
        if (windowFrames > 0 && outputCount < output.size) {
            val mean = (windowSum / windowFrames).coerceIn(-1f, 1f)
            output[outputCount++] = (mean * SHORT_MAX).roundToInt().toShort()
            windowSum = 0f
            windowFrames = 0
        }
    }
}

/*
 * Audio processor that applies a clip's gain envelope to 16-bit sound without wrapping loud samples.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Microseconds in a second. */
private const val MICROS_PER_SECOND = 1_000_000L

/** Returns [sample] scaled by [gain] and rounded, held inside the 16-bit range instead of wrapping. */
internal fun clampedSample(
    sample: Short,
    gain: Float,
): Short = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

/**
 * Multiplies each 16-bit sample by [envelope] at its position, clamping to the sample range so gains above 1 clip
 * instead of wrapping around. The position restarts from the stream offset given on each flush, so seeks stay in
 * step with the envelope.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class ClampingGainProcessor(
    private val envelope: GainEnvelope,
) : BaseAudioProcessor() {
    private var framesRead = 0L

    /** Accepts 16-bit PCM unchanged; other encodings are refused. */
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    /** Writes the queued frames of [inputBuffer] with the gain of each frame's position applied. */
    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val output = replaceOutputBuffer(frames * format.bytesPerFrame)
        repeat(frames) {
            val gain = envelope.gainAt(framesRead * MICROS_PER_SECOND / format.sampleRate)
            repeat(format.channelCount) {
                output.putShort(clampedSample(inputBuffer.getShort(), gain))
            }
            framesRead++
        }
        output.flip()
    }

    /** Restarts the position at the stream offset in [streamMetadata]. */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val rate = inputAudioFormat.sampleRate
        framesRead = if (rate > 0) streamMetadata.positionOffsetUs.coerceAtLeast(0L) * rate / MICROS_PER_SECOND else 0L
    }

    /** Forgets the position. */
    override fun onReset() {
        framesRead = 0L
    }
}

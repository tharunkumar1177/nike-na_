/*
 * Builds the per-item sound chain: 16-bit samples, stereo channels, then the clip's volume, fades, and ducking.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.framecraft.editor.engine.plan.PlanSegment

/** Channel count every item is mixed to, so all sequences can be summed. */
private const val OUTPUT_CHANNELS = 2

/** Source channel counts mixed with constant gain; larger layouts use constant power. */
private val CONSTANT_GAIN_INPUTS = 1..2

/** Largest source channel count Media3 can mix down to stereo. */
private const val MAX_INPUT_CHANNELS = 6

/** Creates the audio processors for one edited item. */
@OptIn(markerClass = [UnstableApi::class])
internal object AudioItemEffects {
    /**
     * Returns the processors for [segment]: conversion to 16-bit stereo, then its gain envelope unless it plays at
     * the source level.
     */
    fun processorsFor(segment: PlanSegment.Media): List<AudioProcessor> {
        val envelope =
            GainEnvelope(
                segment.volumePercent,
                segment.fadeInMicros,
                segment.fadeOutMicros,
                segment.durationMicros,
                segment.duckWindows,
            )
        val gain = ClampingGainProcessor(envelope).takeUnless { envelope.isUnity }
        return listOfNotNull(ToInt16PcmAudioProcessor(), stereoMixer(), gain)
    }

    /** Returns a processor that mixes any supported channel layout to stereo. */
    private fun stereoMixer(): ChannelMixingAudioProcessor =
        ChannelMixingAudioProcessor().apply {
            for (channels in 1..MAX_INPUT_CHANNELS) {
                val matrix =
                    if (channels in CONSTANT_GAIN_INPUTS) {
                        ChannelMixingMatrix.createForConstantGain(channels, OUTPUT_CHANNELS)
                    } else {
                        ChannelMixingMatrix.createForConstantPower(channels, OUTPUT_CHANNELS)
                    }
                putChannelMixingMatrix(matrix)
            }
        }
}

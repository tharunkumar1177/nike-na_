/*
 * Turns a stream of decoded samples into a loudness envelope for drawing: RMS per fixed time bucket, scaled so the
 * loudest bucket is 1.
 */
package com.framecraft.editor.engine

import kotlin.math.sqrt

/** Length of one waveform bucket. */
const val WAVEFORM_BUCKET_MICROS: Long = 50_000L

/** Microseconds in a second. */
private const val MICROS_PER_SECOND = 1_000_000L

/**
 * A loudness envelope: [levels] holds one value from 0 to 1 per [bucketMicros] of source time, where 1 is the
 * loudest bucket of the file.
 */
class Waveform(
    val bucketMicros: Long,
    val levels: FloatArray,
) {
    /** Returns the level of the bucket holding [sourceMicros], or 0 outside the source. */
    fun levelAt(sourceMicros: Long): Float = levels.getOrElse((sourceMicros / bucketMicros).toInt()) { 0f }
}

/**
 * Accumulates interleaved samples of [channelCount] channels at [sampleRate] into RMS buckets of
 * [WAVEFORM_BUCKET_MICROS]. Samples run from -1 to 1. A final partial bucket counts as a bucket of its own.
 */
class WaveformBucketer(
    sampleRate: Int,
    channelCount: Int,
) {
    init {
        require(sampleRate > 0 && channelCount > 0) { "Invalid audio format: $sampleRate Hz, $channelCount channels" }
    }

    private val framesPerBucket = (sampleRate.toLong() * WAVEFORM_BUCKET_MICROS / MICROS_PER_SECOND).coerceAtLeast(1L)
    private val samplesPerBucket = framesPerBucket * channelCount
    private val levels = ArrayList<Float>()
    private var sumOfSquares = 0.0
    private var count = 0L

    /** Adds one sample, closing the current bucket when it is full. */
    fun add(sample: Float) {
        sumOfSquares += sample.toDouble() * sample
        count++
        if (count == samplesPerBucket) closeBucket()
    }

    /** Returns the envelope of everything added, normalized so the loudest bucket is 1; silence stays at 0. */
    fun finish(): Waveform {
        if (count > 0) closeBucket()
        val peak = levels.maxOrNull() ?: 0f
        val normalized = FloatArray(levels.size) { if (peak > 0f) levels[it] / peak else 0f }
        return Waveform(WAVEFORM_BUCKET_MICROS, normalized)
    }

    /** Stores the RMS of the current bucket and starts a new one. */
    private fun closeBucket() {
        levels += sqrt(sumOfSquares / count).toFloat()
        sumOfSquares = 0.0
        count = 0L
    }
}

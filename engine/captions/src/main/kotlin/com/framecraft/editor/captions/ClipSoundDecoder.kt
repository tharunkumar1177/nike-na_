/*
 * Decodes the sound of one stretch of a media file into recognizer samples, using the platform extractor and codecs.
 */
package com.framecraft.editor.captions

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** How long one codec call waits for a buffer. */
private const val CODEC_TIMEOUT_MICROS = 10_000L

/** Full scale of a 16-bit sample. */
private const val SHORT_SCALE = 32_768f

/** Microseconds in one second. */
private const val MICROS_PER_SECOND = 1_000_000L

/** Reads the sound of a stretch of a media file as recognizer samples. */
fun interface SpeechAudioSource {
    /**
     * Decodes the sound of [uri] from [fromMicros] until [untilMicros] of its own timeline and hands it to [sink] as
     * [SPEECH_SAMPLE_RATE] mono samples, in order, while [isActive] stays true. The first sample handed over plays
     * at [fromMicros]. Throws when the file has no readable sound.
     */
    fun read(
        uri: String,
        fromMicros: Long,
        untilMicros: Long,
        isActive: () -> Boolean,
        sink: (ShortArray, Int) -> Unit,
    )
}

/** Decodes clip sound with [MediaExtractor] and [MediaCodec]. */
class ClipSoundDecoder(
    context: Context,
) : SpeechAudioSource {
    private val appContext = context.applicationContext

    /** Decodes the first audio track of [uri] over the requested stretch. */
    override fun read(
        uri: String,
        fromMicros: Long,
        untilMicros: Long,
        isActive: () -> Boolean,
        sink: (ShortArray, Int) -> Unit,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(appContext, Uri.parse(uri), null)
            val track =
                requireNotNull(
                    (0 until extractor.trackCount).firstOrNull { index ->
                        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    },
                ) { "No audio track in $uri" }
            extractor.selectTrack(track)
            extractor.seekTo(fromMicros, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                SpanDrain(extractor, codec, fromMicros until untilMicros, isActive, sink).run()
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }
}

/**
 * Feeds [extractor] samples through [codec] and passes the decoded sound inside [window] through a
 * [SpeechResampler] to [sink], stopping at the end of the window, the end of the stream, or when [isActive] fails.
 */
private class SpanDrain(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    private val window: LongRange,
    private val isActive: () -> Boolean,
    private val sink: (ShortArray, Int) -> Unit,
) {
    private val info = MediaCodec.BufferInfo()
    private val chunk = ShortArray(SPEECH_SAMPLE_RATE / 2)
    private var inputDone = false
    private var resampler: SpeechResampler? = null
    private var rate = 0
    private var channels = 0
    private var isFloat = false

    /** Decodes until the window is covered and hands over the last samples. */
    fun run() {
        var done = false
        while (!done && isActive()) {
            if (!inputDone) feedInput()
            done = drainOutput()
        }
        resampler?.let {
            it.finish()
            flush(it)
        }
    }

    /** Queues the next compressed sample, or the end of the stream once the window has been read. */
    private fun feedInput() {
        val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
        if (index < 0) return
        val size = extractor.readSampleData(requireNotNull(codec.getInputBuffer(index)), 0)
        if (size < 0 || extractor.sampleTime > window.last) {
            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    /** Passes on one decoded buffer if available; returns true at the end of the stream or the window. */
    private fun drainOutput(): Boolean {
        val index = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_MICROS)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) configure(codec.outputFormat)
        if (index < 0) return false
        if (resampler == null) configure(codec.outputFormat)
        val buffer = requireNotNull(codec.getOutputBuffer(index))
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val ended = add(buffer.order(ByteOrder.nativeOrder()), info.presentationTimeUs)
        codec.releaseOutputBuffer(index, false)
        return ended || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    /** Starts resampling for the PCM layout of [format]. */
    private fun configure(format: MediaFormat) {
        if (resampler == null) {
            rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            resampler = SpeechResampler(rate, channels)
        }
        val encoding =
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
    }

    /**
     * Resamples the frames of [buffer], which starts at [startMicros], that fall inside the window; returns true
     * once a frame reaches the end of the window.
     */
    private fun add(
        buffer: ByteBuffer,
        startMicros: Long,
    ): Boolean {
        val target = checkNotNull(resampler) { "Output format is not known yet" }
        val samples = if (isFloat) FloatSamples(buffer) else ShortSamples(buffer)
        var frame = 0L
        var ended = false
        while (!ended && samples.remaining >= channels) {
            val time = startMicros + frame * MICROS_PER_SECOND / rate
            ended = time > window.last
            val inside = !ended && time >= window.first
            repeat(channels) {
                val value = samples.next()
                if (inside) push(target, value)
            }
            frame++
        }
        return ended
    }

    /** Adds [value] to [target], first handing on its samples when it has no room left. */
    private fun push(
        target: SpeechResampler,
        value: Float,
    ) {
        if (target.isFull) flush(target)
        target.add(value)
    }

    /** Hands every resampled sample to the sink. */
    private fun flush(target: SpeechResampler) {
        var count = target.drain(chunk)
        while (count > 0) {
            sink(chunk, count)
            count = target.drain(chunk)
        }
    }
}

/** Decoded samples read one at a time as -1 to 1. */
private sealed interface PcmSamples {
    /** Samples left to read. */
    val remaining: Int

    /** Returns the next sample. */
    fun next(): Float
}

/** Samples from 32-bit float PCM. */
private class FloatSamples(
    buffer: ByteBuffer,
) : PcmSamples {
    private val samples = buffer.asFloatBuffer()

    override val remaining: Int
        get() = samples.remaining()

    /** Returns the next float sample. */
    override fun next(): Float = samples.get()
}

/** Samples from 16-bit PCM. */
private class ShortSamples(
    buffer: ByteBuffer,
) : PcmSamples {
    private val samples = buffer.asShortBuffer()

    override val remaining: Int
        get() = samples.remaining()

    /** Returns the next 16-bit sample scaled to -1 to 1. */
    override fun next(): Float = samples.get() / SHORT_SCALE
}

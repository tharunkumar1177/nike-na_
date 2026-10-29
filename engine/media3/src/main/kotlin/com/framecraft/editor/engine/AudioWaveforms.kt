/*
 * Timeline waveforms: decodes each source's sound off the main thread into a loudness envelope, cached in memory.
 */
package com.framecraft.editor.engine

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Memory budget for cached waveforms. */
private const val CACHE_BYTES = 8 * 1024 * 1024

/** Bytes in one cached level. */
private const val BYTES_PER_LEVEL = 4

/** How long one codec call waits for a buffer. */
private const val CODEC_TIMEOUT_MICROS = 10_000L

/** Full scale of a 16-bit sample. */
private const val SHORT_SCALE = 32_768f

/** Log tag for waveform failures. */
private const val TAG = "AudioWaveforms"

/** Read side of a waveform cache used by the timeline. */
interface WaveformSource {
    /** Increments whenever a requested waveform becomes available or fails. */
    val version: StateFlow<Int>

    /** Returns the cached waveform of [uri], or null when not loaded yet. */
    fun get(uri: String): Waveform?

    /** Returns true when the waveform of [uri] could not be produced. */
    fun hasFailed(uri: String): Boolean

    /** Starts loading any of [uris] that are neither cached, failed, nor in flight. */
    fun request(uris: Collection<String>)
}

/** Produces waveforms one source at a time. Must be created, used, and closed on the main thread. */
class AudioWaveforms(
    context: Context,
) : WaveformSource {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val decodeLock = Mutex()
    private val pending = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private val cache =
        object : LruCache<String, Waveform>(CACHE_BYTES) {
            /** Weighs entries by their level memory. */
            override fun sizeOf(
                key: String,
                value: Waveform,
            ): Int = value.levels.size * BYTES_PER_LEVEL
        }
    private val mutableVersion = MutableStateFlow(0)

    override val version: StateFlow<Int> = mutableVersion.asStateFlow()

    /** Returns the cached waveform of [uri]. */
    override fun get(uri: String): Waveform? = cache.get(uri)

    /** Returns true when [uri] failed to load. */
    override fun hasFailed(uri: String): Boolean = uri in failed

    /** Loads missing waveforms one at a time in request order. */
    override fun request(uris: Collection<String>) {
        uris
            .filter { cache.get(it) == null && it !in failed && it !in pending }
            .forEach { uri ->
                pending += uri
                scope.launch { load(uri) }
            }
    }

    /** Cancels outstanding work and drops cached waveforms. */
    fun close() {
        scope.cancel()
        cache.evictAll()
    }

    /** Loads [uri] into the cache, or records a failure. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(uri: String) {
        try {
            val waveform = decodeLock.withLock { withContext(Dispatchers.IO) { decode(uri) } }
            cache.put(uri, waveform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Extractors and codecs surface unreadable or unsupported media through many exception types.
            Log.w(TAG, "Waveform failed for $uri", e)
            failed += uri
        } finally {
            pending -= uri
            mutableVersion.update { it + 1 }
        }
    }

    /** Decodes the first audio track of [uri] into a waveform. */
    private fun decode(uri: String): Waveform {
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
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return PcmDrain(extractor, codec).run()
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }
}

/** Feeds [extractor] samples through [codec] and buckets the decoded PCM until the end of the stream. */
private class PcmDrain(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
) {
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var bucketer: WaveformBucketer? = null
    private var isFloat = false

    /** Decodes everything and returns the waveform. */
    fun run(): Waveform {
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) feedInput()
            outputDone = drainOutput()
        }
        return checkNotNull(bucketer) { "Decoder produced no audio" }.finish()
    }

    /** Queues the next compressed sample, or the end of the stream. */
    private fun feedInput() {
        val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
        if (index < 0) return
        val size = extractor.readSampleData(requireNotNull(codec.getInputBuffer(index)), 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    /** Buckets one decoded buffer if available; returns true at the end of the stream. */
    private fun drainOutput(): Boolean {
        val index = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_MICROS)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) configure(codec.outputFormat)
        if (index < 0) return false
        if (bucketer == null) configure(codec.outputFormat)
        val buffer = requireNotNull(codec.getOutputBuffer(index))
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        add(buffer.order(ByteOrder.nativeOrder()))
        codec.releaseOutputBuffer(index, false)
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    /** Starts bucketing for the PCM layout of [format]. */
    private fun configure(format: MediaFormat) {
        if (bucketer == null) {
            bucketer =
                WaveformBucketer(
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                )
        }
        val encoding =
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
    }

    /** Adds every sample in [buffer] to the bucketer. */
    private fun add(buffer: ByteBuffer) {
        val target = checkNotNull(bucketer) { "Output format is not known yet" }
        if (isFloat) {
            val samples = buffer.asFloatBuffer()
            while (samples.hasRemaining()) {
                target.add(samples.get())
            }
        } else {
            val samples = buffer.asShortBuffer()
            while (samples.hasRemaining()) {
                target.add(samples.get() / SHORT_SCALE)
            }
        }
    }
}

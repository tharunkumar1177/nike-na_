/*
 * Timeline thumbnails: video frames via Media3 FrameExtractor and downscaled images, cached in memory.
 */
package com.framecraft.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.inspector.frame.FrameExtractor
import com.framecraft.editor.domain.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Memory budget for cached thumbnails. */
private const val CACHE_BYTES = 32 * 1024 * 1024

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Log tag for thumbnail failures. */
private const val TAG = "ClipThumbnails"

/** Identifies one thumbnail: a frame of [uri] at [sourceMicros] (ignored for images). */
data class ThumbnailKey(
    val uri: String,
    val kind: MediaKind,
    val sourceMicros: Long,
)

/** Read side of a thumbnail cache used by the timeline. */
interface ThumbnailSource {
    /** Increments whenever a requested thumbnail becomes available or fails. */
    val version: StateFlow<Int>

    /** Returns the cached thumbnail for [key], or null when not loaded yet. */
    fun get(key: ThumbnailKey): Bitmap?

    /** Returns true when [key] could not be produced. */
    fun hasFailed(key: ThumbnailKey): Boolean

    /** Starts loading any of [keys] that are neither cached, failed, nor in flight. */
    fun request(keys: Collection<ThumbnailKey>)
}

/**
 * Produces thumbnails [heightPx] tall. Must be created, used, and closed on the main thread,
 * which is also where FrameExtractor instances live.
 */
@OptIn(markerClass = [UnstableApi::class])
class ClipThumbnails(
    context: Context,
    private val heightPx: Int,
) : ThumbnailSource {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val extractionLock = Mutex()
    private val extractors = mutableMapOf<String, FrameExtractor>()
    private val pending = mutableSetOf<ThumbnailKey>()
    private val failed = mutableSetOf<ThumbnailKey>()
    private val cache =
        object : LruCache<ThumbnailKey, Bitmap>(CACHE_BYTES) {
            /** Weighs entries by their pixel memory. */
            override fun sizeOf(
                key: ThumbnailKey,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val mutableVersion = MutableStateFlow(0)

    override val version: StateFlow<Int> = mutableVersion.asStateFlow()

    /** Returns the cached thumbnail for [key]. */
    override fun get(key: ThumbnailKey): Bitmap? = cache.get(key)

    /** Returns true when [key] failed to load. */
    override fun hasFailed(key: ThumbnailKey): Boolean = key in failed

    /** Loads missing thumbnails one at a time in request order. */
    override fun request(keys: Collection<ThumbnailKey>) {
        keys
            .filter { cache.get(it) == null && it !in failed && it !in pending }
            .forEach { key ->
                pending += key
                scope.launch { load(key) }
            }
    }

    /** Cancels outstanding work and releases decoders and cached bitmaps. */
    fun close() {
        scope.cancel()
        extractors.values.forEach { it.close() }
        extractors.clear()
        cache.evictAll()
    }

    /** Loads [key] into the cache, or records a failure. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(key: ThumbnailKey) {
        try {
            val bitmap = extractionLock.withLock { produce(key) }
            cache.put(key, bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Decoders surface unreadable or unsupported media through many exception types.
            Log.w(TAG, "Thumbnail failed for ${key.uri} at ${key.sourceMicros}us", e)
            failed += key
        } finally {
            pending -= key
            mutableVersion.update { it + 1 }
        }
    }

    /** Decodes the bitmap for [key]; sound and text have no source picture, so their keys fail. */
    private suspend fun produce(key: ThumbnailKey): Bitmap =
        when (key.kind) {
            MediaKind.VIDEO -> extractorFor(key.uri).getFrame(key.sourceMicros / MICROS_PER_MILLI).await().bitmap
            MediaKind.IMAGE -> withContext(Dispatchers.IO) { decodeImage(key.uri) }
            MediaKind.AUDIO -> throw IllegalArgumentException("Audio ${key.uri} has no thumbnail")
            MediaKind.TEXT -> throw IllegalArgumentException("Text has no thumbnail")
        }

    /** Returns the frame extractor for [uri], creating it on first use. */
    private fun extractorFor(uri: String): FrameExtractor =
        extractors.getOrPut(uri) {
            FrameExtractor
                .Builder(appContext, MediaItem.fromUri(uri))
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setEffects(listOf(Presentation.createForHeight(heightPx)))
                .build()
        }

    /** Decodes [uri] scaled so its height is [heightPx]. */
    private fun decodeImage(uri: String): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, Uri.parse(uri))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = (info.size.width.toLong() * heightPx / info.size.height).toInt().coerceAtLeast(1)
            decoder.setTargetSize(width, heightPx)
        }
    }
}

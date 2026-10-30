/*
 * Reads one source frame of a clip as plain pixels, so the chroma key's eyedropper can pick the exact color the key
 * shader will see, before any look colors it.
 */
package com.framecraft.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.inspector.frame.FrameExtractor
import com.framecraft.editor.domain.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/** Microseconds in a millisecond. */
private const val PROBE_MICROS_PER_MILLI = 1_000L

/** Log tag for probe failures. */
private const val PROBE_TAG = "KeyColorProbe"

/** A picture [width] by [height] pixels, its opaque or translucent ARGB colors row by row from the top left. */
class FramePixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Frame must have pixels, was $width by $height" }
        require(argb.size == width * height) { "Frame needs ${width * height} pixels, has ${argb.size}" }
    }

    /** Returns the color at column [x] and row [y], clamped onto the frame. */
    fun at(
        x: Int,
        y: Int,
    ): Int = argb[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
}

/** Reads source frames for the eyedropper. */
interface KeyColorProbe {
    /** Returns the upright picture of [uri], a [kind] source, at [sourceMicros] (ignored for images), or null. */
    suspend fun frameOf(
        uri: String,
        kind: MediaKind,
        sourceMicros: Long,
    ): FramePixels?
}

/**
 * Reads frames [heightPx] tall: video through a one-shot Media3 FrameExtractor, images through ImageDecoder. It must
 * be called on the main thread, where FrameExtractor lives.
 */
@OptIn(markerClass = [UnstableApi::class])
class FrameExtractorKeyColorProbe(
    context: Context,
    private val heightPx: Int,
) : KeyColorProbe {
    private val appContext = context.applicationContext

    /** Returns the frame, or null when the source cannot be read. */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun frameOf(
        uri: String,
        kind: MediaKind,
        sourceMicros: Long,
    ): FramePixels? =
        try {
            when (kind) {
                MediaKind.VIDEO -> pixelsOf(videoFrame(uri, sourceMicros))
                MediaKind.IMAGE -> withContext(Dispatchers.IO) { pixelsOf(decodeImage(uri)) }
                MediaKind.AUDIO, MediaKind.TEXT -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Decoders surface unreadable or unsupported media through many exception types.
            Log.w(PROBE_TAG, "Key color probe failed for $uri at ${sourceMicros}us", e)
            null
        }

    /** Returns the video frame of [uri] nearest [sourceMicros], scaled to [heightPx]. */
    private suspend fun videoFrame(
        uri: String,
        sourceMicros: Long,
    ): Bitmap {
        val extractor =
            FrameExtractor
                .Builder(appContext, MediaItem.fromUri(uri))
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setEffects(listOf(Presentation.createForHeight(heightPx)))
                .build()
        return try {
            extractor.getFrame(sourceMicros / PROBE_MICROS_PER_MILLI).await().bitmap
        } finally {
            extractor.close()
        }
    }

    /** Decodes [uri] in software, scaled so its height is [heightPx]. */
    private fun decodeImage(uri: String): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, Uri.parse(uri))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = (info.size.width.toLong() * heightPx / info.size.height).toInt().coerceAtLeast(1)
            decoder.setTargetSize(width, heightPx)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** Returns the pixels of [bitmap], copying it into software memory first when it lives on the GPU. */
    private fun pixelsOf(bitmap: Bitmap): FramePixels {
        val readable =
            if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val argb = IntArray(readable.width * readable.height)
        readable.getPixels(argb, 0, readable.width, 0, 0, readable.width, readable.height)
        return FramePixels(readable.width, readable.height, argb)
    }
}

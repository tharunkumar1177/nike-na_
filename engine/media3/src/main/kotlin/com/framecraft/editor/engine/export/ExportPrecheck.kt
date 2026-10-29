/*
 * Checks that run before an export starts: something to export, an H.264 encoder for the size, and free space.
 */
package com.framecraft.editor.engine.export

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.StatFs
import com.framecraft.editor.domain.export.ExportSize

/** Free space needed, as tenths of the estimated size: the cache file and its gallery copy, plus a margin. */
private const val SPACE_FACTOR_TENTHS = 22L

/** Tenths in one unit. */
private const val TENTHS = 10L

/** Pre-export checks. */
internal object ExportPrecheck {
    /**
     * Returns why an export of [durationMicros] at [size], expected to take [estimateBytes], cannot start,
     * or null when it can.
     */
    fun check(
        context: Context,
        durationMicros: Long,
        size: ExportSize,
        estimateBytes: Long,
    ): ExportFailure? =
        when {
            durationMicros <= 0L -> ExportFailure.NOTHING_TO_EXPORT
            !hasEncoder(size) -> ExportFailure.UNSUPPORTED_CODEC
            !hasSpace(context, estimateBytes) -> ExportFailure.STORAGE_FULL
            else -> null
        }

    /** Returns true if an H.264 encoder supports [size] in either orientation; the encoder may rotate. */
    private fun hasEncoder(size: ExportSize): Boolean {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return listOf(size.width to size.height, size.height to size.width).any { (width, height) ->
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            codecs.findEncoderForFormat(format) != null
        }
    }

    /** Returns true if the cache volume can hold the export file and its gallery copy. */
    private fun hasSpace(
        context: Context,
        estimateBytes: Long,
    ): Boolean = StatFs(context.cacheDir.path).availableBytes >= estimateBytes * SPACE_FACTOR_TENTHS / TENTHS
}

/*
 * Turns picked content URIs into domain media assets: keeps read access, detects type, and probes metadata.
 */
package com.framecraft.editor.media

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.SourceFingerprint
import java.io.IOException

/** Outcome of importing one URI. */
sealed interface ImportResult {
    /** The URI was readable and became [asset]. */
    data class Imported(
        val asset: MediaAsset,
    ) : ImportResult

    /** The URI could not be used, for [reason]. */
    data class Failed(
        val uri: String,
        val reason: String,
    ) : ImportResult
}

/** Imports a single content URI. Blocking; call off the main thread. */
fun interface MediaImporter {
    /** Imports [uri] and reports whether it became an asset. */
    fun importUri(uri: String): ImportResult
}

/** Pixel dimensions of an image. */
private data class ImageBounds(
    val width: Int,
    val height: Int,
)

/** [MediaImporter] backed by the content resolver and platform metadata APIs. */
class AndroidMediaImporter(
    private val context: Context,
    private val idGenerator: IdGenerator,
) : MediaImporter {
    private val resolver = context.contentResolver

    /** Keeps read access to [uri], then probes it as a video or image according to its MIME type. */
    override fun importUri(uri: String): ImportResult {
        val parsed = Uri.parse(uri)
        keepReadAccess(parsed)
        val mimeType = resolver.getType(parsed).orEmpty()
        return when {
            mimeType.startsWith("video/") -> importVideo(uri, parsed)
            mimeType.startsWith("image/") -> importImage(uri, parsed)
            else -> ImportResult.Failed(uri, "Unsupported type '$mimeType'")
        }
    }

    /** Requests a persistable read grant so the project can reopen this media later. */
    private fun keepReadAccess(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (ignored: SecurityException) {
            // The provider offers no persistable grant; the session grant still allows reading now.
        }
    }

    /** Creates a video asset from probed metadata. */
    private fun importVideo(
        uri: String,
        parsed: Uri,
    ): ImportResult =
        probeVideo(parsed).fold(
            onSuccess = { metadata ->
                ImportResult.Imported(
                    MediaAsset(
                        id = idGenerator.next(),
                        uri = uri,
                        kind = MediaKind.VIDEO,
                        durationMicros = metadata.durationMicros,
                        width = metadata.width,
                        height = metadata.height,
                        rotationDegrees = metadata.rotationDegrees,
                        hasAudio = metadata.hasAudio,
                        fingerprint = fingerprint(parsed),
                    ),
                )
            },
            onFailure = { ImportResult.Failed(uri, describe(it)) },
        )

    /** Creates an image asset from its decoded bounds. */
    private fun importImage(
        uri: String,
        parsed: Uri,
    ): ImportResult =
        readImageBounds(parsed).fold(
            onSuccess = { bounds ->
                ImportResult.Imported(
                    MediaAsset(
                        id = idGenerator.next(),
                        uri = uri,
                        kind = MediaKind.IMAGE,
                        durationMicros = null,
                        width = bounds.width,
                        height = bounds.height,
                        rotationDegrees = 0,
                        hasAudio = false,
                        fingerprint = fingerprint(parsed),
                    ),
                )
            },
            onFailure = { ImportResult.Failed(uri, describe(it)) },
        )

    /** Reads video metadata with MediaMetadataRetriever. */
    @Suppress("TooGenericExceptionCaught")
    private fun probeVideo(uri: Uri): Result<VideoMetadata> =
        try {
            val metadata =
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    VideoMetadataParser.parse(
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                        rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                        hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO),
                    )
                }
            if (metadata != null) {
                Result.success(metadata)
            } else {
                Result.failure(IOException("Missing or invalid video metadata"))
            }
        } catch (e: RuntimeException) {
            // setDataSource reports unreadable media as a bare RuntimeException.
            Result.failure(e)
        }

    /** Reads image dimensions without decoding pixels. */
    private fun readImageBounds(uri: Uri): Result<ImageBounds> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open image")
            stream.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) {
                Result.success(ImageBounds(options.outWidth, options.outHeight))
            } else {
                Result.failure(IOException("Unreadable image"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /** Reads the file size reported by the provider, when available. */
    private fun fingerprint(uri: Uri): SourceFingerprint {
        val size =
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        return SourceFingerprint(sizeBytes = size, lastModifiedMillis = null)
    }

    /** Returns a short human-readable cause for [error]. */
    private fun describe(error: Throwable): String = error.message ?: error.javaClass.simpleName
}

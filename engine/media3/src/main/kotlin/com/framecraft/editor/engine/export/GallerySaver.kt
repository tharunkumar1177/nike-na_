/*
 * Saves a finished export into the shared Movies/Framecraft collection, invisible to other apps until complete.
 */
package com.framecraft.editor.engine.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Folder under the shared Movies directory that holds exports. */
private const val EXPORT_FOLDER = "Framecraft"

/** MIME type of every export. */
private const val VIDEO_MP4 = "video/mp4"

/** Longest project-name part of a file name, in characters. */
private const val MAX_NAME_LENGTH = 60

/** Characters that are not safe in a shared file name. */
private val UNSAFE_NAME_CHARS = Regex("[^\\p{L}\\p{N} _-]")

/** Timestamp appended to file names so repeated exports never collide. */
private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")

/** Copies export files into MediaStore. Calls block and must run off the main thread. */
internal class GallerySaver(
    context: Context,
) {
    private val resolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Copies [source] into the gallery as [displayName] and returns its content URI. The entry stays pending,
     * and hidden, until the copy completes; a failed copy removes it.
     */
    @Throws(IOException::class)
    fun save(
        source: File,
        displayName: String,
    ): Uri {
        val uri = insertPending(displayName)
        try {
            copy(source, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (error: IOException) {
            delete(uri)
            throw error
        }
        return uri
    }

    /** Removes the saved entry [uri]. */
    fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    /** Inserts a pending, hidden gallery entry named [displayName]. */
    private fun insertPending(displayName: String): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MP4)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$EXPORT_FOLDER")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        return resolver.insert(collection, values) ?: throw IOException("The gallery refused a new video")
    }

    /** Copies the bytes of [source] into the entry [uri]. */
    private fun copy(
        source: File,
        uri: Uri,
    ) {
        val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write to $uri")
        output.use { out -> source.inputStream().use { it.copyTo(out) } }
    }

    /** File naming. */
    companion object {
        /** Returns the file name for an export of [projectName] made at [now]. */
        fun displayNameFor(
            projectName: String,
            now: LocalDateTime,
        ): String {
            val cleaned = projectName.replace(UNSAFE_NAME_CHARS, "_").trim()
            val safe = cleaned.take(MAX_NAME_LENGTH).ifBlank { EXPORT_FOLDER }
            return "$safe ${STAMP_FORMAT.format(now)}.mp4"
        }
    }
}

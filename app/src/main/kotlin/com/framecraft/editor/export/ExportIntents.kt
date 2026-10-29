/*
 * Intents that hand a saved export to other apps: share it or play it.
 */
package com.framecraft.editor.export

import android.content.Intent
import android.net.Uri

/** MIME type of every export. */
private const val VIDEO_MP4 = "video/mp4"

/** Builds intents for a saved export; each grants the receiving app read access to the video only. */
object ExportIntents {
    /** Returns a share-sheet intent for the video at [uri], titled [chooserTitle]. */
    fun share(
        uri: Uri,
        chooserTitle: String,
    ): Intent {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(VIDEO_MP4)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, chooserTitle)
    }

    /** Returns an intent that plays the video at [uri] in the user's video player. */
    fun play(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, VIDEO_MP4)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}

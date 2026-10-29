/*
 * The caches the timeline draws an open project from: picture thumbnails and sound waveforms.
 */
package com.framecraft.editor.editor

import com.framecraft.editor.engine.AudioWaveforms
import com.framecraft.editor.engine.ClipThumbnails

/** Owns the [thumbnails] and [waveforms] of one open project; both live on the main thread. */
class TimelineMedia(
    val thumbnails: ClipThumbnails,
    val waveforms: AudioWaveforms,
) {
    /** Stops loading and drops everything cached. */
    fun close() {
        thumbnails.close()
        waveforms.close()
    }
}

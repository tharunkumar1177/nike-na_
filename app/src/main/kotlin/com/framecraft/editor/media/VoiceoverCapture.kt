/*
 * Microphone capture for voiceover takes: starting a take in a project's recordings folder, reading its level, and
 * finishing it into a sound asset.
 */
package com.framecraft.editor.media

import com.framecraft.editor.domain.model.MediaAsset

/** Records voiceover takes from the microphone into a project's recordings folder. */
interface VoiceoverCapture {
    /** Starts recording a new take for project [projectId]; the result says whether the microphone is now live. */
    fun begin(projectId: String): CaptureStart
}

/** How starting a take went. */
sealed interface CaptureStart {
    /** The microphone is recording into [take]. */
    data class Started(
        val take: CaptureTake,
    ) : CaptureStart

    /** Too little free storage is left to record safely. */
    data object StorageFull : CaptureStart

    /** The microphone could not be opened. */
    data object Failed : CaptureStart
}

/** A take that is recording. */
interface CaptureTake {
    /** Loudness of the input since the previous call, from 0 (silence) to 1 (full scale). */
    fun level(): Float

    /**
     * Stops recording and returns the take as a sound asset, or null when nothing usable was recorded. A null
     * take leaves no file behind.
     */
    fun finish(): MediaAsset?

    /** Stops recording and deletes the take. */
    fun discard()
}

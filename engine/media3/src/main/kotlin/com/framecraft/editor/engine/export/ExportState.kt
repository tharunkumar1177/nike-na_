/*
 * Media3-free state of one export, from preparation to a saved video or a failure reason.
 */
package com.framecraft.editor.engine.export

/** Why an export did not produce a video. */
enum class ExportFailure {
    NOTHING_TO_EXPORT,
    UNSUPPORTED_CODEC,
    SOURCE_MISSING,
    DECODE_FAILED,
    ENCODE_FAILED,
    STORAGE_FULL,
    UNKNOWN,
}

/** Progress of an export. */
sealed interface ExportState {
    /** No export has started. */
    data object Idle : ExportState

    /** Checks are running and the encoder is starting. */
    data object Preparing : ExportState

    /** Encoding; [percent] runs from 0 to 100, and 100 means the video is being saved to the gallery. */
    data class Running(
        val percent: Int,
    ) : ExportState

    /** The video was saved at [contentUri] and is [sizeBytes] long. */
    data class Succeeded(
        val contentUri: String,
        val sizeBytes: Long,
    ) : ExportState

    /** The export stopped because of [reason]; nothing was saved. */
    data class Failed(
        val reason: ExportFailure,
    ) : ExportState

    /** The user stopped the export; nothing was saved. */
    data object Cancelled : ExportState
}

/** Whether an export is preparing or running, so another one cannot start. */
val ExportState.isInProgress: Boolean
    get() = this is ExportState.Preparing || this is ExportState.Running

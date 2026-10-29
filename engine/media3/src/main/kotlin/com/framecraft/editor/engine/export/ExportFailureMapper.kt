/*
 * Maps Media3 export error codes and their causes to the failure reasons shown to the user.
 */
package com.framecraft.editor.engine.export

import java.io.IOException

/** First code of Media3's input and output error family. */
private const val IO_FAMILY = 2000

/** First code of Media3's decoding error family. */
private const val DECODING_FAMILY = 3000

/** First code of Media3's encoding error family. */
private const val ENCODING_FAMILY = 4000

/** First code after the video, audio, and muxing families. */
private const val PROCESSING_FAMILY_END = 8000

/** Codes are grouped by thousands into families. */
private const val FAMILY_SIZE = 1000

/** Media3 `ERROR_CODE_DECODING_FORMAT_UNSUPPORTED` and `ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED`. */
private val UNSUPPORTED_FORMAT_CODES = setOf(3003, 4003)

/** Text the platform puts in I/O errors when a disk is full. */
private val NO_SPACE_MARKERS = listOf("ENOSPC", "No space left")

/** How many causes are inspected, guarding against cause cycles. */
private const val MAX_CAUSE_DEPTH = 10

/**
 * Turns Media3 `ExportException` codes into [ExportFailure]. Codes are taken as plain integers so the mapping
 * is testable without a device. A full disk wins over the code, because it surfaces as an I/O or muxing error.
 */
internal object ExportFailureMapper {
    /** Returns the failure for Media3 error [errorCode] raised with [cause]. */
    fun map(
        errorCode: Int,
        cause: Throwable?,
    ): ExportFailure =
        when {
            isNoSpace(cause) -> ExportFailure.STORAGE_FULL
            errorCode in UNSUPPORTED_FORMAT_CODES -> ExportFailure.UNSUPPORTED_CODEC
            errorCode in familyOf(IO_FAMILY) -> ExportFailure.SOURCE_MISSING
            errorCode in familyOf(DECODING_FAMILY) -> ExportFailure.DECODE_FAILED
            errorCode in ENCODING_FAMILY until PROCESSING_FAMILY_END -> ExportFailure.ENCODE_FAILED
            else -> ExportFailure.UNKNOWN
        }

    /** Returns the failure for an error while saving the finished video to the gallery. */
    fun mapSaveFailure(error: Throwable): ExportFailure =
        if (isNoSpace(error)) ExportFailure.STORAGE_FULL else ExportFailure.UNKNOWN

    /** Returns true if [error] or any of its causes is an I/O error reporting a full disk. */
    private fun isNoSpace(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any { cause ->
            cause is IOException && NO_SPACE_MARKERS.any { marker -> cause.message.orEmpty().contains(marker) }
        }

    /** Returns the codes of the family starting at [first]. */
    private fun familyOf(first: Int): IntRange = first until first + FAMILY_SIZE
}

/*
 * Unit tests for mapping Media3 export error codes to user-facing failure reasons, and for export file names.
 */
package com.framecraft.editor.engine.export

import androidx.media3.transformer.ExportException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

/** Verifies [ExportFailureMapper] over each Media3 error family and [GallerySaver.displayNameFor]. */
class ExportFailureMapperTest {
    /** Each error family maps to its reason; unsupported formats are reported as codec problems. */
    @Test
    fun familiesMapToReasons() {
        assertEquals(ExportFailure.SOURCE_MISSING, map(ExportException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(ExportFailure.DECODE_FAILED, map(ExportException.ERROR_CODE_DECODING_FAILED))
        assertEquals(ExportFailure.UNSUPPORTED_CODEC, map(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(ExportFailure.UNSUPPORTED_CODEC, map(ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED))
        assertEquals(ExportFailure.ENCODE_FAILED, map(ExportException.ERROR_CODE_ENCODER_INIT_FAILED))
        assertEquals(ExportFailure.ENCODE_FAILED, map(ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED))
        assertEquals(ExportFailure.ENCODE_FAILED, map(ExportException.ERROR_CODE_MUXING_FAILED))
        assertEquals(ExportFailure.UNKNOWN, map(ExportException.ERROR_CODE_UNSPECIFIED))
    }

    /** A full disk anywhere in the cause chain wins over the error code. */
    @Test
    fun fullDiskWins() {
        val cause = RuntimeException("write failed", IOException("write failed: ENOSPC (No space left on device)"))
        val muxing = ExportFailureMapper.map(ExportException.ERROR_CODE_MUXING_FAILED, cause)
        assertEquals(ExportFailure.STORAGE_FULL, muxing)
        assertEquals(ExportFailure.STORAGE_FULL, ExportFailureMapper.mapSaveFailure(cause))
        assertEquals(ExportFailure.UNKNOWN, ExportFailureMapper.mapSaveFailure(IOException("Cannot write")))
    }

    /** File names keep the readable project name, drop unsafe characters, and end with a timestamp. */
    @Test
    fun fileNamesAreSafeAndStamped() {
        val now = LocalDateTime.of(2026, 10, 29, 7, 5, 9)
        assertEquals("Trip to Goa 2026-10-29 07.05.09.mp4", GallerySaver.displayNameFor("Trip to Goa", now))
        assertEquals("a_b_c 2026-10-29 07.05.09.mp4", GallerySaver.displayNameFor("a/b:c", now))
        assertEquals("Framecraft 2026-10-29 07.05.09.mp4", GallerySaver.displayNameFor("   ", now))
    }

    /** Maps [code] with no cause. */
    private fun map(code: Int): ExportFailure = ExportFailureMapper.map(code, null)
}

/*
 * Unit tests for parsing raw sound file metadata strings.
 */
package com.framecraft.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies validation and conversion of retriever metadata for audio files. */
class AudioMetadataParserTest {
    /** A declared sound track with a positive duration converts milliseconds to microseconds. */
    @Test
    fun parsesValidDuration() {
        assertEquals(180_250_000L, AudioMetadataParser.parseDurationMicros(" 180250 ", "yes"))
    }

    /** Missing, non-positive, or unreadable durations and files without sound are rejected. */
    @Test
    fun rejectsUnusableFiles() {
        assertNull(AudioMetadataParser.parseDurationMicros(null, "yes"))
        assertNull(AudioMetadataParser.parseDurationMicros("0", "yes"))
        assertNull(AudioMetadataParser.parseDurationMicros("long", "yes"))
        assertNull(AudioMetadataParser.parseDurationMicros("1000", null))
    }
}

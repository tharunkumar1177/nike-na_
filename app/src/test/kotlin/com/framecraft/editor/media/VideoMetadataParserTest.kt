/*
 * Unit tests for parsing raw video metadata strings.
 */
package com.framecraft.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies validation and conversion of retriever metadata. */
class VideoMetadataParserTest {
    /** Valid metadata converts milliseconds to microseconds and reads rotation and audio. */
    @Test
    fun parsesValidMetadata() {
        assertEquals(
            VideoMetadata(1_500_000L, 1920, 1080, 90, true),
            VideoMetadataParser.parse("1500", "1920", "1080", "90", "yes"),
        )
    }

    /** Missing or non-positive duration or dimensions are rejected. */
    @Test
    fun rejectsIncompleteMetadata() {
        assertNull(VideoMetadataParser.parse(null, "1920", "1080", "0", "yes"))
        assertNull(VideoMetadataParser.parse("0", "1920", "1080", "0", "yes"))
        assertNull(VideoMetadataParser.parse("1000", "abc", "1080", "0", "yes"))
    }

    /** Unknown rotations fall back to zero and absent audio flags mean no audio. */
    @Test
    fun defaultsRotationAndAudio() {
        assertEquals(
            VideoMetadata(1_000_000L, 640, 480, 0, false),
            VideoMetadataParser.parse("1000", "640", "480", "45", null),
        )
    }
}

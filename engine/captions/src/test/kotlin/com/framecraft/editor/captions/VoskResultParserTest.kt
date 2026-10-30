/*
 * Unit tests for reading Vosk's word-timed JSON results.
 */
package com.framecraft.editor.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies [VoskResultParser]. */
class VoskResultParserTest {
    /** Words come back in order with their times in microseconds, shifted by the offset. */
    @Test
    fun wordsAreTimedInMicros() {
        val json =
            """
            {"result":[{"conf":1.0,"end":1.02,"start":0.84,"word":"hello"},
            {"conf":0.9,"end":1.5,"start":1.08,"word":"there"}],"text":"hello there"}
            """.trimIndent()
        assertEquals(
            listOf(HeardWord("hello", 840_000L, 1_020_000L), HeardWord("there", 1_080_000L, 1_500_000L)),
            VoskResultParser.wordsOf(json),
        )
        assertEquals(2_840_000L, VoskResultParser.wordsOf(json, offsetMicros = 2_000_000L).first().startMicros)
    }

    /** Silence, malformed JSON, and entries missing a word or times give no words. */
    @Test
    fun badResultsGiveNoWords() {
        assertTrue(VoskResultParser.wordsOf("""{"text":""}""").isEmpty())
        assertTrue(VoskResultParser.wordsOf("not json").isEmpty())
        val partial =
            """
            {"result":[{"end":1.0,"start":0.5},{"word":"late","start":2.0,"end":1.0},
            {"word":" ok ","start":3.0,"end":3.25}]}
            """.trimIndent()
        assertEquals(listOf(HeardWord("ok", 3_000_000L, 3_250_000L)), VoskResultParser.wordsOf(partial))
    }
}

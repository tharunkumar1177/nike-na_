/*
 * Reads the JSON results Vosk returns with word timing on: a list of words, each with its start and end in seconds.
 */
package com.framecraft.editor.captions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/** Microseconds in one second. */
private const val MICROS_PER_SECOND = 1_000_000.0

/** Parses Vosk recognition results. */
object VoskResultParser {
    /**
     * Returns the timed words of one Vosk [result], shifted by [offsetMicros] so they count from the start of the
     * session's audio. Entries without a word or times are skipped, and malformed JSON yields no words.
     */
    fun wordsOf(
        result: String,
        offsetMicros: Long = 0L,
    ): List<HeardWord> {
        val entries = runCatching { Json.parseToJsonElement(result) as? JsonObject }.getOrNull()?.get("result")
        return (entries as? JsonArray).orEmpty().mapNotNull { entry -> wordOf(entry as? JsonObject, offsetMicros) }
    }

    /** Returns the word [entry] describes, or null when it lacks a word or valid times. */
    private fun wordOf(
        entry: JsonObject?,
        offsetMicros: Long,
    ): HeardWord? {
        val text = entry?.get("word")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val start = entry?.get("start")?.jsonPrimitive?.doubleOrNull?.takeIf { it >= 0.0 }
        val end = entry?.get("end")?.jsonPrimitive?.doubleOrNull?.takeIf { start != null && it >= start }
        if (text == null || start == null || end == null) return null
        return HeardWord(
            text = text,
            startMicros = offsetMicros + (start * MICROS_PER_SECOND).roundToLong(),
            endMicros = offsetMicros + (end * MICROS_PER_SECOND).roundToLong(),
        )
    }
}

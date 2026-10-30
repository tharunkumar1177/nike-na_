/*
 * Reads the JSON results Vosk returns with word timing on: a list of words, each with its start and end in seconds.
 */
package com.framecraft.editor.captions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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
        val text = entry.field("word")?.contentOrNull?.trim()
        val start = entry.field("start")?.doubleOrNull?.takeIf { it >= 0.0 }
        val end = entry.field("end")?.doubleOrNull?.takeIf { start != null && it >= start }
        if (text.isNullOrEmpty() || start == null || end == null) return null
        return HeardWord(
            text = text,
            startMicros = offsetMicros + (start * MICROS_PER_SECOND).roundToLong(),
            endMicros = offsetMicros + (end * MICROS_PER_SECOND).roundToLong(),
        )
    }

    /** Returns the primitive value of field [name] of this entry, or null when it is missing or not a primitive. */
    private fun JsonObject?.field(name: String): JsonPrimitive? = this?.get(name) as? JsonPrimitive
}

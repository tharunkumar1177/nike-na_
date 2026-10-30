/*
 * Groups recognized words into readable captions: short lines broken at pauses, each held long enough to read.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.model.CaptionContent
import com.framecraft.editor.domain.model.CaptionWord

/** Breaks a transcript into captions. */
object CaptionChunker {
    /** Most words one caption shows. */
    const val MAX_WORDS: Int = 6

    /** Most characters one caption shows, counting the spaces between words. */
    const val MAX_CHARACTERS: Int = 42

    /** Longest a caption may run from its first word's start to its last word's end. */
    const val MAX_SPAN_MICROS: Long = 3_500_000L

    /** A pause at least this long between two words starts a new caption. */
    const val PAUSE_MICROS: Long = 600_000L

    /** Shortest time a caption stays up when the next one leaves room. */
    const val MIN_HOLD_MICROS: Long = 1_000_000L

    /** Gap kept before the next caption when a caption is held past its last word. */
    const val HOLD_GAP_MICROS: Long = 80_000L

    /** First characters of the markers a recognizer writes for noise or unknown words, such as "[unk]". */
    private const val NOISE_MARKS = "[<"

    /**
     * Returns [words] grouped into captions in source order. Words that do not start after the previous kept word,
     * noise markers, and words that hold no letter or digit are dropped. Each caption starts with a capital, the
     * pronoun "i" is capitalized, and each caption is held to at least [MIN_HOLD_MICROS] without reaching the next one.
     */
    fun chunk(words: List<CaptionWord>): List<CaptionContent> {
        val groups = group(cleaned(words))
        return groups.mapIndexed { index, group ->
            val next = groups.getOrNull(index + 1)?.first()?.sourceStartMicros
            val shown = group.mapIndexed { position, word -> word.copy(text = cased(word.text, position == 0)) }
            CaptionContent(shown, holdEnd(group, next))
        }
    }

    /** Returns [words] without noise or unreadable tokens and without words that do not move forward in time. */
    private fun cleaned(words: List<CaptionWord>): List<CaptionWord> {
        val kept = mutableListOf<CaptionWord>()
        val readable =
            words.filter { word -> word.text.first() !in NOISE_MARKS && word.text.any { it.isLetterOrDigit() } }
        readable.forEach { word ->
            val previous = kept.lastOrNull()
            if (previous == null || word.sourceStartMicros > previous.sourceStartMicros) kept += word
        }
        return kept
    }

    /** Returns [words] split wherever a caption would grow too long, too wide, or span a pause. */
    private fun group(words: List<CaptionWord>): List<List<CaptionWord>> {
        val groups = mutableListOf<MutableList<CaptionWord>>()
        words.forEach { word ->
            val current = groups.lastOrNull()
            if (current == null || breaksBefore(current, word)) groups += mutableListOf(word) else current += word
        }
        return groups
    }

    /** True when [word] must start a new caption rather than join [current]. */
    private fun breaksBefore(
        current: List<CaptionWord>,
        word: CaptionWord,
    ): Boolean {
        val characters = current.sumOf { it.text.length + 1 } + word.text.length
        val full = current.size >= MAX_WORDS || characters > MAX_CHARACTERS
        val paused = word.sourceStartMicros - current.last().sourceEndMicros >= PAUSE_MICROS
        return full || paused || word.sourceEndMicros - current.first().sourceStartMicros > MAX_SPAN_MICROS
    }

    /**
     * Returns when a caption of [group] stops showing: after its last word and at least [MIN_HOLD_MICROS] after its
     * first, but [HOLD_GAP_MICROS] before [nextStart] when that comes sooner, and never at or before its first word.
     */
    private fun holdEnd(
        group: List<CaptionWord>,
        nextStart: Long?,
    ): Long {
        val first = group.first().sourceStartMicros
        val spoken = group.last().sourceEndMicros
        val wanted = maxOf(spoken, first + MIN_HOLD_MICROS)
        val held = nextStart?.let { minOf(wanted, it - HOLD_GAP_MICROS) } ?: wanted
        val floor = nextStart?.let { minOf(spoken, it) } ?: spoken
        return maxOf(held, floor, first + 1)
    }

    /** Returns [text] with the pronoun "i" capitalized, and its first letter too when it [starts] a caption. */
    private fun cased(
        text: String,
        starts: Boolean,
    ): String {
        val pronoun = text == "i" || text.startsWith("i'")
        return if (pronoun || starts) text.replaceFirstChar { it.uppercaseChar() } else text
    }
}

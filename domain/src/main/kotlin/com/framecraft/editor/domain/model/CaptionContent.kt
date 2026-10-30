/*
 * The speech behind an auto-caption: the words it shows, each timed in its source, so the caption keeps to the speech
 * through trims, splits, and speed changes of the clip it transcribes.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Most words one caption may hold. */
const val MAX_CAPTION_WORDS: Int = 12

/**
 * A spoken word [text] heard from [sourceStartMicros] to [sourceEndMicros] of its asset, counted from the asset's own
 * start so the times never change when the clip is trimmed. Short serial names keep long transcripts small.
 */
@Serializable
data class CaptionWord(
    @SerialName("w") val text: String,
    @SerialName("s") val sourceStartMicros: Long,
    @SerialName("e") val sourceEndMicros: Long,
) {
    init {
        require(text.isNotBlank() && text.none { it.isWhitespace() }) { "A caption word must be one word: '$text'" }
        require(sourceStartMicros in 0..sourceEndMicros) {
            "Caption word times out of order: $sourceStartMicros to $sourceEndMicros"
        }
    }
}

/**
 * The [words] a caption transcribes from the clip its anchor names, shown until [sourceEndMicros] of that clip's
 * asset, which may run past the last word into the following silence. Words are in source order.
 */
@Serializable
data class CaptionContent(
    val words: List<CaptionWord>,
    val sourceEndMicros: Long,
) {
    init {
        require(words.isNotEmpty() && words.size <= MAX_CAPTION_WORDS) {
            "A caption holds 1 to $MAX_CAPTION_WORDS words"
        }
        require(words.zipWithNext().all { (previous, next) -> previous.sourceStartMicros < next.sourceStartMicros }) {
            "Caption words must be in source order"
        }
        require(sourceEndMicros > words.first().sourceStartMicros) { "A caption must end after its first word starts" }
    }
}

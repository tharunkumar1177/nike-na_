/*
 * Word timing for captions that highlight the word being spoken: when each shown word starts on the timeline, and
 * which word is spoken at a given time.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CaptionResolver
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project

/** Finds the spoken word of a caption. */
object Karaoke {
    /**
     * Returns the timeline time each word [clip] shows starts in [project], in order, or nothing when the clip is
     * not a caption attached to the clip it transcribes.
     */
    fun wordStarts(
        project: Project,
        clip: Clip,
    ): List<Long> {
        val caption = clip.text?.caption
        val parentId = clip.anchor?.parentClipId
        val parent = project.tracks.flatMap { it.clips }.find { it.id == parentId }
        return if (caption == null || parent == null) {
            emptyList()
        } else {
            CaptionResolver.visibleWords(caption, parent).map {
                CaptionResolver.timelineAt(parent, it.sourceStartMicros, project.frameRate)
            }
        }
    }

    /** Returns the index of the word spoken at [timeMicros] given each word's start in [starts], or -1 before any. */
    fun wordAt(
        starts: List<Long>,
        timeMicros: Long,
    ): Int = starts.indexOfLast { it <= timeMicros }

    /** Returns the character range of word [index] in [text], whose words are separated by single spaces, or null. */
    fun rangeOf(
        text: String,
        index: Int,
    ): IntRange? {
        val words = text.split(' ')
        return if (index !in words.indices) {
            null
        } else {
            val start = words.take(index).sumOf { it.length + 1 }
            start until start + words[index].length
        }
    }
}

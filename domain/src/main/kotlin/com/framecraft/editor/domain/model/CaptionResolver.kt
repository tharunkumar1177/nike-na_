/*
 * The single place that derives where auto-captions sit: each caption follows the speech it transcribes through its
 * parent clip's trim, speed, and position, so captions never drift from the words they show.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate

/** Where a caption sits on the timeline, from [startMicros] for [durationMicros], and the [text] it shows there. */
data class CaptionSpan(
    val startMicros: Long,
    val durationMicros: Long,
    val text: String,
)

/** Keeps captions bound to their parent clips' speech. */
object CaptionResolver {
    /** Returns the words of [caption] that [parent] still plays: those starting inside its source span. */
    fun visibleWords(
        caption: CaptionContent,
        parent: Clip,
    ): List<CaptionWord> {
        val from = parent.trimStartMicros
        val end = from + parent.sourceSpanMicros
        return caption.words.filter { it.sourceStartMicros in from until end }
    }

    /** Returns the timeline time at which [parent] plays [sourceMicros] of its asset, held to the clip. */
    fun timelineAt(
        parent: Clip,
        sourceMicros: Long,
        frameRate: FrameRate,
    ): Long {
        val offset = (sourceMicros - parent.trimStartMicros).coerceIn(0L, parent.sourceSpanMicros)
        return parent.startMicros + (parent.speedMap(frameRate)?.timelineAt(offset) ?: offset)
    }

    /** Returns where [caption] sits while [parent] plays it at [frameRate], or null when none of its words play. */
    fun spanOf(
        caption: CaptionContent,
        parent: Clip,
        frameRate: FrameRate,
    ): CaptionSpan? {
        val words = visibleWords(caption, parent)
        return words.firstOrNull()?.let { first ->
            val start = timelineAt(parent, first.sourceStartMicros, frameRate)
            val end = timelineAt(parent, caption.sourceEndMicros, frameRate)
            CaptionSpan(start, maxOf(end - start, 1L), words.joinToString(" ") { it.text })
        }
    }

    /**
     * Returns [tracks] with every anchored caption moved, sized, and worded as its parent now plays it at [frameRate],
     * and each changed track re-sorted. Throws [IllegalArgumentException] when a caption's parent is missing or plays
     * none of its words; commands that can cause that remove the caption themselves.
     */
    fun materialize(
        tracks: List<Track>,
        frameRate: FrameRate,
    ): List<Track> {
        val clips = tracks.flatMap { it.clips }.associateBy { it.id }
        return tracks.map { track ->
            if (track.clips.none { it.isBoundCaption }) {
                track
            } else {
                track.copy(clips = track.clips.map { placed(it, clips, frameRate) }.sortedBy { it.startMicros })
            }
        }
    }

    /**
     * Throws [IllegalArgumentException] unless every anchored caption in [tracks] transcribes a clip with sound from
     * [assets] and sits exactly where, and says exactly what, its parent plays at [frameRate].
     */
    fun validate(
        tracks: List<Track>,
        assets: List<MediaAsset>,
        frameRate: FrameRate,
    ) {
        val assetsById = assets.associateBy { it.id }
        val parents = tracks.filter { it.kind != TrackKind.TEXT }.flatMap { it.clips }.associateBy { it.id }
        tracks.flatMap { it.clips }.filter { it.isBoundCaption }.forEach { clip ->
            val parent = requireNotNull(parents[clip.anchor?.parentClipId]) { "Caption ${clip.id} has no sound clip" }
            val asset = assetsById[parent.assetId]
            require(asset != null && asset.isTimed && asset.hasAudio) { "Caption ${clip.id} transcribes a silent clip" }
            val text = requireNotNull(clip.text)
            val span = requireNotNull(text.caption?.let { spanOf(it, parent, frameRate) }) {
                "Caption ${clip.id} shows no played words"
            }
            require(span == CaptionSpan(clip.startMicros, clip.durationMicros, text.text)) {
                "Caption ${clip.id} is out of step with its speech: expected $span"
            }
        }
    }

    /** Returns [clip] placed where its parent in [clips] plays its words, or unchanged when not a bound caption. */
    private fun placed(
        clip: Clip,
        clips: Map<String, Clip>,
        frameRate: FrameRate,
    ): Clip {
        val text = clip.text
        val caption = text?.caption
        val anchor = clip.anchor
        if (caption == null || anchor == null) return clip
        val parent =
            requireNotNull(clips[anchor.parentClipId]) { "Caption ${clip.id} lost clip ${anchor.parentClipId}" }
        val span = requireNotNull(spanOf(caption, parent, frameRate)) { "Caption ${clip.id} has no words left to show" }
        return clip.copy(
            startMicros = span.startMicros,
            durationMicros = span.durationMicros,
            anchor = anchor.copy(offsetMicros = span.startMicros - parent.startMicros),
            text = text.copy(text = span.text).within(span.durationMicros),
        )
    }

    /** Returns this content with each animation shortened to fit a clip [durationMicros] long. */
    private fun TextContent.within(durationMicros: Long): TextContent =
        copy(
            animationIn = animationIn?.let { it.copy(durationMicros = minOf(it.durationMicros, durationMicros)) },
            animationOut = animationOut?.let { it.copy(durationMicros = minOf(it.durationMicros, durationMicros)) },
        )
}

/** True when this clip is a caption still attached to the clip whose speech it shows. */
val Clip.isBoundCaption: Boolean
    get() = text?.caption != null && anchor != null

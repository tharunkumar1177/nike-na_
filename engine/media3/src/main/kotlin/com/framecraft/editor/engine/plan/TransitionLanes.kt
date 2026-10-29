/*
 * Plans a base track that plays transitions: the track itself, the lane its incoming clips play on, and the backdrop
 * its fades pass through.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TransitionCut
import com.framecraft.editor.domain.model.TransitionGeometry
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.model.speedMap
import com.framecraft.editor.engine.SolidPng

/** Opaque black, which a fade to black passes through. */
private const val BLACK_ARGB = 0xFF000000.toInt()

/** Opaque white, which a fade to white passes through. */
private const val WHITE_ARGB = 0xFFFFFFFF.toInt()

/**
 * Transitions never shorten the timeline. Through each window the base track keeps playing the outgoing clip into the
 * hidden source past its end while the incoming clip plays from the hidden source before its start on a lane drawn
 * above, fading in its sound as the outgoing sound fades out. After the window the base plays the incoming clip on
 * from where the lane left it. Media3 sequences cannot overlap their own items, so the lane is a sequence of its own;
 * windows never meet, so one lane serves every transition on the track.
 */
internal object TransitionLanes {
    /**
     * Returns the incoming lane, [track], and the fade backdrop when any, each padded to [endMicros], or null when no
     * transition plays on [track].
     */
    fun sequencesOf(
        project: Project,
        track: Track,
        endMicros: Long,
    ): List<SequencePlan>? {
        val cuts = TransitionGeometry.cutsOf(project, track)
        return cuts.takeIf { found -> found.isNotEmpty() }?.let {
            val base =
                SequencePlan(padded(baseSegments(project, track, cuts), endMicros), baseSpans = baseSpans(track, cuts))
            listOfNotNull(incomingLane(project, cuts, endMicros), base, backdrop(cuts, endMicros))
        }
    }

    /** Returns the segments of base [track]: each clip from its incoming window's end to its outgoing window's end. */
    private fun baseSegments(
        project: Project,
        track: Track,
        cuts: List<TransitionCut>,
    ): List<PlanSegment> {
        val outgoing = cuts.associateBy { it.left.id }
        val incoming = cuts.associateBy { it.right.id }
        var cursor = 0L
        val segments = mutableListOf<PlanSegment>()
        track.clips.forEach { clip ->
            val start = clip.startMicros + (incoming[clip.id]?.headMicros ?: 0L)
            if (start > cursor) segments += PlanSegment.Gap(start - cursor)
            val segment = baseMedia(project, clip, incoming[clip.id], outgoing[clip.id])
            segments += segment
            cursor = start + segment.durationMicros
        }
        return segments
    }

    /**
     * Returns [clip] on the base track, starting where its [incoming] window ends and playing on through its
     * [outgoing] window, its sound fading out over that window. A fade at an end with a transition gives way to it.
     */
    private fun baseMedia(
        project: Project,
        clip: Clip,
        incoming: TransitionCut?,
        outgoing: TransitionCut?,
    ): PlanSegment.Media {
        val asset = project.assetOf(clip)
        val skipped = incoming?.headMicros ?: 0L
        val extra = outgoing?.headMicros ?: 0L
        val duration = clip.durationMicros - skipped + extra
        val from = sourceOffset(project, clip, skipped)
        val beyond = if (extra == 0L) 0L else SpeedMap.spanFor(clip.speed, extra, project.frameRate)
        val available = asset.durationMicros?.let { it - clip.trimStartMicros } ?: Long.MAX_VALUE
        val to = (clip.sourceSpanMicros + beyond).coerceAtMost(available)
        val fadeOut = (outgoing?.durationMicros ?: clip.fadeOutMicros).coerceAtMost(duration)
        val fadeIn = (if (incoming != null) 0L else clip.fadeInMicros).coerceAtMost(duration - fadeOut)
        return PlanSegment.Media(
            uri = asset.uri,
            kind = asset.kind,
            sourceDurationMicros = asset.durationMicros ?: duration,
            sourceStartMicros = clip.trimStartMicros + if (asset.isTimed) from else 0L,
            durationMicros = duration,
            volumePercent = clip.volumePercent,
            fadeInMicros = fadeIn,
            fadeOutMicros = fadeOut,
            speed = clip.speed,
            sourceSpanMicros = if (asset.isTimed) to - from else duration,
            maintainPitch = clip.maintainPitch,
        )
    }

    /** Returns where each clip of [track] shows: alone, then as the outgoing side of its transition. */
    private fun baseSpans(
        track: Track,
        cuts: List<TransitionCut>,
    ): List<BaseSpan> {
        val outgoing = cuts.associateBy { it.left.id }
        val incoming = cuts.associateBy { it.right.id }
        return track.clips.flatMap { clip ->
            val placement = clip.canvasPlacement ?: CanvasPlacement()
            val out = outgoing[clip.id]
            val start = clip.startMicros + (incoming[clip.id]?.headMicros ?: 0L)
            val alone = BaseSpan(start, out?.startMicros ?: clip.endMicros, placement)
            listOf(alone) + listOfNotNull(out?.let { BaseSpan(it.startMicros, it.endMicros, placement, it.kind) })
        }
    }

    /** Returns the lane of every incoming clip in [cuts], each over its window, padded to [endMicros]. */
    private fun incomingLane(
        project: Project,
        cuts: List<TransitionCut>,
        endMicros: Long,
    ): SequencePlan {
        val spans =
            cuts.map { cut ->
                val placement = cut.right.canvasPlacement ?: CanvasPlacement()
                TransitionSpan(cut.startMicros, cut.endMicros, cut.kind, placement)
            }
        val segments = windowed(cuts, endMicros) { incomingMedia(project, it) }
        return SequencePlan(segments, role = SequenceRole.INCOMING, transitionSpans = spans)
    }

    /**
     * Returns the incoming clip of [cut] over the window: the source that leads up to where the clip plays on alone,
     * its sound rising over the window.
     */
    private fun incomingMedia(
        project: Project,
        cut: TransitionCut,
    ): PlanSegment.Media {
        val clip = cut.right
        val asset = project.assetOf(clip)
        val duration = cut.durationMicros
        val end = clip.trimStartMicros + sourceOffset(project, clip, cut.headMicros)
        val start = (end - SpeedMap.spanFor(clip.speed, duration, project.frameRate)).coerceAtLeast(0L)
        return PlanSegment.Media(
            uri = asset.uri,
            kind = asset.kind,
            sourceDurationMicros = asset.durationMicros ?: duration,
            sourceStartMicros = if (asset.isTimed) start else clip.trimStartMicros,
            durationMicros = duration,
            volumePercent = clip.volumePercent,
            fadeInMicros = duration,
            speed = clip.speed,
            sourceSpanMicros = if (asset.isTimed) end - start else duration,
            maintainPitch = clip.maintainPitch,
        )
    }

    /** Returns the black or white still under every fade in [cuts], padded to [endMicros], or null without fades. */
    private fun backdrop(
        cuts: List<TransitionCut>,
        endMicros: Long,
    ): SequencePlan? {
        val fades = cuts.filter { it.kind == TransitionKind.FADE_BLACK || it.kind == TransitionKind.FADE_WHITE }
        return fades.takeIf { found -> found.isNotEmpty() }?.let {
            val spans = fades.map { cut -> TransitionSpan(cut.startMicros, cut.endMicros, cut.kind, CanvasPlacement()) }
            val segments = windowed(fades, endMicros, ::backdropMedia)
            SequencePlan(segments, role = SequenceRole.BACKDROP, transitionSpans = spans)
        }
    }

    /** Returns [media] of each of [cuts] over its window, with gaps between and up to [endMicros]. */
    private fun windowed(
        cuts: List<TransitionCut>,
        endMicros: Long,
        media: (TransitionCut) -> PlanSegment.Media,
    ): List<PlanSegment> {
        var cursor = 0L
        val segments = mutableListOf<PlanSegment>()
        cuts.forEach { cut ->
            if (cut.startMicros > cursor) segments += PlanSegment.Gap(cut.startMicros - cursor)
            segments += media(cut)
            cursor = cut.endMicros
        }
        return padded(segments, endMicros, cursor)
    }

    /** Returns [segments], which end at [cursorMicros], followed by a gap that runs them on to [endMicros]. */
    private fun padded(
        segments: List<PlanSegment>,
        endMicros: Long,
        cursorMicros: Long = segments.sumOf { it.durationMicros },
    ): List<PlanSegment> {
        val pad = endMicros - cursorMicros
        return segments + listOfNotNull(PlanSegment.Gap(pad).takeIf { pad > 0 })
    }

    /** Returns the source offset of [clip] that plays [timelineMicros] into it in [project]. */
    private fun sourceOffset(
        project: Project,
        clip: Clip,
        timelineMicros: Long,
    ): Long = clip.speedMap(project.frameRate)?.sourceAt(timelineMicros) ?: timelineMicros
}

/** Returns the still that [cut], a fade, passes through over its window. */
private fun backdropMedia(cut: TransitionCut): PlanSegment.Media {
    val argb = if (cut.kind == TransitionKind.FADE_WHITE) WHITE_ARGB else BLACK_ARGB
    return PlanSegment.Media(SolidPng.dataUri(argb), MediaKind.IMAGE, cut.durationMicros, 0L, cut.durationMicros)
}

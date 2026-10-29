/*
 * Media3-independent playback plan: the ordered segments (media and gaps) each track contributes, plus text spans.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.CanvasSettings
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT
import com.framecraft.editor.domain.time.FrameRate

/** One contiguous piece of a sequence. */
sealed interface PlanSegment {
    /** How long the segment occupies the timeline. */
    val durationMicros: Long

    /**
     * Plays [sourceSpanMicros] of [uri] starting at [sourceStartMicros] over [durationMicros] of timeline;
     * [sourceDurationMicros] is the full source. The source plays at [speed], normal when null, keeping its pitch when
     * [maintainPitch] is set. Its sound plays at [volumePercent] (0 is silent) with fades of [fadeInMicros] and
     * [fadeOutMicros] in timeline time.
     */
    data class Media(
        val uri: String,
        val kind: MediaKind,
        val sourceDurationMicros: Long,
        val sourceStartMicros: Long,
        override val durationMicros: Long,
        val volumePercent: Int = UNITY_VOLUME_PERCENT,
        val fadeInMicros: Long = 0,
        val fadeOutMicros: Long = 0,
        val speed: ClipSpeed? = null,
        val sourceSpanMicros: Long = durationMicros,
        val maintainPitch: Boolean = true,
    ) : PlanSegment

    /** Empty timeline span: black frames and silence on the base sequence, hidden on overlay sequences. */
    data class Gap(
        override val durationMicros: Long,
    ) : PlanSegment
}

/** Where an overlay clip sits on the canvas from [startMicros] until [endMicros] (exclusive), in timeline time. */
data class OverlaySpan(
    val startMicros: Long,
    val endMicros: Long,
    val transform: OverlayTransform,
)

/** A text clip drawn over the picture with [content] at [transform] from [startMicros] until [endMicros]. */
data class TextSpan(
    val startMicros: Long,
    val endMicros: Long,
    val transform: OverlayTransform,
    val content: TextContent,
)

/** Where a base clip sits on the canvas from [startMicros] until [endMicros] (exclusive), in timeline time. */
data class BaseSpan(
    val startMicros: Long,
    val endMicros: Long,
    val placement: CanvasPlacement,
)

/**
 * Segments of one track of [kind] in timeline order, starting at time zero; overlay tracks also carry their
 * [overlaySpans] and base tracks their [baseSpans].
 */
data class SequencePlan(
    val segments: List<PlanSegment>,
    val overlaySpans: List<OverlaySpan> = emptyList(),
    val kind: TrackKind = TrackKind.VIDEO,
    val baseSpans: List<BaseSpan> = emptyList(),
)

/** What fills the canvas behind the base clips when it is not black. */
sealed interface BackgroundPlan {
    /** One opaque color, [argb]. */
    data class Solid(
        val argb: Int,
    ) : BackgroundPlan

    /** A blurred copy of the first base track: its padded [segments], shown during its clips' [spans]. */
    data class Blur(
        val segments: List<PlanSegment>,
        val spans: List<BaseSpan>,
    ) : BackgroundPlan
}

/**
 * Everything the engine needs to build a composition from a project; [textSpans] are drawn last on top. When
 * [composited], the picture goes through the compositor at the canvas shape [aspectRatio] (null keeps the first base
 * clip's shape) over [background] (null is black).
 */
data class PlaybackPlan(
    val sequences: List<SequencePlan>,
    val frameRate: FrameRate,
    val textSpans: List<TextSpan> = emptyList(),
    val composited: Boolean = false,
    val aspectRatio: Float? = null,
    val background: BackgroundPlan? = null,
)

/** Converts project snapshots into playback plans. */
object PlaybackPlanner {
    /**
     * Plans every non-empty overlay track of [project] from the top lane down, then every non-empty base video
     * track, then every non-empty audio track, filling implicit gaps with [PlanSegment.Gap]. Media3 draws earlier
     * picture sequences over later ones, so overlays come before the base. Picture tracks that end before the latest
     * track are padded with a gap, because the compositor waits for a frame from every picture input; on the base
     * this shows black under music that runs past the video. Text tracks add no sequence but count toward that end;
     * their spans run from the bottom lane up, so upper lanes draw on top. The picture is composited when there are
     * overlays or the canvas or any base placement differs from the defaults.
     */
    fun plan(project: Project): PlaybackPlan {
        val planned = project.tracks.filter { it.clips.isNotEmpty() }
        val end = planned.maxOfOrNull { it.endMicros } ?: 0L
        val base =
            planned
                .filter { it.kind == TrackKind.VIDEO }
                .map { SequencePlan(paddedSegmentsOf(project, it, end), baseSpans = baseSpansOf(it)) }
        val overlays =
            planned
                .filter { it.kind == TrackKind.OVERLAY }
                .map { SequencePlan(paddedSegmentsOf(project, it, end), spansOf(it), TrackKind.OVERLAY) }
        val audio =
            planned
                .filter { it.kind == TrackKind.AUDIO }
                .map { SequencePlan(segmentsOf(project, it), kind = TrackKind.AUDIO) }
        val text =
            planned
                .filter { it.kind == TrackKind.TEXT }
                .asReversed()
                .flatMap(::textSpansOf)
        val placed = planned.any { track -> track.clips.any { it.canvasPlacement != null } }
        val composited = overlays.isNotEmpty() || placed || project.canvas != CanvasSettings()
        return PlaybackPlan(
            sequences = overlays + base + audio,
            frameRate = project.frameRate,
            textSpans = text,
            composited = composited,
            aspectRatio = project.canvas.aspect.ratio,
            background = backgroundOf(project.canvas.background, base.firstOrNull()),
        )
    }

    /** Returns the plan for [background] behind [firstBase], or null for black or when there is nothing to blur. */
    private fun backgroundOf(
        background: CanvasBackground,
        firstBase: SequencePlan?,
    ): BackgroundPlan? =
        when (background) {
            CanvasBackground.Black -> null
            is CanvasBackground.Solid -> BackgroundPlan.Solid(background.argb)
            CanvasBackground.Blur -> firstBase?.let { BackgroundPlan.Blur(it.segments, it.baseSpans) }
        }

    /** Returns the canvas placement of every clip on base [track], defaulting to fitted, centered, and upright. */
    private fun baseSpansOf(track: Track): List<BaseSpan> =
        track.clips.map { clip ->
            BaseSpan(clip.startMicros, clip.endMicros, clip.canvasPlacement ?: CanvasPlacement())
        }

    /** Returns the segments of [track] followed by a gap that runs it on to [endMicros]. */
    private fun paddedSegmentsOf(
        project: Project,
        track: Track,
        endMicros: Long,
    ): List<PlanSegment> {
        val pad = endMicros - track.endMicros
        return segmentsOf(project, track) + listOfNotNull(PlanSegment.Gap(pad).takeIf { pad > 0 })
    }

    /** Returns the media and gap segments of [track] from time zero. */
    private fun segmentsOf(
        project: Project,
        track: Track,
    ): List<PlanSegment> {
        var cursor = 0L
        val segments = mutableListOf<PlanSegment>()
        track.clips.forEach { clip ->
            if (clip.startMicros > cursor) {
                segments += PlanSegment.Gap(clip.startMicros - cursor)
            }
            val asset = project.assetOf(clip)
            segments +=
                PlanSegment.Media(
                    uri = asset.uri,
                    kind = asset.kind,
                    sourceDurationMicros = asset.durationMicros ?: clip.durationMicros,
                    sourceStartMicros = clip.trimStartMicros,
                    durationMicros = clip.durationMicros,
                    volumePercent = clip.volumePercent,
                    fadeInMicros = clip.fadeInMicros,
                    fadeOutMicros = clip.fadeOutMicros,
                    speed = clip.speed,
                    sourceSpanMicros = clip.sourceSpanMicros,
                    maintainPitch = clip.maintainPitch,
                )
            cursor = clip.endMicros
        }
        return segments
    }

    /** Returns the canvas placement of every clip on [track], defaulting to full frame. */
    private fun spansOf(track: Track): List<OverlaySpan> =
        track.clips.map { clip ->
            OverlaySpan(clip.startMicros, clip.endMicros, clip.overlayTransform ?: OverlayTransform())
        }

    /** Returns the text of every clip on [track], centered at natural size when it has no placement. */
    private fun textSpansOf(track: Track): List<TextSpan> =
        track.clips.mapNotNull { clip ->
            val content = clip.text ?: return@mapNotNull null
            TextSpan(clip.startMicros, clip.endMicros, clip.overlayTransform ?: OverlayTransform(), content)
        }
}

/*
 * Decides which clips get auto-captions and turns their transcripts into one edit that places the captions on a
 * caption lane per source track, replacing any captions the project already has.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.CaptionLaneChange
import com.framecraft.editor.domain.command.ReplaceCaptions
import com.framecraft.editor.domain.model.CaptionResolver
import com.framecraft.editor.domain.model.CaptionWord
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TextOutline
import com.framecraft.editor.domain.model.TextStyle
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind

/** Which clips a caption run transcribes. */
enum class CaptionScope {
    /** Every clip with sound. */
    ALL,

    /** Only voiceovers recorded in the editor. */
    VOICEOVERS,

    /** Only the selected clip. */
    SELECTED,
}

/** Clip [clip] on [track], whose [asset] has the sound to transcribe. */
data class CaptionSource(
    val track: Track,
    val clip: Clip,
    val asset: MediaAsset,
)

/** The [command] that places captions, and how many captions it adds as [captionCount]. */
data class CaptionPlan(
    val command: ReplaceCaptions,
    val captionCount: Int,
)

/** Plans auto-captions. */
object CaptionPlanner {
    /** Color of the word being spoken in a new caption. */
    const val HIGHLIGHT_ARGB: Int = -0x29f6

    /** Text size of a new caption, as a fraction of the canvas height. */
    const val CAPTION_SIZE_FRACTION: Float = 0.06f

    /** Vertical center of a new caption, in the lower third of the canvas. */
    const val CAPTION_CENTER_Y: Float = 0.7f

    /** The look of a new caption: white words outlined in black with the spoken word highlighted. */
    val DEFAULT_STYLE: TextStyle =
        TextStyle(sizeFraction = CAPTION_SIZE_FRACTION, outline = TextOutline(), highlightArgb = HIGHLIGHT_ARGB)

    /**
     * Returns the clips [scope] covers in [project], in track order: clips with sound on video, overlay, and audio
     * tracks, the selected clip [selectedClipId] alone for [CaptionScope.SELECTED], and only audible clips unless
     * [includeMuted].
     */
    fun sources(
        project: Project,
        scope: CaptionScope,
        selectedClipId: String?,
        includeMuted: Boolean,
    ): List<CaptionSource> {
        val assets = project.assets.associateBy { it.id }
        return project.tracks.filter { it.kind != TrackKind.TEXT }.flatMap { track ->
            track.clips.mapNotNull { clip ->
                val asset = assets[clip.assetId]
                val audible = includeMuted || (clip.volumePercent > 0 && track.volumePercent > 0)
                val inScope =
                    when (scope) {
                        CaptionScope.ALL -> true
                        CaptionScope.VOICEOVERS -> clip.voiceover
                        CaptionScope.SELECTED -> clip.id == selectedClipId
                    }
                val heard = asset != null && asset.isTimed && asset.hasAudio
                if (heard && audible && inScope) CaptionSource(track, clip, requireNotNull(asset)) else null
            }
        }
    }

    /** True when [project] holds at least one caption. */
    fun hasCaptions(project: Project): Boolean = project.tracks.any { track -> track.clips.any { it.isCaption } }

    /** Returns the edit that removes every caption in [project], or null when it has none. */
    fun removeAll(project: Project): ReplaceCaptions? =
        removals(project).takeIf { it.isNotEmpty() }?.let(::ReplaceCaptions)

    /**
     * Returns the edit that replaces every caption in [project] with captions of [transcripts], keyed by clip id and
     * timed in each clip's asset, styled [style], or null when nothing would change. Captions of clips on one track
     * share a new lane; tracks past the text lane limit are left out.
     */
    fun plan(
        project: Project,
        transcripts: Map<String, List<CaptionWord>>,
        style: TextStyle,
        idGenerator: IdGenerator,
    ): CaptionPlan? {
        val removals = removals(project)
        val textLanes = project.tracks.count { it.kind == TrackKind.TEXT }
        val emptied = project.tracks.count { lane -> lane.clips.isNotEmpty() && lane.clips.all { it.isCaption } }
        val freeLanes = TextPlacement.MAX_TEXT_LANES - textLanes + emptied
        val additions =
            project.tracks
                .filter { it.kind != TrackKind.TEXT }
                .map { track -> captionsOf(project, track, transcripts, style, idGenerator) }
                .filter { it.isNotEmpty() }
                .take(freeLanes.coerceAtLeast(0))
                .map { CaptionLaneChange(idGenerator.next(), 0, emptyList(), it) }
        val changes = removals + additions
        val count = additions.sumOf { it.added.size }
        return changes.takeIf { it.isNotEmpty() }?.let { CaptionPlan(ReplaceCaptions(it), count) }
    }

    /** Returns the lane changes that take every caption out of [project]. */
    private fun removals(project: Project): List<CaptionLaneChange> =
        project.tracks.mapIndexedNotNull { index, track ->
            val captions = track.clips.filter { it.isCaption }.map { it.id }
            captions.takeIf { it.isNotEmpty() }?.let { CaptionLaneChange(track.id, index, it, emptyList()) }
        }

    /** Returns the captions of every clip on [track] that has a transcript, in time order, never overlapping. */
    private fun captionsOf(
        project: Project,
        track: Track,
        transcripts: Map<String, List<CaptionWord>>,
        style: TextStyle,
        idGenerator: IdGenerator,
    ): List<Clip> {
        val captions = mutableListOf<Clip>()
        track.clips.forEach { parent ->
            val words = transcripts[parent.id].orEmpty().sortedBy { it.sourceStartMicros }
            CaptionChunker.chunk(words).forEach { content ->
                val span = CaptionResolver.spanOf(content, parent, project.frameRate)
                val free = span != null && span.startMicros >= (captions.lastOrNull()?.endMicros ?: 0L)
                if (span != null && free) {
                    captions +=
                        Clip(
                            id = idGenerator.next(),
                            assetId = TEXT_ASSET_ID,
                            startMicros = span.startMicros,
                            trimStartMicros = 0L,
                            durationMicros = span.durationMicros,
                            anchor = ClipAnchor(parent.id, span.startMicros - parent.startMicros),
                            overlayTransform = OverlayTransform(centerY = CAPTION_CENTER_Y),
                            text = TextContent(span.text, style, caption = content),
                        )
                }
            }
        }
        return captions
    }

    /** True when this clip is an auto-caption. */
    private val Clip.isCaption: Boolean
        get() = text?.caption != null
}

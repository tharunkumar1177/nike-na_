/*
 * Decides where a newly added text clip goes: at the playhead, attached to the base clip there, on a free text lane.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.AddTextClip
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TEXT_ASSET_ID
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind

/** Where new text goes: from [startMicros] to [endMicros], attached to [parent], on [lane] or a new lane when null. */
private class TextSlot(
    val parent: Clip,
    val startMicros: Long,
    val endMicros: Long,
    val lane: Track?,
)

/** Places new text clips. */
object TextPlacement {
    /** Most text lanes a project may have. */
    const val MAX_TEXT_LANES: Int = 8

    /** How long a new text clip lasts when its base clip leaves room. */
    const val DEFAULT_TEXT_DURATION_MICROS: Long = 3_000_000L

    /** Returns true when [propose] would place text at [playheadMicros]. */
    fun canPlace(
        project: Project,
        playheadMicros: Long,
    ): Boolean = slotAt(project, playheadMicros) != null

    /**
     * Returns the command that adds [content] at [playheadMicros], or null when the project has no base clip or every
     * text lane is busy. The clip attaches to the base clip under the playhead (or the nearest one), starts on a frame
     * boundary inside it, lasts up to [DEFAULT_TEXT_DURATION_MICROS] without passing the base clip's end, and goes on
     * the first text lane free for that span, or on a new lane after the other text lanes.
     */
    fun propose(
        project: Project,
        playheadMicros: Long,
        content: TextContent,
        idGenerator: IdGenerator,
    ): AddTextClip? =
        slotAt(project, playheadMicros)?.let { slot ->
            val trackId = slot.lane?.id ?: idGenerator.next()
            val clip =
                Clip(
                    id = idGenerator.next(),
                    assetId = TEXT_ASSET_ID,
                    startMicros = slot.startMicros,
                    trimStartMicros = 0L,
                    durationMicros = slot.endMicros - slot.startMicros,
                    anchor = ClipAnchor(slot.parent.id, slot.startMicros - slot.parent.startMicros),
                    overlayTransform = OverlayTransform(),
                    text = content,
                )
            val lastTextLane = project.tracks.indexOfLast { it.kind == TrackKind.TEXT }
            AddTextClip(trackId, clip, trackIndex = lastTextLane + 1)
        }

    /** Returns where text added at [playheadMicros] would go, or null when it cannot be added. */
    private fun slotAt(
        project: Project,
        playheadMicros: Long,
    ): TextSlot? {
        val base = OverlayPlacement.baseTrack(project) ?: return null
        val parent = OverlayPlacement.parentAt(base, playheadMicros)
        val frame = project.frameRate.startOfFrame(1)
        val latestStart = maxOf(parent.startMicros, parent.endMicros - frame)
        val start = project.frameRate.snapToNearestFrame(playheadMicros).coerceIn(parent.startMicros, latestStart)
        val end = start + minOf(DEFAULT_TEXT_DURATION_MICROS, parent.endMicros - start)
        val lanes = project.tracks.filter { it.kind == TrackKind.TEXT }
        val free =
            lanes
                .filter { lane -> lane.clips.none { it.text?.caption != null } }
                .firstOrNull { lane -> lane.clips.none { it.startMicros < end && start < it.endMicros } }
        return if (free == null && lanes.size >= MAX_TEXT_LANES) null else TextSlot(parent, start, end, free)
    }
}

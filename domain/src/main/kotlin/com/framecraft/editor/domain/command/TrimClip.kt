/*
 * Command that changes where a clip starts, which part of its source it reads, and how long it lasts.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.AnchorResolver
import com.framecraft.editor.domain.model.CaptionResolver
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets clip [clipId] to start at [startMicros], read its source from [trimStartMicros], and last [durationMicros].
 * On a magnetic track the start must not move and later clips shift by the change in duration; on other tracks
 * an overlap is rejected. Clips shorter than one frame and image or text clips with a changed source offset are
 * rejected.
 * An anchored clip keeps its parent and its anchor offset moves with the start; clips anchored to moved clips follow.
 * [fadeInMicros] and [fadeOutMicros] set the fades when given; otherwise fades shrink to fit the new duration,
 * fade-in first. A text clip's animations take [animationInMicros] and [animationOutMicros] when given; otherwise each
 * shrinks to fit the new duration. A sped clip reads [sourceDurationMicros] of source, which it requires; a clip at
 * normal speed must not carry one. Captions of the clip left with no words to show are removed, and
 * [restoredCaptions] are put back on their lanes. A caption itself cannot be trimmed.
 */
@Serializable
@SerialName("trimClip")
data class TrimClip(
    val clipId: String,
    val startMicros: Long,
    val trimStartMicros: Long,
    val durationMicros: Long,
    val fadeInMicros: Long? = null,
    val fadeOutMicros: Long? = null,
    val animationInMicros: Long? = null,
    val animationOutMicros: Long? = null,
    val sourceDurationMicros: Long? = null,
    val restoredCaptions: List<LaneClip> = emptyList(),
) : EditCommand {
    /** The inverse restores the clip's previous start, source offset, duration, fades, animations, and captions. */
    override fun applyTo(project: Project): CommandOutcome {
        val location = project.locate(clipId) ?: return clipNotFound(clipId)
        val minimum = project.frameRate.startOfFrame(1)
        val lock = captionLock(location.clip)
        return when {
            lock != null -> lock
            durationMicros < minimum -> CommandOutcome.Rejected(CommandRejection.ClipTooShort(minimum))
            else -> trim(project, location)
        }
    }

    /** Replaces the clip at [location] in [project] with its trimmed form. */
    private fun trim(
        project: Project,
        location: ClipLocation,
    ): CommandOutcome {
        val (track, clip) = location
        val silenced = project.captionsSilencedBy(clipId, trimStartMicros, sourceDurationMicros ?: durationMicros)
        val inverse =
            TrimClip(
                clipId,
                clip.startMicros,
                clip.trimStartMicros,
                clip.durationMicros,
                clip.fadeInMicros,
                clip.fadeOutMicros,
                clip.text?.animationIn?.durationMicros,
                clip.text?.animationOut?.durationMicros,
                clip.sourceDurationMicros,
                silenced,
            )
        return project.replacingTracks(inverse) {
            require(project.assetOf(clip).isTimed || trimStartMicros == clip.trimStartMicros) {
                "Clip $clipId has no source offset to trim"
            }
            require((clip.speed == null) == (sourceDurationMicros == null)) {
                "Clip $clipId needs a source length exactly when sped"
            }
            val fadeIn = fadeInMicros ?: minOf(clip.fadeInMicros, durationMicros)
            val fadeOut = fadeOutMicros ?: minOf(clip.fadeOutMicros, (durationMicros - fadeIn).coerceAtLeast(0))
            val trimmed =
                clip.copy(
                    startMicros = startMicros,
                    trimStartMicros = trimStartMicros,
                    durationMicros = durationMicros,
                    anchor = shiftedAnchor(clip.anchor, startMicros - clip.startMicros),
                    fadeInMicros = fadeIn,
                    fadeOutMicros = fadeOut,
                    text = clip.text?.fitted(durationMicros, animationInMicros, animationOutMicros),
                    sourceDurationMicros = sourceDurationMicros,
                )
            val others = track.clips.filter { it.id != clipId }
            val delta = trimmed.endMicros - clip.endMicros
            val placed = if (track.magnetic) others.shiftedFrom(clip.endMicros, delta) else others
            val trimmedTrack = track.copy(clips = (placed + trimmed).sortedBy { it.startMicros })
            val trimmedTracks =
                project.tracks
                    .map { if (it.id == track.id) trimmedTrack else it }
                    .swappingClips(silenced.map { it.clip.id }.toSet(), emptyList())
            settled(trimmedTracks, project.frameRate).swappingClips(emptySet(), restoredCaptions)
        }
    }

    /**
     * Returns [tracks] with anchored clips and captions moved to follow their parents at [frameRate]. Restored
     * captions go back only after this, because until the others have moved back they would overlap them.
     */
    private fun settled(
        tracks: List<Track>,
        frameRate: FrameRate,
    ): List<Track> = CaptionResolver.materialize(AnchorResolver.materialize(tracks), frameRate)
    }
}

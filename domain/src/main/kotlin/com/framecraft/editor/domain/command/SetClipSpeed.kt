/*
 * Command that changes how fast a clip plays its source and whether its sound keeps its pitch.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SpeedMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Plays clip [clipId] at [speed], where null is normal speed, keeping its sound's pitch when [maintainPitch] is set.
 * The clip keeps its start and reads the same source, so its duration follows the speed; on a magnetic track later
 * clips shift by the change, and on other tracks an overlap is rejected. Clips anchored to it keep their offsets.
 * [fadeInMicros] and [fadeOutMicros] set the fades when given; otherwise fades shrink to fit, fade-in first. Image and
 * text clips are rejected, as is a result shorter than one frame.
 */
@Serializable
@SerialName("setClipSpeed")
data class SetClipSpeed(
    val clipId: String,
    val speed: ClipSpeed?,
    val maintainPitch: Boolean = true,
    val fadeInMicros: Long? = null,
    val fadeOutMicros: Long? = null,
) : EditCommand {
    /** The inverse restores the previous speed, pitch setting, and fades. */
    override fun applyTo(project: Project): CommandOutcome {
        val location = project.locate(clipId) ?: return clipNotFound(clipId)
        val clip = location.clip
        val span = clip.sourceSpanMicros
        val durationMicros = SpeedMap.durationFor(speed, span, project.frameRate)
        val minimum = project.frameRate.startOfFrame(1)
        return when {
            !project.assetOf(clip).isTimed -> {
                CommandOutcome.Rejected(CommandRejection.SpeedNotSupported(clipId))
            }

            durationMicros < minimum -> {
                CommandOutcome.Rejected(CommandRejection.ClipTooShort(minimum))
            }

            else -> {
                retimed(project, location, durationMicros)
            }
        }
    }

    /** Replaces the clip at [location] in [project] with its form lasting [durationMicros] at the new speed. */
    private fun retimed(
        project: Project,
        location: ClipLocation,
        durationMicros: Long,
    ): CommandOutcome {
        val (track, clip) = location
        val inverse = SetClipSpeed(clipId, clip.speed, clip.maintainPitch, clip.fadeInMicros, clip.fadeOutMicros)
        return project.replacingTrack(inverse) {
            val fadeIn = fadeInMicros ?: minOf(clip.fadeInMicros, durationMicros)
            val fadeOut = fadeOutMicros ?: minOf(clip.fadeOutMicros, durationMicros - fadeIn)
            val sped =
                clip.copy(
                    durationMicros = durationMicros,
                    speed = speed,
                    sourceDurationMicros = clip.sourceSpanMicros.takeIf { speed != null },
                    maintainPitch = maintainPitch,
                    fadeInMicros = fadeIn,
                    fadeOutMicros = fadeOut,
                )
            val others = track.clips.filter { it.id != clipId }
            val delta = sped.endMicros - clip.endMicros
            val placed = if (track.magnetic) others.shiftedFrom(clip.endMicros, delta) else others
            track.copy(clips = (placed + sped).sortedBy { it.startMicros })
        }
    }
}

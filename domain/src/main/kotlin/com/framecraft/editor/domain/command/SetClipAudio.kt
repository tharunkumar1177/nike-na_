/*
 * Command that changes how loud a clip plays, how its sound fades in and out, and how far it ducks under voiceovers.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets clip [clipId] to play at [volumePercent] with fades of [fadeInMicros] and [fadeOutMicros], ducking by
 * [autoDuckPercent] while a voiceover plays. Image and text clips, which have no sound, and fades that overlap or
 * exceed the clip are rejected.
 */
@Serializable
@SerialName("setClipAudio")
data class SetClipAudio(
    val clipId: String,
    val volumePercent: Int,
    val fadeInMicros: Long,
    val fadeOutMicros: Long,
    val autoDuckPercent: Int = 0,
) : EditCommand {
    /** The inverse restores the previous volume, fades, and ducking. */
    override fun applyTo(project: Project): CommandOutcome {
        val (track, clip) = project.locate(clipId) ?: return clipNotFound(clipId)
        return if (!project.assetOf(clip).isTimed) {
            modelInvariant("Clip $clipId has no sound")
        } else {
            val inverse =
                SetClipAudio(clipId, clip.volumePercent, clip.fadeInMicros, clip.fadeOutMicros, clip.autoDuckPercent)
            project.replacingTrack(inverse) {
                val changed =
                    clip.copy(
                        volumePercent = volumePercent,
                        fadeInMicros = fadeInMicros,
                        fadeOutMicros = fadeOutMicros,
                        autoDuckPercent = autoDuckPercent,
                    )
                track.copy(clips = track.clips.map { if (it.id == clipId) changed else it })
            }
        }
    }
}

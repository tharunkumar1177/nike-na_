/*
 * Command that puts one transition on every cut of a video track that can take it, as a single undo step.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionGeometry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gives every cut on video track [trackId] [transition], each held to what its cut allows, replacing any transition
 * there. Cuts that allow less than the shortest transition keep what they have. The command is rejected off a video
 * track and when no cut can take a transition.
 */
@Serializable
@SerialName("applyTransitionToAll")
data class ApplyTransitionToAll(
    val trackId: String,
    val transition: ClipTransition,
) : EditCommand {
    /** The inverse restores every changed cut's previous transition. */
    override fun applyTo(project: Project): CommandOutcome {
        val track =
            project.tracks.firstOrNull { it.id == trackId }
                ?: return CommandOutcome.Rejected(CommandRejection.TrackNotFound(trackId))
        val values = valuesFor(project, track, transition)
        return when {
            track.kind != TrackKind.VIDEO -> CommandOutcome.Rejected(CommandRejection.TransitionNotSupported(trackId))
            values.isEmpty() -> CommandOutcome.Rejected(CommandRejection.TransitionTooShort(0L))
            else -> SetTransitions(values).applyTo(project)
        }
    }

    /** Which cuts apply-to-all reaches, shared with the editor so it can say how many cuts changed. */
    companion object {
        /** Returns the transition each cut on [track] of [project] takes from [transition], skipping cuts too short. */
        fun valuesFor(
            project: Project,
            track: Track,
            transition: ClipTransition,
        ): List<TransitionValue> =
            if (track.kind != TrackKind.VIDEO) {
                emptyList()
            } else {
                track.clips
                    .zipWithNext()
                    .filter { (left, right) -> left.endMicros == right.startMicros }
                    .mapNotNull { (left, right) ->
                        val cap = TransitionGeometry.capMicros(project, left, right)
                        TransitionGeometry
                            .clampedMicros(transition.durationMicros, cap, project.frameRate)
                            ?.let { TransitionValue(left.id, transition.copy(durationMicros = it)) }
                    }
            }
    }
}

/*
 * Command that sets or clears the transition at the cut after one clip.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionGeometry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gives clip [clipId] [transition] into the clip that starts where it ends, held to what the cut allows, or clears
 * it when null. Setting one is rejected off a video track, where the clip ends at no cut, and where the cut allows
 * less than the shortest transition.
 */
@Serializable
@SerialName("setTransition")
data class SetTransition(
    val clipId: String,
    val transition: ClipTransition?,
) : EditCommand {
    /** The inverse restores the clip's previous transition exactly. */
    override fun applyTo(project: Project): CommandOutcome {
        val location = project.locate(clipId) ?: return clipNotFound(clipId)
        val rejection = transition?.let { rejectionFor(project, location) }
        return if (rejection != null) {
            CommandOutcome.Rejected(rejection)
        } else {
            SetTransitions(listOf(TransitionValue(clipId, transition?.let { fitted(project, location, it) })))
                .applyTo(project)
        }
    }

    /** Returns why a transition cannot go after the clip at [location] in [project], or null when it can. */
    private fun rejectionFor(
        project: Project,
        location: ClipLocation,
    ): CommandRejection? {
        val next = TransitionGeometry.nextOf(location.track, location.clip)
        return when {
            location.track.kind != TrackKind.VIDEO -> {
                CommandRejection.TransitionNotSupported(clipId)
            }

            next == null -> {
                CommandRejection.NotButted(clipId)
            }

            else -> {
                val cap = TransitionGeometry.capMicros(project, location.clip, next)
                val minimum = TransitionGeometry.minimumMicros(project.frameRate)
                CommandRejection.TransitionTooShort(cap).takeIf { cap < minimum }
            }
        }
    }

    /** Returns [wanted] held to what the cut after the clip at [location] in [project] allows. */
    private fun fitted(
        project: Project,
        location: ClipLocation,
        wanted: ClipTransition,
    ): ClipTransition {
        val next: Clip = requireNotNull(TransitionGeometry.nextOf(location.track, location.clip))
        val cap = TransitionGeometry.capMicros(project, location.clip, next)
        val duration = TransitionGeometry.clampedMicros(wanted.durationMicros, cap, project.frameRate)
        return wanted.copy(durationMicros = requireNotNull(duration))
    }
}

/*
 * Edit commands: pure, serializable project transformations that return their own inverse.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import kotlinx.serialization.Serializable

/** A user edit. Applying it never mutates the input; success returns the next project and the inverse command. */
@Serializable
sealed interface EditCommand {
    /** Applies this command to [project]. */
    fun applyTo(project: Project): CommandOutcome
}

/** Result of applying an [EditCommand]. */
sealed interface CommandOutcome {
    /** The command produced [project]; applying [inverse] to it restores the input. */
    data class Applied(
        val project: Project,
        val inverse: EditCommand,
    ) : CommandOutcome

    /** The command could not apply; the project is unchanged. */
    data class Rejected(
        val reason: CommandRejection,
    ) : CommandOutcome
}

/** Why a command was rejected. */
sealed interface CommandRejection {
    /** No clip with [clipId] exists. */
    data class ClipNotFound(
        val clipId: String,
    ) : CommandRejection

    /** No track with [trackId] exists. */
    data class TrackNotFound(
        val trackId: String,
    ) : CommandRejection

    /** A project name must contain visible characters. */
    data object BlankName : CommandRejection

    /** The result would break a model invariant described by [detail]. */
    data class ModelInvariant(
        val detail: String,
    ) : CommandRejection

    /** A clip would be shorter than one frame, [minimumMicros]. */
    data class ClipTooShort(
        val minimumMicros: Long,
    ) : CommandRejection
}

/** A clip together with the track that holds it. */
internal data class ClipLocation(
    val track: Track,
    val clip: Clip,
)

/** Returns the clip [clipId] and its track, or null if no track holds it. */
internal fun Project.locate(clipId: String): ClipLocation? {
    val track = tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } }
    return track?.let { ClipLocation(it, it.clips.first { clip -> clip.id == clipId }) }
}

/** Returns the rejection for a missing clip [clipId]. */
internal fun clipNotFound(clipId: String): CommandOutcome =
    CommandOutcome.Rejected(CommandRejection.ClipNotFound(clipId))

/** Returns these clips with every clip starting at or after [fromMicros] moved by [deltaMicros]. */
internal fun List<Clip>.shiftedFrom(
    fromMicros: Long,
    deltaMicros: Long,
): List<Clip> =
    map { clip ->
        if (clip.startMicros >= fromMicros) clip.copy(startMicros = clip.startMicros + deltaMicros) else clip
    }

/**
 * Replaces the track that [build] returns (matched by id) and pairs the result with [inverse].
 * Model validation failures become [CommandRejection.ModelInvariant].
 */
internal fun Project.replacingTrack(
    inverse: EditCommand,
    build: () -> Track,
): CommandOutcome {
    val result =
        runCatching {
            val track = build()
            copy(tracks = tracks.map { if (it.id == track.id) track else it })
        }
    return result.fold(
        onSuccess = { CommandOutcome.Applied(it, inverse) },
        onFailure = { CommandOutcome.Rejected(CommandRejection.ModelInvariant(it.message.orEmpty())) },
    )
}

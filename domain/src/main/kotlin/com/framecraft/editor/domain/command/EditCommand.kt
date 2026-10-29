/*
 * Edit commands: pure, serializable project transformations that return their own inverse.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.AnchorResolver
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.MediaAsset
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

/** Returns the rejection for a result that would break a model rule described by [detail]. */
internal fun modelInvariant(detail: String): CommandOutcome =
    CommandOutcome.Rejected(CommandRejection.ModelInvariant(detail))

/** Returns these tracks with every clip replaced by [transform]; clip starts must not change. */
internal fun List<Track>.mapClips(transform: (Clip) -> Clip): List<Track> =
    map { track -> track.copy(clips = track.clips.map(transform)) }

/** Returns [anchor] for a clip whose start moved by [deltaMicros] while keeping the same parent. */
internal fun shiftedAnchor(
    anchor: ClipAnchor?,
    deltaMicros: Long,
): ClipAnchor? = anchor?.copy(offsetMicros = anchor.offsetMicros + deltaMicros)

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
 * Anchored clips follow their parents; model validation failures become [CommandRejection.ModelInvariant].
 */
internal fun Project.replacingTrack(
    inverse: EditCommand,
    build: () -> Track,
): CommandOutcome =
    replacingTracks(inverse) {
        val track = build()
        tracks.map { if (it.id == track.id) track else it }
    }

/**
 * Replaces all tracks with those [build] returns, and the assets with [assets], pairing the result with [inverse].
 * Anchored clips follow their parents; model validation failures become [CommandRejection.ModelInvariant].
 */
internal fun Project.replacingTracks(
    inverse: EditCommand,
    assets: List<MediaAsset> = this.assets,
    build: () -> List<Track>,
): CommandOutcome {
    val result = runCatching { copy(assets = assets, tracks = AnchorResolver.materialize(build())) }
    return result.fold(
        onSuccess = { CommandOutcome.Applied(it, inverse) },
        onFailure = { CommandOutcome.Rejected(CommandRejection.ModelInvariant(it.message.orEmpty())) },
    )
}

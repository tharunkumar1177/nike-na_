/*
 * Command that sets the volume of whole tracks exactly as given; the mixer sends it and it undoes itself.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Track [trackId] plays at [volumePercent] of its clips' own levels. */
@Serializable
data class TrackVolumeValue(
    val trackId: String,
    val volumePercent: Int,
)

/**
 * Gives each track in [values] its volume as given, so undo always restores what was there. A missing track, a text
 * track, and a volume out of range reject the whole command.
 */
@Serializable
@SerialName("setTrackVolumes")
data class SetTrackVolumes(
    val values: List<TrackVolumeValue>,
) : EditCommand {
    /** The inverse gives the same tracks their previous volumes. */
    override fun applyTo(project: Project): CommandOutcome {
        val tracksById = project.tracks.associateBy { it.id }
        val missing = values.firstOrNull { it.trackId !in tracksById }
        val silent = values.firstOrNull { tracksById[it.trackId]?.kind?.takesVolume == false }
        return when {
            missing != null -> CommandOutcome.Rejected(CommandRejection.TrackNotFound(missing.trackId))
            silent != null -> CommandOutcome.Rejected(CommandRejection.TrackVolumeNotSupported(silent.trackId))
            else -> {
                val previous =
                    values.map { TrackVolumeValue(it.trackId, tracksById.getValue(it.trackId).volumePercent) }
                val byTrack = values.associate { it.trackId to it.volumePercent }
                project.replacingTracks(SetTrackVolumes(previous)) {
                    project.tracks.map { track ->
                        byTrack[track.id]?.let { track.copy(volumePercent = it) } ?: track
                    }
                }
            }
        }
    }
}

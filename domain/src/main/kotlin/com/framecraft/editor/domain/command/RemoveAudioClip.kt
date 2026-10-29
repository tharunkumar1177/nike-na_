/*
 * Command that removes a sound clip together with its now-unused asset and empty track.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Removes audio clip [clipId]. Its asset goes too unless another clip reads it, and its track goes if it becomes
 * empty. Clips anchored to the removed clip lose their anchor and stay where they are. When this undoes an
 * extraction, [restoreVolumeClipId] names the source clip whose volume returns to [restoreVolumePercent].
 */
@Serializable
@SerialName("removeAudioClip")
data class RemoveAudioClip(
    val clipId: String,
    val restoreVolumeClipId: String? = null,
    val restoreVolumePercent: Int? = null,
) : EditCommand {
    /** The inverse adds the clip back, or extracts it again when this undid an extraction. */
    override fun applyTo(project: Project): CommandOutcome {
        val location = project.locate(clipId) ?: return clipNotFound(clipId)
        val sourceId = restoreVolumeClipId
        return when {
            location.track.kind != TrackKind.AUDIO -> modelInvariant("Clip $clipId is not an audio clip")
            sourceId != null && project.locate(sourceId) == null -> clipNotFound(sourceId)
            else -> remove(project, location)
        }
    }

    /** Removes the clip at [location] from [project], restoring the source volume when this undoes an extraction. */
    private fun remove(
        project: Project,
        location: ClipLocation,
    ): CommandOutcome {
        val (track, clip) = location
        val asset = project.assetOf(clip)
        val restorations = restorationsFor(project.tracks, clipId)
        val index = project.tracks.indexOf(track)
        val sourceId = restoreVolumeClipId
        val inverse =
            if (sourceId == null) {
                AddAudioClip(track.id, clip, asset, restorations, index)
            } else {
                ExtractAudio(sourceId, track.id, clip, index, restorations)
            }
        val shared = project.tracks.any { lane -> lane.clips.any { it.id != clipId && it.assetId == asset.id } }
        val assets = if (shared) project.assets else project.assets.filter { it.id != asset.id }
        val remaining = track.clips.filter { it.id != clipId }
        return project.replacingTracks(inverse, assets) {
            val lanes =
                project.tracks.mapNotNull { lane ->
                    when {
                        lane.id != track.id -> lane
                        remaining.isEmpty() -> null
                        else -> lane.copy(clips = remaining)
                    }
                }
            lanes.mapClips(::restoredVolume).orphaningChildrenOf(clipId)
        }
    }

    /** Returns [clip] with its volume restored if it is the extraction source this command names. */
    private fun restoredVolume(clip: Clip): Clip {
        val volume = restoreVolumePercent
        return if (clip.id == restoreVolumeClipId && volume != null) clip.copy(volumePercent = volume) else clip
    }
}

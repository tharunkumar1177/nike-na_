/*
 * Command that lifts a video clip's sound onto an audio track, attached to the clip, and mutes the video.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places audio [clip] on audio track [trackId] (created at [trackIndex], clamped, when missing) and mutes video clip
 * [videoClipId], so the sound plays once. The audio clip must read the video's asset, which must have sound. Each of
 * [anchorRestorations] re-attaches a clip that lost its anchor when the audio clip was removed.
 */
@Serializable
@SerialName("extractAudio")
data class ExtractAudio(
    val videoClipId: String,
    val trackId: String,
    val clip: Clip,
    val trackIndex: Int = Int.MAX_VALUE,
    val anchorRestorations: List<AnchorRestoration> = emptyList(),
) : EditCommand {
    /** The inverse removes the audio clip and restores the video's previous volume. */
    override fun applyTo(project: Project): CommandOutcome {
        val (_, video) = project.locate(videoClipId) ?: return clipNotFound(videoClipId)
        val asset = project.assetOf(video)
        return if (asset.kind != MediaKind.VIDEO || !asset.hasAudio || clip.assetId != asset.id) {
            modelInvariant("Clip ${clip.id} does not carry the sound of video clip $videoClipId")
        } else {
            val inverse = RemoveAudioClip(clip.id, videoClipId, video.volumePercent)
            project.replacingTracks(inverse) {
                project.tracks
                    .mapClips { if (it.id == videoClipId) it.copy(volumePercent = 0) else it }
                    .withLaneClip(TrackKind.AUDIO, trackId, clip, trackIndex)
                    .restoringAnchors(anchorRestorations)
            }
        }
    }
}

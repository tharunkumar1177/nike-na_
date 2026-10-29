/*
 * Decides where new sound goes: imported music at the playhead and extracted audio under its video clip, each on
 * the first audio lane with room.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.command.AddAudioClip
import com.framecraft.editor.domain.command.ExtractAudio
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT

/** Stand-in id for a lane that would be created, used when only asking whether one fits. */
private const val NEW_LANE = "new-lane"

/** Places new audio clips on audio lanes. */
object AudioPlacement {
    /** Most audio lanes a project may hold. */
    const val MAX_AUDIO_LANES: Int = 8

    /**
     * Returns the command that adds audio [asset] in full at [playheadMicros], snapped to a frame, on the first audio
     * lane with room, or on a new lane below the others. Returns null when the asset is not audio or all
     * [MAX_AUDIO_LANES] lanes are busy there. The clip may run past the end of the video.
     */
    fun proposeMusic(
        project: Project,
        playheadMicros: Long,
        asset: MediaAsset,
        idGenerator: IdGenerator,
    ): AddAudioClip? {
        val duration = asset.durationMicros?.takeIf { asset.kind == MediaKind.AUDIO } ?: return null
        val start = project.frameRate.snapToNearestFrame(playheadMicros.coerceAtLeast(0L))
        return laneFor(project, start, start + duration) { idGenerator.next() }?.let { laneId ->
            AddAudioClip(laneId, Clip(idGenerator.next(), asset.id, start, 0L, duration), asset)
        }
    }

    /**
     * Returns the command that extracts the sound of video clip [videoClipId] onto the first audio lane with room
     * over the same time, attached to the video clip. Returns null when the clip has no sound to extract, its sound
     * was already extracted, or all [MAX_AUDIO_LANES] lanes are busy there.
     */
    fun proposeExtract(
        project: Project,
        videoClipId: String,
        idGenerator: IdGenerator,
    ): ExtractAudio? {
        val video = project.tracks.flatMap { it.clips }.firstOrNull { it.id == videoClipId }
        val laneId =
            video
                ?.takeIf { canExtract(project, it) }
                ?.let { laneFor(project, it.startMicros, it.endMicros) { idGenerator.next() } }
        return if (video == null || laneId == null) {
            null
        } else {
            val clip =
                video.copy(
                    id = idGenerator.next(),
                    anchor = ClipAnchor(video.id, 0L),
                    overlayTransform = null,
                    canvasPlacement = null,
                    volumePercent = video.volumePercent.takeIf { it > 0 } ?: UNITY_VOLUME_PERCENT,
                )
            ExtractAudio(video.id, laneId, clip)
        }
    }

    /**
     * True when [video] reads a video asset with sound whose audio was not already extracted and an audio lane has
     * room over its time.
     */
    fun canExtract(
        project: Project,
        video: Clip,
    ): Boolean {
        val asset = project.assetOf(video)
        val extracted =
            project.tracks
                .filter { it.kind == TrackKind.AUDIO }
                .flatMap { it.clips }
                .any { it.anchor?.parentClipId == video.id && it.assetId == video.assetId }
        val hasRoom = laneFor(project, video.startMicros, video.endMicros) { NEW_LANE } != null
        return asset.kind == MediaKind.VIDEO && asset.hasAudio && !extracted && hasRoom
    }

    /**
     * Returns the id of the first audio lane free over [startMicros] until [endMicros], or a new id from [newId] when
     * none is free and another lane fits, or null when every lane is taken.
     */
    private fun laneFor(
        project: Project,
        startMicros: Long,
        endMicros: Long,
        newId: () -> String,
    ): String? {
        val lanes = project.tracks.filter { it.kind == TrackKind.AUDIO }
        val free =
            lanes.firstOrNull { lane -> lane.clips.none { it.startMicros < endMicros && startMicros < it.endMicros } }
        return when {
            free != null -> free.id
            lanes.size < MAX_AUDIO_LANES -> newId()
            else -> null
        }
    }
}

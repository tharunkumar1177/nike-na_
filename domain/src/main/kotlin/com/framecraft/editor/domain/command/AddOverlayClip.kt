/*
 * Command that places a picture-in-picture clip on an overlay track, creating the track if needed.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places [clip], which reads [asset], on overlay track [trackId]. The asset is added if the project lacks it. A
 * missing track is created at position [trackIndex] of the track list, clamped to the list, which defaults to the
 * start so a new lane is drawn above the base video, at [trackVolumePercent]. An overlap is rejected. Each of
 * [anchorRestorations] then re-attaches a clip that lost its anchor when this clip was removed.
 */
@Serializable
@SerialName("addOverlayClip")
data class AddOverlayClip(
    val trackId: String,
    val clip: Clip,
    val asset: MediaAsset,
    val anchorRestorations: List<AnchorRestoration> = emptyList(),
    val trackIndex: Int = 0,
    val trackVolumePercent: Int = UNITY_VOLUME_PERCENT,
) : EditCommand {
    /** The inverse removes the overlay clip again. */
    override fun applyTo(project: Project): CommandOutcome {
        val existing = project.tracks.firstOrNull { it.id == trackId }
        val known = project.assets.firstOrNull { it.id == asset.id }
        return when {
            existing != null && existing.kind != TrackKind.OVERLAY -> {
                modelInvariant("Track $trackId is not an overlay track")
            }

            known != null && known != asset -> {
                modelInvariant("Asset ${asset.id} already exists with different details")
            }

            else -> {
                val assets = if (known == null) project.assets + asset else project.assets
                project.replacingTracks(RemoveOverlayClip(clip.id), assets) {
                    val tracks =
                        if (existing == null) {
                            val lane = Track(trackId, TrackKind.OVERLAY, false, listOf(clip), trackVolumePercent)
                            project.tracks.toMutableList().apply {
                                add(trackIndex.coerceIn(0, project.tracks.size), lane)
                            }
                        } else {
                            val placed = existing.copy(clips = (existing.clips + clip).sortedBy { it.startMicros })
                            project.tracks.map { if (it.id == trackId) placed else it }
                        }
                    tracks.restoringAnchors(anchorRestorations)
                }
            }
        }
    }
}

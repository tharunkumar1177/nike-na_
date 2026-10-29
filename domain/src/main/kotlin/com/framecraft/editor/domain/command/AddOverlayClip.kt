/*
 * Command that places a picture-in-picture clip on an overlay track, creating the track if needed.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places [clip], which reads [asset], on overlay track [trackId]. The asset is added if the project lacks it, and the
 * track is created at the end of the track list if it does not exist. An overlap is rejected. Each of
 * [anchorRestorations] then re-attaches a clip that lost its anchor when this clip was removed.
 */
@Serializable
@SerialName("addOverlayClip")
data class AddOverlayClip(
    val trackId: String,
    val clip: Clip,
    val asset: MediaAsset,
    val anchorRestorations: List<AnchorRestoration> = emptyList(),
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
                            project.tracks + Track(trackId, TrackKind.OVERLAY, false, listOf(clip))
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

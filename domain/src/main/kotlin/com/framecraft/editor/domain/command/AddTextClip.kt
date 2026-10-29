/*
 * Command that places a text clip on a text lane, creating the lane if needed.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TEXT_ASSET
import com.framecraft.editor.domain.model.TrackKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places text [clip] on text track [trackId]. The shared text asset is added if the project lacks it. A missing track
 * is created at position [trackIndex] of the track list, clamped to the list, which defaults to the start so text is
 * listed above every picture. An overlap or a clip that is not text is rejected. Each of [anchorRestorations] then
 * re-attaches a clip that lost its anchor when this clip was removed.
 */
@Serializable
@SerialName("addTextClip")
data class AddTextClip(
    val trackId: String,
    val clip: Clip,
    val anchorRestorations: List<AnchorRestoration> = emptyList(),
    val trackIndex: Int = 0,
) : EditCommand {
    /** The inverse removes the text clip again. */
    override fun applyTo(project: Project): CommandOutcome {
        val known = project.assets.any { it.id == TEXT_ASSET.id }
        val assets = if (known) project.assets else project.assets + TEXT_ASSET
        return project.replacingTracks(RemoveTextClip(clip.id), assets) {
            project.tracks
                .withLaneClip(TrackKind.TEXT, trackId, clip, trackIndex)
                .restoringAnchors(anchorRestorations)
        }
    }
}

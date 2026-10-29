/*
 * Records which clips lost their anchor when a parent was removed, so undo can re-attach them.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.AnchorResolver
import com.framecraft.editor.domain.model.ClipAnchor
import com.framecraft.editor.domain.model.Track
import kotlinx.serialization.Serializable

/** Re-attaches clip [clipId] with [anchor] when a removed parent comes back. */
@Serializable
data class AnchorRestoration(
    val clipId: String,
    val anchor: ClipAnchor,
)

/** Returns how to re-attach every clip in [tracks] that is anchored directly to [parentClipId]. */
internal fun restorationsFor(
    tracks: List<Track>,
    parentClipId: String,
): List<AnchorRestoration> =
    AnchorResolver.childrenOf(tracks, parentClipId).map { child ->
        AnchorRestoration(child.id, requireNotNull(child.anchor))
    }

/** Returns these tracks with every clip anchored directly to [parentClipId] detached where it stands. */
internal fun List<Track>.orphaningChildrenOf(parentClipId: String): List<Track> =
    mapClips { clip -> if (clip.anchor?.parentClipId == parentClipId) clip.copy(anchor = null) else clip }

/** Returns these tracks with the clips named in [restorations] re-attached to their anchors. */
internal fun List<Track>.restoringAnchors(restorations: List<AnchorRestoration>): List<Track> {
    val anchors = restorations.associate { it.clipId to it.anchor }
    return if (anchors.isEmpty()) {
        this
    } else {
        mapClips { clip -> anchors[clip.id]?.let { clip.copy(anchor = it) } ?: clip }
    }
}

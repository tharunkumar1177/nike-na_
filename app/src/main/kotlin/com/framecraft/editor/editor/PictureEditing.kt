/*
 * Wires a picture clip's look, mask, and chroma key editing: the state of their sheet and panels, and the preview
 * layers the mask and chroma key panels put over the picture.
 */
package com.framecraft.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetClipChromaKey
import com.framecraft.editor.domain.command.SetClipMask
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.takesLook
import kotlinx.coroutines.launch

/** The look sheet, mask panel, and chroma key panel of picture clips; at most one panel is open at a time. */
internal class PictureEditing(
    val look: LookEditing,
    val mask: MaskEditing,
    val chroma: ChromaEditing,
) {
    /** Opens the mask panel on clip [id], closing the chroma key panel. */
    fun openMask(id: String?) {
        chroma.close()
        mask.open(id)
    }

    /** Opens the chroma key panel on clip [id], closing the mask panel. */
    fun openChromaKey(id: String?) {
        mask.close()
        chroma.open(id)
    }
}

/** Returns the picture editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberPictureEditing(): PictureEditing =
    PictureEditing(rememberLookEditing(), rememberMaskEditing(), rememberChromaEditing())

/** Returns clip [clipId] of [project] with its track kind when it sits on a track that takes picture effects. */
internal fun pictureClipOf(
    project: Project,
    clipId: String,
): Pair<Clip, TrackKind>? {
    val track = project.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } }
    val clip = track?.clips?.firstOrNull { it.id == clipId }
    return clip?.takeIf { track.kind.takesLook }?.let { it to track.kind }
}

/**
 * Returns the preview layer that [editing]'s panels put over clip [selectedClipId] of [project] while the playhead at
 * [positionMicros] is inside it: the eyedropper while the chroma key panel is picking, or the mask handles while the
 * mask panel is open on a masked clip. Edits go to [viewModel] and the eyedropper reads frames through its probe.
 * Null leaves the preview to the other layers.
 */
@Composable
internal fun pictureEffectLayer(
    editing: PictureEditing,
    project: Project,
    selectedClipId: String?,
    positionMicros: Long,
    viewModel: EditorViewModel,
): (@Composable (Modifier) -> Unit)? {
    val (clip, kind) = selectedClipId?.let { pictureClipOf(project, it) } ?: return null
    val dispatch: (EditCommand) -> Unit = viewModel::dispatch
    val probe = viewModel.keyColorProbe
    val boxOf = { canvas: Size -> PictureBox.of(project, clip, kind, canvas) }
    val pickLayer: @Composable (Modifier) -> Unit = { layerModifier ->
        key(clip.id) {
            val scope = rememberCoroutineScope()
            KeyPickLayer(
                boxOf = boxOf,
                onPick = { point ->
                    scope.launch {
                        val source = KeyColorMath.sourceMicrosAt(clip, positionMicros, project.frameRate)
                        val asset = project.assetOf(clip)
                        val frame = probe.frameOf(asset.uri, asset.kind, source)
                        editing.chroma.pickFailed = frame == null
                        frame?.let {
                            dispatch(SetClipChromaKey(clip.id, keyed(clip.chromaKey, KeyColorMath.colorAt(it, point))))
                            editing.chroma.picking = false
                        }
                    }
                },
                modifier = layerModifier,
            )
        }
    }
    val maskLayer: @Composable (Modifier) -> Unit = { layerModifier ->
        clip.mask?.let { mask ->
            key(clip.id) {
                MaskHandlesLayer(mask, boxOf, { dispatch(SetClipMask(clip.id, it)) }, layerModifier)
            }
        }
    }
    val onClip = positionMicros >= clip.startMicros && positionMicros < clip.endMicros
    return when {
        onClip && editing.chroma.clipId == clip.id && editing.chroma.picking -> pickLayer
        onClip && editing.mask.clipId == clip.id && clip.mask != null -> maskLayer
        else -> null
    }
}

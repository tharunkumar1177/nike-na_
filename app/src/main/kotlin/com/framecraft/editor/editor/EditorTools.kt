/*
 * Wires the editor's tools zone and preview overlay handles to the view model: availability, actions, and picker.
 */
package com.framecraft.editor.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.framecraft.editor.domain.command.DeleteClip
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.RemoveOverlayClip
import com.framecraft.editor.domain.command.SetOverlayTransform
import com.framecraft.editor.domain.command.SplitClip
import com.framecraft.editor.domain.edit.OverlayPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.engine.ProjectPreview

/**
 * Returns an action that opens the photo and video picker and adds the picked item through [overlays] at
 * [preview]'s playhead when it returns.
 */
@Composable
internal fun rememberOverlayPicker(
    overlays: OverlayImport,
    preview: ProjectPreview,
): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { overlays.add(it.toString(), preview.state.value.positionMicros) }
        }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

/**
 * Returns which tools apply to [selected], which is the [overlay] clip when an overlay is selected, at
 * [positionMicros]. Overlays can be added when the project has a base clip and no pick is still [overlayBusy].
 */
internal fun toolAvailability(
    project: Project,
    selected: Clip?,
    overlay: Clip?,
    positionMicros: Long,
    overlayBusy: Boolean,
): ToolAvailability =
    ToolAvailability(
        hasSelection = selected != null,
        canSplit = selected != null && SplitClip.isSplittable(selected, positionMicros, project.frameRate),
        canAddOverlay = !overlayBusy && OverlayPlacement.baseTrack(project) != null,
        overlayOpacity = overlay?.let { (it.overlayTransform ?: OverlayTransform()).opacity },
    )

/**
 * Split at [positionMicros], delete, and opacity for the [selected] clip, which is an overlay when [isOverlay], and
 * [onAddOverlay] to pick overlay media. The clip tools do nothing without a selection.
 */
internal fun toolActions(
    viewModel: EditorViewModel,
    selected: Clip?,
    isOverlay: Boolean,
    positionMicros: Long,
    onAddOverlay: () -> Unit,
): ToolActions =
    ToolActions(
        onSplitClip = {
            selected?.let { viewModel.dispatch(SplitClip(it.id, positionMicros, viewModel.idGenerator.next())) }
        },
        onDeleteClip = {
            selected?.let { viewModel.dispatch(if (isOverlay) RemoveOverlayClip(it.id) else DeleteClip(it.id)) }
        },
        onAddOverlay = onAddOverlay,
        onOpacityCommit = { opacity ->
            selected?.let { clip ->
                val transform = (clip.overlayTransform ?: OverlayTransform()).copy(opacity = opacity.coerceIn(0f, 1f))
                viewModel.dispatch(SetOverlayTransform(clip.id, transform))
            }
        },
    )

/**
 * Returns the handles layer for overlay [clip] of [project]; a finished gesture goes to [dispatch] as one placement
 * change, and [haptics] ticks when rotation snaps.
 */
internal fun overlayLayer(
    clip: Clip,
    project: Project,
    dispatch: (EditCommand, String?) -> Unit,
    haptics: HapticFeedback,
): @Composable (Modifier) -> Unit =
    { layerModifier ->
        key(clip.id) {
            OverlayTransformLayer(
                transform = clip.overlayTransform ?: OverlayTransform(),
                overlayAspect = project.assetOf(clip).displayAspectRatio,
                onCommit = { dispatch(SetOverlayTransform(clip.id, it), null) },
                onSnap = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                modifier = layerModifier,
            )
        }
    }

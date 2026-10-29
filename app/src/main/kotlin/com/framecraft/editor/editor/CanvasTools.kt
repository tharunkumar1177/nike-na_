/*
 * Wires the canvas tools to the view model: the canvas sheet, base clip fit and fill, and the base clip placement
 * handles on the preview.
 */
package com.framecraft.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetBaseClipPlacement
import com.framecraft.editor.domain.command.SetCanvasAspect
import com.framecraft.editor.domain.command.SetCanvasBackground
import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind

/** Whether the canvas sheet is open and which base clip, if any, shows its placement handles. */
internal class CanvasEditing {
    /** True while the canvas sheet shows. */
    var sheetOpen by mutableStateOf(false)

    /** Base clip whose handles show; selecting another clip hides them. */
    private var adjustingClipId by mutableStateOf<String?>(null)

    /** Returns true when the handles of clip [clipId] show. */
    fun isAdjusting(clipId: String?): Boolean = clipId != null && adjustingClipId == clipId

    /** Shows the handles of clip [clipId], or hides them when they already show. */
    fun toggleAdjust(clipId: String?) {
        adjustingClipId = if (isAdjusting(clipId)) null else clipId
    }
}

/** Returns the canvas editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberCanvasEditing(): CanvasEditing = remember { CanvasEditing() }

/**
 * Returns the canvas tools for [selected], which sits on a track of [selectedKind]. The canvas tool shows with no
 * selection or a base clip selected and waits while a pick is still [importing] or the project is empty; the fit and
 * adjust tools show for a base clip, with [editing] telling whether its handles show.
 */
internal fun canvasToolState(
    project: Project,
    selected: Clip?,
    selectedKind: TrackKind?,
    importing: Boolean,
    editing: CanvasEditing,
): CanvasToolState {
    val isBase = selected != null && selectedKind == TrackKind.VIDEO
    return CanvasToolState(
        show = selectedKind == null || isBase,
        enabled = !importing && project.tracks.any { it.clips.isNotEmpty() },
        isBase = isBase,
        fill = isBase && selected?.canvasPlacement?.fit == CanvasFit.FILL,
        adjusting = isBase && editing.isAdjusting(selected?.id),
    )
}

/**
 * Returns [actions] with the canvas tools wired: the canvas tool opens the sheet through [editing], and for the
 * [selected] base clip the fit tool goes to [dispatch] and the adjust tool shows or hides its handles.
 */
internal fun withCanvasActions(
    actions: ToolActions,
    selected: Clip?,
    editing: CanvasEditing,
    dispatch: (EditCommand) -> Unit,
): ToolActions =
    actions.copy(
        onOpenCanvas = { editing.sheetOpen = true },
        onToggleFit = { selected?.let { dispatch(SetBaseClipPlacement(it.id, toggledFit(it.canvasPlacement))) } },
        onToggleAdjust = { editing.toggleAdjust(selected?.id) },
    )

/**
 * Returns [placement] with fit and fill swapped, keeping where the clip sits; a result that matches a clip with no
 * placement is null, so the project stays as if never placed.
 */
internal fun toggledFit(placement: CanvasPlacement?): CanvasPlacement? {
    val current = placement ?: CanvasPlacement()
    val next = current.copy(fit = if (current.fit == CanvasFit.FIT) CanvasFit.FILL else CanvasFit.FIT)
    return next.takeUnless { it == CanvasPlacement() }
}

/** Returns the placement handles' transform for [placement]: the same center, scale, and turn, fully opaque. */
internal fun transformOf(placement: CanvasPlacement): OverlayTransform =
    OverlayTransform(placement.centerX, placement.centerY, placement.scale, placement.rotationDegrees)

/** Returns the placement that keeps [fit] and takes [transform]'s center, scale, and turn; centered fit is null. */
internal fun placementOf(
    fit: CanvasFit,
    transform: OverlayTransform,
): CanvasPlacement? =
    CanvasPlacement(fit, transform.centerX, transform.centerY, transform.scale, transform.rotationDegrees)
        .takeUnless { it == CanvasPlacement() }

/** Shows the canvas sheet for [project] while [editing] has it open; each tap goes to [dispatch] as one edit. */
@Composable
internal fun CanvasSheetHost(
    editing: CanvasEditing,
    project: Project,
    dispatch: (EditCommand) -> Unit,
) {
    if (editing.sheetOpen) {
        CanvasSheet(
            canvas = project.canvas,
            onAspect = { if (it != project.canvas.aspect) dispatch(SetCanvasAspect(it)) },
            onBackground = { if (it != project.canvas.background) dispatch(SetCanvasBackground(it)) },
            onDismiss = { editing.sheetOpen = false },
        )
    }
}

/**
 * Returns the placement handles for base [clip] of [project]; the box matches the clip's fitted or filled size on the
 * canvas, a finished gesture goes to [dispatch] as one placement change, and [haptics] ticks when rotation snaps.
 */
internal fun baseLayer(
    clip: Clip,
    project: Project,
    dispatch: (EditCommand, String?) -> Unit,
    haptics: HapticFeedback,
): @Composable (Modifier) -> Unit =
    { layerModifier ->
        key(clip.id) {
            val placement = clip.canvasPlacement ?: CanvasPlacement()
            val aspect = project.assetOf(clip).displayAspectRatio
            OverlayTransformLayer(
                transform = transformOf(placement),
                boxSize = { canvas, scale ->
                    if (placement.fit == CanvasFit.FILL) {
                        OverlayGeometry.fillBoxSize(canvas, aspect, scale)
                    } else {
                        OverlayGeometry.boxSize(canvas, aspect, scale)
                    }
                },
                onCommit = { dispatch(SetBaseClipPlacement(clip.id, placementOf(placement.fit, it)), null) },
                onSnap = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                modifier = layerModifier,
                description = stringResource(R.string.editor_base_box),
            )
        }
    }

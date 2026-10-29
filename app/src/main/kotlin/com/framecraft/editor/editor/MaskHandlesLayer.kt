/*
 * Handles drawn over the preview for the selected clip's mask: drag to move it, pinch to resize it, and twist to
 * turn it, all within the clip's own picture.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.ClipMask

/** Test tag of the mask handles layer. */
const val MASK_LAYER_TAG = "editor_mask_layer"

/** Share of the picture width or height an accessibility move action shifts the mask by. */
private const val MASK_NUDGE = 0.05f

/** Factor of the accessibility resize actions. */
private const val MASK_ZOOM_STEP = 1.1f

/** Degrees the accessibility rotate actions turn the mask by. */
private const val MASK_TURN_STEP = 15f

/** Width of the mask outline. */
private val MASK_OUTLINE_WIDTH = 2.dp

/** Radius of the mask's center handle. */
private val MASK_CENTER_RADIUS = 5.dp

/**
 * Draws [mask]'s outline on the clip picture that [boxOf] places on a preview of a given size. Drags move it and
 * pinches and twists resize and turn it anywhere on the preview; the result goes to [onCommit] once, when the last
 * finger lifts. TalkBack offers moves, resizes, and turns as actions.
 */
@Composable
internal fun MaskHandlesLayer(
    mask: ClipMask,
    boxOf: (Size) -> PictureBox?,
    onCommit: (ClipMask) -> Unit,
    modifier: Modifier = Modifier,
) {
    var live by remember(mask) { mutableStateOf(mask) }
    var canvas by remember { mutableStateOf(Size.Zero) }
    val commit by rememberUpdatedState(onCommit)
    val currentBoxOf by rememberUpdatedState(boxOf)
    val color = MaterialTheme.colorScheme.primary
    val actions =
        maskActions { pan, zoom, turn ->
            boxOf(canvas)?.let { box ->
                val move = Offset(pan.x * box.size.width, pan.y * box.size.height)
                commit(MaskGeometry.transformed(mask, box, move, zoom, turn))
            }
        }
    val description = stringResource(R.string.editor_mask_handles)
    Canvas(
        modifier =
            modifier
                .testTag(MASK_LAYER_TAG)
                .onSizeChanged { canvas = it.toSize() }
                .maskGestures(mask, { currentBoxOf(it) }, onChange = { live = it }, onCommit = { commit(it) })
                .semantics {
                    contentDescription = description
                    customActions = actions
                },
    ) {
        boxOf(size)?.let { drawMask(live, it, color) }
    }
}

/**
 * Turns every gesture on the preview into a change of [mask] on the picture [boxOf] places: [onChange] gets each
 * step and [onCommit] the final mask when the last finger lifts, if it moved.
 */
private fun Modifier.maskGestures(
    mask: ClipMask,
    boxOf: (Size) -> PictureBox?,
    onChange: (ClipMask) -> Unit,
    onCommit: (ClipMask) -> Unit,
): Modifier =
    pointerInput(mask) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val box = boxOf(size.toSize())
            var shown = mask
            do {
                val event = awaitPointerEvent()
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val turn = event.calculateRotation()
                if (box != null && (pan != Offset.Zero || zoom != 1f || turn != 0f)) {
                    shown = MaskGeometry.transformed(shown, box, pan, zoom, turn)
                    onChange(shown)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (shown != mask) onCommit(shown)
        }
    }

/**
 * Returns the TalkBack actions for a mask: move in four directions, resize, and turn. Each calls [apply] with a move
 * as a share of the picture's width and height, a zoom factor, and a clockwise turn.
 */
@Composable
private fun maskActions(apply: (pan: Offset, zoom: Float, turn: Float) -> Unit): List<CustomAccessibilityAction> {
    /** Returns an action labelled [label] that applies the given change. */
    fun action(
        label: String,
        pan: Offset = Offset.Zero,
        zoom: Float = 1f,
        turn: Float = 0f,
    ): CustomAccessibilityAction =
        CustomAccessibilityAction(label) {
            apply(pan, zoom, turn)
            true
        }
    return listOf(
        action(stringResource(R.string.editor_overlay_move_left), Offset(-MASK_NUDGE, 0f)),
        action(stringResource(R.string.editor_overlay_move_right), Offset(MASK_NUDGE, 0f)),
        action(stringResource(R.string.editor_overlay_move_up), Offset(0f, -MASK_NUDGE)),
        action(stringResource(R.string.editor_overlay_move_down), Offset(0f, MASK_NUDGE)),
        action(stringResource(R.string.editor_overlay_bigger), zoom = MASK_ZOOM_STEP),
        action(stringResource(R.string.editor_overlay_smaller), zoom = 1f / MASK_ZOOM_STEP),
        action(stringResource(R.string.editor_overlay_rotate), turn = MASK_TURN_STEP),
        action(stringResource(R.string.editor_overlay_rotate_back), turn = -MASK_TURN_STEP),
    )
}

/** Draws [mask]'s outline or edge lines and its center on [box], clipped to the picture. */
private fun DrawScope.drawMask(
    mask: ClipMask,
    box: PictureBox,
    color: Color,
) {
    val picture =
        Path().apply {
            val corners = listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f))
            corners.map(box::toScreen).forEachIndexed { index, corner ->
                if (index == 0) moveTo(corner.x, corner.y) else lineTo(corner.x, corner.y)
            }
            close()
        }
    val stroke = MASK_OUTLINE_WIDTH.toPx()
    clipPath(picture) {
        val outline = MaskGeometry.outlineOf(mask, box)
        if (outline.isNotEmpty()) {
            val path =
                Path().apply {
                    moveTo(outline.first().x, outline.first().y)
                    outline.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
            drawPath(path, color, style = Stroke(stroke))
        }
        MaskGeometry.linesOf(mask, box).forEach { (start, end) -> drawLine(color, start, end, stroke) }
    }
    drawCircle(color, MASK_CENTER_RADIUS.toPx(), MaskGeometry.centerOf(mask, box))
}

/*
 * Handles drawn over the preview for the selected overlay: drag to move, pinch to resize, and twist to rotate.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.framecraft.editor.domain.model.OverlayTransform

/** Test tag of the overlay handles layer. */
const val OVERLAY_LAYER_TAG = "editor_overlay_layer"

/** Share of the canvas width or height an accessibility move action shifts the overlay by. */
private const val NUDGE_FRACTION = 0.05f

/** Scale step of the accessibility resize actions. */
private const val SCALE_STEP = 0.1f

/** Degrees the accessibility rotate action turns the overlay by. */
private const val ROTATE_STEP_DEGREES = 15f

/** Degrees in a quarter turn, the rotations that rotation snapping lands on. */
private const val QUARTER_TURN = 90f

/** Width of the selection outline. */
private val OUTLINE_WIDTH = 2.dp

/** Radius of the corner and rotation handles. */
private val HANDLE_RADIUS = 6.dp

/** Distance of the rotation handle above the top edge. */
private val ROTATE_HANDLE_OFFSET = 24.dp

/**
 * Draws the selected overlay's outline at [transform], [boxSize] big on a canvas of a given size at a given scale.
 * One-finger drags move it and two-finger pinches and twists resize and rotate it anywhere on the canvas; the result
 * goes to [onCommit] once, when the last finger lifts. [onSnap] fires when rotation snaps to a quarter turn. When
 * [onDoubleTap] is set, a double tap calls it and TalkBack offers it as [doubleTapLabel].
 */
@Composable
fun OverlayTransformLayer(
    transform: OverlayTransform,
    boxSize: (canvas: Size, scale: Float) -> Size,
    onCommit: (OverlayTransform) -> Unit,
    onSnap: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.editor_overlay_box),
    onDoubleTap: (() -> Unit)? = null,
    doubleTapLabel: String = "",
) {
    var live by remember(transform) { mutableStateOf(transform) }
    var canvas by remember { mutableStateOf(Size.Zero) }
    val commit by rememberUpdatedState(onCommit)
    val snap by rememberUpdatedState(onSnap)
    val color = MaterialTheme.colorScheme.primary
    val moves =
        overlayActions(transform) { pan, zoom, turn ->
            if (canvas != Size.Zero) commit(OverlayGeometry.transformed(transform, canvas, pan(canvas), zoom, turn))
        }
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val edit =
        onDoubleTap?.let { tap ->
            CustomAccessibilityAction(doubleTapLabel) {
                tap()
                true
            }
        }
    val actions = listOfNotNull(edit) + moves
    Canvas(
        modifier =
            modifier
                .testTag(OVERLAY_LAYER_TAG)
                .onSizeChanged { canvas = it.toSize() }
                .overlayGestures(transform, onChange = { live = it }, onCommit = { commit(it) }, onSnap = { snap() })
                .doubleTaps(enabled = onDoubleTap != null) { doubleTap?.invoke() }
                .semantics {
                    contentDescription = description
                    customActions = actions
                },
    ) {
        drawOverlayBox(live, boxSize(size, live.scale), color)
    }
}

/** Calls [onDoubleTap] on each double tap while [enabled]. */
private fun Modifier.doubleTaps(
    enabled: Boolean,
    onDoubleTap: () -> Unit,
): Modifier =
    if (enabled) {
        pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
    } else {
        this
    }

/**
 * Turns every gesture on the canvas into a change of [transform]: [onChange] gets each step, with rotation snapped
 * to quarter turns, and [onCommit] the final placement when the last finger lifts, if it moved. [onSnap] fires each
 * time rotation lands on a quarter turn.
 */
private fun Modifier.overlayGestures(
    transform: OverlayTransform,
    onChange: (OverlayTransform) -> Unit,
    onCommit: (OverlayTransform) -> Unit,
    onSnap: () -> Unit,
): Modifier =
    pointerInput(transform) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var raw = transform
            var shown = transform
            do {
                val event = awaitPointerEvent()
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val turn = event.calculateRotation()
                if (pan != Offset.Zero || zoom != 1f || turn != 0f) {
                    raw = OverlayGeometry.transformed(raw, size.toSize(), pan, zoom, turn)
                    val wasSnapped = shown.rotationDegrees % QUARTER_TURN == 0f
                    shown = raw.copy(rotationDegrees = OverlayGeometry.snappedRotation(raw.rotationDegrees))
                    if (turn != 0f && !wasSnapped && shown.rotationDegrees % QUARTER_TURN == 0f) onSnap()
                    onChange(shown)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (shown != transform) onCommit(shown)
        }
    }

/**
 * Returns the TalkBack actions for an overlay at [transform]: move in four directions, resize, and rotate. Each calls
 * [apply] with a pan for a given canvas size, a zoom factor, and a clockwise turn.
 */
@Composable
private fun overlayActions(
    transform: OverlayTransform,
    apply: (pan: (Size) -> Offset, zoom: Float, turn: Float) -> Unit,
): List<CustomAccessibilityAction> {
    /** Returns an action labelled [label] that applies the given change. */
    fun action(
        label: String,
        pan: (Size) -> Offset = { Offset.Zero },
        zoom: Float = 1f,
        turn: Float = 0f,
    ): CustomAccessibilityAction =
        CustomAccessibilityAction(label) {
            apply(pan, zoom, turn)
            true
        }
    val bigger = (transform.scale + SCALE_STEP) / transform.scale
    val smaller = (transform.scale - SCALE_STEP).coerceAtLeast(OverlayGeometry.MIN_SCALE) / transform.scale
    return listOf(
        action(stringResource(R.string.editor_overlay_move_left), { Offset(-it.width * NUDGE_FRACTION, 0f) }),
        action(stringResource(R.string.editor_overlay_move_right), { Offset(it.width * NUDGE_FRACTION, 0f) }),
        action(stringResource(R.string.editor_overlay_move_up), { Offset(0f, -it.height * NUDGE_FRACTION) }),
        action(stringResource(R.string.editor_overlay_move_down), { Offset(0f, it.height * NUDGE_FRACTION) }),
        action(stringResource(R.string.editor_overlay_bigger), zoom = bigger),
        action(stringResource(R.string.editor_overlay_smaller), zoom = smaller),
        action(stringResource(R.string.editor_overlay_rotate), turn = ROTATE_STEP_DEGREES),
        action(stringResource(R.string.editor_overlay_rotate_back), turn = -ROTATE_STEP_DEGREES),
    )
}

/** Draws the [box]-sized outline of an overlay at [transform] with corner handles and a rotation handle on top. */
private fun DrawScope.drawOverlayBox(
    transform: OverlayTransform,
    box: Size,
    color: Color,
) {
    val center = OverlayGeometry.center(size, transform)
    val topLeft = center - Offset(box.width / 2f, box.height / 2f)
    val radius = HANDLE_RADIUS.toPx()
    rotate(transform.rotationDegrees, pivot = center) {
        drawRect(color, topLeft = topLeft, size = box, style = Stroke(OUTLINE_WIDTH.toPx()))
        listOf(
            topLeft,
            topLeft + Offset(box.width, 0f),
            topLeft + Offset(0f, box.height),
            topLeft + Offset(box.width, box.height),
        ).forEach { drawCircle(color, radius, it) }
        val topCenter = Offset(center.x, topLeft.y)
        val handle = topCenter - Offset(0f, ROTATE_HANDLE_OFFSET.toPx())
        drawLine(color, topCenter, handle, OUTLINE_WIDTH.toPx())
        drawCircle(color, radius, handle)
    }
}

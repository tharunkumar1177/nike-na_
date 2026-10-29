/*
 * The speed curve editor: the curve on a log speed scale across the clip's source, with points that drag vertically
 * and the playhead's place in the clip.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.edit.SpeedCurveEdit
import com.framecraft.editor.domain.edit.SpeedScale
import com.framecraft.editor.domain.model.CURVE_POSITION_SCALE
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.MAX_CURVE_SPEED_MILLI
import com.framecraft.editor.domain.model.MIN_SPEED_MILLI
import com.framecraft.editor.domain.model.NORMAL_SPEED_MILLI
import com.framecraft.editor.domain.model.SpeedPoint
import com.framecraft.editor.timeline.SpeedLabel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Test tag prefix of a curve point's handle; the point's index follows. */
const val SPEED_POINT_TAG_PREFIX = "speed_point_"

/** Size of a point's touch and focus target. */
private val POINT_TARGET = 48.dp

/** Radius of a drawn point. */
private val POINT_RADIUS = 6.dp

/** Width of the drawn curve. */
private val CURVE_WIDTH = 3.dp

/** Width of the playhead and normal-speed lines. */
private val GUIDE_WIDTH = 1.dp

/** Returns how far up the editor [speedMilli] sits, from 0 at the bottom to 1 at the top. */
internal fun curveHeightOf(speedMilli: Int): Float =
    SpeedScale.fractionOf(speedMilli, MIN_SPEED_MILLI, MAX_CURVE_SPEED_MILLI)

/** Returns the speed at [heightFraction] of the editor's height, from 0 at the bottom to 1 at the top. */
internal fun curveSpeedAt(heightFraction: Float): Int =
    SpeedScale.speedAt(heightFraction, MIN_SPEED_MILLI, MAX_CURVE_SPEED_MILLI)

/**
 * Returns the index of the point of [curve] closest across to [x] in an editor [width] pixels wide, or null when
 * none is within [reach] pixels.
 */
internal fun nearestPoint(
    curve: ClipSpeed.Curve,
    x: Float,
    width: Float,
    reach: Float,
): Int? {
    val distances = curve.points.map { abs(width * it.position / CURVE_POSITION_SCALE - x) }
    return distances.indices.minByOrNull { distances[it] }?.takeIf { distances[it] <= reach }
}

/**
 * Draws [curve] with the playhead at curve position [playhead], or none when null. Dragging near a point moves it up
 * or down: each step goes to [onDrag] and the result to [onCommit] when the finger lifts. Each point is also a
 * focusable handle whose actions nudge it by 0.1x through [onCommit].
 */
@Composable
internal fun SpeedCurveEditor(
    curve: ClipSpeed.Curve,
    playhead: Int?,
    onDrag: (ClipSpeed.Curve) -> Unit,
    onCommit: (ClipSpeed.Curve) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.editor_speed_curve_editor)
    val latest = rememberUpdatedState(curve)
    val drag = rememberUpdatedState(onDrag)
    val commit = rememberUpdatedState(onCommit)
    BoxWithConstraints(
        modifier =
            modifier
                .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
                .semantics { contentDescription = description }
                .pointerInput(Unit) { curveDrags(latest, drag, commit) },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val guide = GUIDE_WIDTH.toPx()
            val normalY = size.height * (1f - curveHeightOf(NORMAL_SPEED_MILLI))
            drawLine(colors.outline, Offset(0f, normalY), Offset(size.width, normalY), guide)
            if (playhead != null) {
                val playheadX = size.width * playhead / CURVE_POSITION_SCALE
                drawLine(colors.onSurfaceVariant, Offset(playheadX, 0f), Offset(playheadX, size.height), guide)
            }
            val points = curve.points.map { pointOffset(it, size.width, size.height) }
            val line = Path()
            line.moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { line.lineTo(it.x, it.y) }
            drawPath(line, colors.primary, style = Stroke(CURVE_WIDTH.toPx()))
            points.forEach { drawCircle(colors.primary, POINT_RADIUS.toPx(), it) }
        }
        curve.points.indices.forEach { index ->
            PointHandle(curve, index, pointOffset(curve.points[index], widthPx, heightPx), onCommit)
        }
    }
}

/** Returns where [point] sits in an editor [width] by [height] pixels. */
private fun pointOffset(
    point: SpeedPoint,
    width: Float,
    height: Float,
): Offset = Offset(width * point.position / CURVE_POSITION_SCALE, height * (1f - curveHeightOf(point.speedMilli)))

/**
 * Moves the point of [curve] nearest a drag's start, within half a touch target across, to the finger's height: each
 * step goes to [onDrag] and the last to [onCommit]. Drags that start away from every point do nothing.
 */
private suspend fun PointerInputScope.curveDrags(
    curve: State<ClipSpeed.Curve>,
    onDrag: State<(ClipSpeed.Curve) -> Unit>,
    onCommit: State<(ClipSpeed.Curve) -> Unit>,
) {
    var index: Int? = null
    var edited = curve.value
    detectDragGestures(
        onDragStart = { start ->
            edited = curve.value
            index = nearestPoint(edited, start.x, size.width.toFloat(), POINT_TARGET.toPx() / 2f)
        },
        onDragEnd = { if (index != null) onCommit.value(edited) },
        onDragCancel = { if (index != null) onCommit.value(edited) },
        onDrag = { change, _ ->
            val moving = index
            if (moving != null) {
                change.consume()
                val height = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                edited = SpeedCurveEdit.withSpeed(edited, moving, curveSpeedAt(height))
                onDrag.value(edited)
            }
        },
    )
}

/**
 * An invisible, focusable handle over point [index] of [curve] at [center]; its actions nudge it through [onCommit].
 */
@Composable
private fun PointHandle(
    curve: ClipSpeed.Curve,
    index: Int,
    center: Offset,
    onCommit: (ClipSpeed.Curve) -> Unit,
) {
    val point = curve.points[index]
    val description =
        stringResource(R.string.editor_speed_point, index + 1, curve.points.size, SpeedLabel.of(point.speedMilli))
    val up = stringResource(R.string.editor_speed_nudge_up)
    val down = stringResource(R.string.editor_speed_nudge_down)
    val half = with(LocalDensity.current) { POINT_TARGET.toPx() / 2f }
    Box(
        modifier =
            Modifier
                .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
                .size(POINT_TARGET)
                .testTag(SPEED_POINT_TAG_PREFIX + index)
                .semantics {
                    contentDescription = description
                    customActions =
                        listOf(
                            CustomAccessibilityAction(up) {
                                onCommit(SpeedCurveEdit.nudged(curve, index, faster = true))
                                true
                            },
                            CustomAccessibilityAction(down) {
                                onCommit(SpeedCurveEdit.nudged(curve, index, faster = false))
                                true
                            },
                        )
                },
    )
}

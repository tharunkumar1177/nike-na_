/*
 * The chroma key's eyedropper over the preview: a tap on the clip's picture picks the key color there.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.framecraft.editor.R

/** Test tag of the eyedropper layer. */
const val KEY_PICK_LAYER_TAG = "editor_key_pick_layer"

/** Width of the picture outline and the reticle. */
private val PICK_STROKE = 2.dp

/** Radius of the reticle drawn where the last tap landed. */
private val RETICLE_RADIUS = 14.dp

/** Dash and gap length of the picture outline. */
private val DASH = 8.dp

/**
 * Outlines the clip picture that [boxOf] places on a preview of a given size and sends the picture point of each tap
 * on it to [onPick], marking where it landed. TalkBack offers picking the picture's center.
 */
@Composable
internal fun KeyPickLayer(
    boxOf: (Size) -> PictureBox?,
    onPick: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tapped by remember { mutableStateOf<Offset?>(null) }
    val pick by rememberUpdatedState(onPick)
    val currentBoxOf by rememberUpdatedState(boxOf)
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.editor_chroma_pick_layer)
    val centerAction =
        CustomAccessibilityAction(stringResource(R.string.editor_chroma_pick_center)) {
            pick(Offset.Zero)
            true
        }
    val onTap: (Offset, Offset) -> Unit = { position, point ->
        tapped = position
        pick(point)
    }
    Canvas(
        modifier =
            modifier
                .testTag(KEY_PICK_LAYER_TAG)
                .pictureTaps({ currentBoxOf(it) }, onTap)
                .semantics {
                    contentDescription = description
                    customActions = listOf(centerAction)
                },
    ) {
        val box = boxOf(size) ?: return@Canvas
        val outline =
            Path().apply {
                val corners = listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f))
                corners.map(box::toScreen).forEachIndexed { index, corner ->
                    if (index == 0) moveTo(corner.x, corner.y) else lineTo(corner.x, corner.y)
                }
                close()
            }
        val dash = DASH.toPx()
        val dashed = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
        drawPath(outline, color, style = Stroke(PICK_STROKE.toPx(), pathEffect = dashed))
        tapped?.let { drawCircle(color, RETICLE_RADIUS.toPx(), it, style = Stroke(PICK_STROKE.toPx())) }
    }
}

/**
 * Calls [onTap] with the screen position and picture point of each tap that lands on the picture [boxOf] places on
 * this layer.
 */
private fun Modifier.pictureTaps(
    boxOf: (Size) -> PictureBox?,
    onTap: (Offset, Offset) -> Unit,
): Modifier =
    pointerInput(Unit) {
        detectTapGestures { position ->
            val box = boxOf(size.toSize())
            if (box != null && box.contains(position)) onTap(position, box.toPicture(position))
        }
    }

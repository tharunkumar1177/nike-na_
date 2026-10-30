/*
 * The mask panel: a picture clip's mask shape, inversion, feather, and corner rounding. It docks below the preview
 * so the mask handles stay usable, and every finished change is saved as one edit right away.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetClipMask
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MaskShape
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the mask panel. */
const val MASK_PANEL_TAG = "mask_panel"

/** Test tag prefix of the shape chips, followed by the shape's name in lower case or "none". */
const val MASK_SHAPE_TAG_PREFIX = "mask_shape_"

/** Test tag of the invert switch. */
const val MASK_INVERT_TAG = "mask_invert"

/** Test tag of the feather slider. */
const val MASK_FEATHER_TAG = "mask_feather"

/** Test tag of the corner rounding slider. */
const val MASK_CORNER_TAG = "mask_corner"

/** Which clip the mask panel shows; null while the panel is closed. */
internal class MaskEditing {
    /** Clip the panel shows, or null while it is closed. */
    var clipId by mutableStateOf<String?>(null)
        private set

    /** Opens the panel on clip [id]; a null id leaves it closed. */
    fun open(id: String?) {
        clipId = id
    }

    /** Closes the panel. */
    fun close() {
        clipId = null
    }
}

/** Returns the mask editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberMaskEditing(): MaskEditing = remember { MaskEditing() }

/**
 * Shows the mask panel for the clip [editing] has open in [project] while it is the [selectedClipId]; each change goes
 * to [dispatch] as one edit. The panel says when the playhead at [positionMicros] is off the clip, where its handles
 * cannot show, and closes when its clip is gone, unselected, or cannot take a mask.
 */
@Composable
internal fun MaskPanelHost(
    editing: MaskEditing,
    project: Project,
    selectedClipId: String?,
    positionMicros: Long,
    dispatch: (EditCommand) -> Unit,
) {
    val clipId = editing.clipId
    val clip = clipId?.takeIf { it == selectedClipId }?.let { pictureClipOf(project, it)?.first }
    if (clip != null) {
        MaskPanel(
            mask = clip.mask,
            onClip = positionMicros >= clip.startMicros && positionMicros < clip.endMicros,
            onChange = { next -> if (next != clip.mask) dispatch(SetClipMask(clip.id, next)) },
            onDone = editing::close,
        )
    } else if (clipId != null) {
        LaunchedEffect(clipId) { editing.close() }
    }
}

/**
 * Panel for a clip's [mask], none when null. A shape chip, the invert switch, or a finished slider drag goes to
 * [onChange] with the whole new mask; the hint says whether the playhead is [onClip], where the handles show.
 * [onDone] closes the panel.
 */
@Composable
internal fun MaskPanel(
    mask: ClipMask?,
    onClip: Boolean,
    onChange: (ClipMask?) -> Unit,
    onDone: () -> Unit,
) {
    DockedPanel(tag = MASK_PANEL_TAG, title = stringResource(R.string.editor_mask_title), onDone = onDone) {
        MaskShapeChips(mask?.shape) { shape -> if (shape != mask?.shape) onChange(MaskGeometry.switched(mask, shape)) }
        if (mask != null) {
            MaskSettings(mask, onChange)
            Text(
                text = stringResource(if (onClip) R.string.editor_mask_hint else R.string.editor_mask_outside),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One chip for no mask and one per shape, with [selected] chosen; a tap sends its shape to [onPick]. */
@Composable
private fun MaskShapeChips(
    selected: MaskShape?,
    onPick: (MaskShape?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        listOf<MaskShape?>(null).plus(MaskShape.entries).forEach { shape ->
            FilterChip(
                selected = shape == selected,
                onClick = { onPick(shape) },
                label = { Text(stringResource(shapeLabelOf(shape))) },
                modifier = Modifier.testTag(MASK_SHAPE_TAG_PREFIX + (shape?.name?.lowercase() ?: "none")),
            )
        }
    }
}

/** Invert, feather, and, for rectangles, corner rounding of [mask]; each finished change goes to [onChange]. */
@Composable
private fun MaskSettings(
    mask: ClipMask,
    onChange: (ClipMask) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.editor_mask_invert),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = mask.invert,
            onCheckedChange = { onChange(mask.copy(invert = it)) },
            modifier = Modifier.testTag(MASK_INVERT_TAG),
        )
    }
    PercentSlider(
        value = mask.featherPercent,
        label = stringResource(R.string.editor_mask_feather),
        valueRes = R.string.editor_mask_feather_value,
        onCommit = { onChange(mask.copy(featherPercent = it)) },
        tag = MASK_FEATHER_TAG,
    )
    if (mask.shape == MaskShape.RECTANGLE) {
        PercentSlider(
            value = mask.cornerPercent,
            label = stringResource(R.string.editor_mask_corner),
            valueRes = R.string.editor_mask_corner_value,
            onCommit = { onChange(mask.copy(cornerPercent = it)) },
            tag = MASK_CORNER_TAG,
        )
    }
}

/** Returns the label of the chip for [shape], or for no mask when null. */
@StringRes
private fun shapeLabelOf(shape: MaskShape?): Int =
    when (shape) {
        null -> R.string.editor_mask_none
        MaskShape.LINEAR -> R.string.editor_mask_linear
        MaskShape.MIRROR -> R.string.editor_mask_mirror
        MaskShape.CIRCLE -> R.string.editor_mask_circle
        MaskShape.RECTANGLE -> R.string.editor_mask_rectangle
        MaskShape.HEART -> R.string.editor_mask_heart
        MaskShape.STAR -> R.string.editor_mask_star
    }

/*
 * The chroma key panel: the key color, picked from the preview or a green or blue screen preset, and how strongly,
 * softly, and cleanly it keys. It docks below the preview so the eyedropper can work there, and every finished
 * change is saved as one edit right away.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetClipChromaKey
import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the chroma key panel. */
const val CHROMA_PANEL_TAG = "chroma_panel"

/** Test tag of the pick color chip. */
const val CHROMA_PICK_TAG = "chroma_pick"

/** Test tag of the green screen preset chip. */
const val CHROMA_GREEN_TAG = "chroma_green"

/** Test tag of the blue screen preset chip. */
const val CHROMA_BLUE_TAG = "chroma_blue"

/** Test tag of the strength slider. */
const val CHROMA_STRENGTH_TAG = "chroma_strength"

/** Test tag of the softness slider. */
const val CHROMA_SOFTNESS_TAG = "chroma_softness"

/** Test tag of the spill removal slider. */
const val CHROMA_SPILL_TAG = "chroma_spill"

/** Test tag of the remove button. */
const val CHROMA_REMOVE_TAG = "chroma_remove"

/** Test tag of the picking instruction. */
const val CHROMA_PICKING_TAG = "chroma_picking"

/** The key color of a typical green screen. */
const val GREEN_SCREEN_ARGB: Int = 0xFF00B140.toInt()

/** The key color of a typical blue screen. */
const val BLUE_SCREEN_ARGB: Int = 0xFF0047BB.toInt()

/** Size of the key color swatch. */
private val SWATCH_SIZE = 32.dp

/** Border width of the key color swatch. */
private val SWATCH_BORDER = 1.dp

/** Which clip the chroma key panel shows, and whether its eyedropper is waiting for a tap on the preview. */
internal class ChromaEditing {
    /** Clip the panel shows, or null while it is closed. */
    var clipId by mutableStateOf<String?>(null)
        private set

    /** True while a tap on the preview picks the key color. */
    var picking by mutableStateOf(false)

    /** True when the last pick could not read the frame. */
    var pickFailed by mutableStateOf(false)

    /** Opens the panel on clip [id], not picking; a null id leaves it closed. */
    fun open(id: String?) {
        clipId = id
        picking = false
        pickFailed = false
    }

    /** Closes the panel. */
    fun close() {
        open(null)
    }
}

/** Returns the chroma key editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberChromaEditing(): ChromaEditing = remember { ChromaEditing() }

/** Returns [key] keying [argb] instead, or a new key of [argb] when there was none. */
internal fun keyed(
    key: ChromaKey?,
    argb: Int,
): ChromaKey = key?.copy(keyColorArgb = argb) ?: ChromaKey(argb)

/**
 * Shows the chroma key panel for the clip [editing] has open in [project] while it is the [selectedClipId]; each
 * change goes to [dispatch] as one edit. The panel closes when its clip is gone, unselected, or cannot take a key.
 */
@Composable
internal fun ChromaPanelHost(
    editing: ChromaEditing,
    project: Project,
    selectedClipId: String?,
    dispatch: (EditCommand) -> Unit,
) {
    val clipId = editing.clipId
    val clip = clipId?.takeIf { it == selectedClipId }?.let { pictureClipOf(project, it)?.first }
    if (clip != null) {
        ChromaPanel(
            key = clip.chromaKey,
            picking = editing.picking,
            pickFailed = editing.pickFailed,
            onChange = { next -> if (next != clip.chromaKey) dispatch(SetClipChromaKey(clip.id, next)) },
            onPickToggle = {
                editing.picking = !editing.picking
                editing.pickFailed = false
            },
            onDone = editing::close,
        )
    } else if (clipId != null) {
        LaunchedEffect(clipId) { editing.close() }
    }
}

/**
 * Panel for a clip's chroma [key], none when null. A preset, finished slider drag, or remove goes to [onChange] with
 * the whole new key; the pick chip calls [onPickToggle] and shows [picking], with an instruction while waiting for a
 * tap and a warning when the last pick [pickFailed]. [onDone] closes the panel.
 */
@Composable
internal fun ChromaPanel(
    key: ChromaKey?,
    picking: Boolean,
    pickFailed: Boolean,
    onChange: (ChromaKey?) -> Unit,
    onPickToggle: () -> Unit,
    onDone: () -> Unit,
) {
    DockedPanel(tag = CHROMA_PANEL_TAG, title = stringResource(R.string.editor_chroma_title), onDone = onDone) {
        KeyColorRow(key, picking, onPickToggle) { onChange(keyed(key, it)) }
        if (picking || pickFailed) {
            val message = if (pickFailed) R.string.editor_chroma_pick_failed else R.string.editor_chroma_picking
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pickFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(CHROMA_PICKING_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (key != null) {
            KeySliders(key, onChange)
            TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag(CHROMA_REMOVE_TAG)) {
                Text(stringResource(R.string.editor_chroma_remove))
            }
        } else {
            Text(
                text = stringResource(R.string.editor_chroma_no_color),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The swatch of [key]'s color, the pick chip that shows [picking] and calls [onPickToggle], and the green and blue
 * screen presets, which send their color to [onPreset].
 */
@Composable
private fun KeyColorRow(
    key: ChromaKey?,
    picking: Boolean,
    onPickToggle: () -> Unit,
    onPreset: (Int) -> Unit,
) {
    val swatchLabel = stringResource(R.string.editor_chroma_color)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(SWATCH_SIZE)
                    .background(key?.let { Color(it.keyColorArgb) } ?: Color.Transparent, CircleShape)
                    .border(SWATCH_BORDER, MaterialTheme.colorScheme.outline, CircleShape)
                    .semantics { contentDescription = swatchLabel },
        )
        FilterChip(
            selected = picking,
            onClick = onPickToggle,
            label = { Text(stringResource(R.string.editor_chroma_pick)) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_colorize), null, Modifier.size(FilterChipDefaults.IconSize))
            },
            modifier = Modifier.testTag(CHROMA_PICK_TAG),
        )
        PresetChip(R.string.editor_chroma_green, GREEN_SCREEN_ARGB, key, CHROMA_GREEN_TAG, onPreset)
        PresetChip(R.string.editor_chroma_blue, BLUE_SCREEN_ARGB, key, CHROMA_BLUE_TAG, onPreset)
    }
}

/** A chip tagged [tag] labelled [labelRes] that sends preset [argb] to [onPreset], selected while [key] uses it. */
@Composable
private fun PresetChip(
    @StringRes labelRes: Int,
    argb: Int,
    key: ChromaKey?,
    tag: String,
    onPreset: (Int) -> Unit,
) {
    FilterChip(
        selected = key?.keyColorArgb == argb,
        onClick = { onPreset(argb) },
        label = { Text(stringResource(labelRes)) },
        leadingIcon = { Box(Modifier.size(FilterChipDefaults.IconSize).background(Color(argb), CircleShape)) },
        modifier = Modifier.testTag(tag),
    )
}

/** Strength, softness, and spill removal of [key]; each finished drag goes to [onChange]. */
@Composable
private fun KeySliders(
    key: ChromaKey,
    onChange: (ChromaKey?) -> Unit,
) {
    PercentSlider(
        value = key.tolerancePercent,
        label = stringResource(R.string.editor_chroma_strength),
        valueRes = R.string.editor_chroma_strength_value,
        onCommit = { onChange(key.copy(tolerancePercent = it)) },
        tag = CHROMA_STRENGTH_TAG,
    )
    PercentSlider(
        value = key.softnessPercent,
        label = stringResource(R.string.editor_chroma_softness),
        valueRes = R.string.editor_chroma_softness_value,
        onCommit = { onChange(key.copy(softnessPercent = it)) },
        tag = CHROMA_SOFTNESS_TAG,
    )
    PercentSlider(
        value = key.spillPercent,
        label = stringResource(R.string.editor_chroma_spill),
        valueRes = R.string.editor_chroma_spill_value,
        onCommit = { onChange(key.copy(spillPercent = it)) },
        tag = CHROMA_SPILL_TAG,
    )
}

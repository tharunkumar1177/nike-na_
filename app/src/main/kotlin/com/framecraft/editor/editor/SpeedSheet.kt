/*
 * The speed sheet: one constant speed or a speed curve for the selected video, and whether its sound keeps its pitch.
 * Every finished gesture is saved as one edit right away, so the sheet has no Done button.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetClipSpeed
import com.framecraft.editor.domain.edit.SpeedCurveEdit
import com.framecraft.editor.domain.edit.SpeedScale
import com.framecraft.editor.domain.model.CURVE_POSITION_SCALE
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.MAX_CONSTANT_SPEED_MILLI
import com.framecraft.editor.domain.model.MIN_SPEED_MILLI
import com.framecraft.editor.domain.model.NORMAL_SPEED_MILLI
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.model.SpeedPreset
import com.framecraft.editor.domain.model.speedMap
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.timeline.SpeedLabel
import com.framecraft.editor.timeline.TimelineGeometry
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the speed sheet. */
const val SPEED_SHEET_TAG = "speed_sheet"

/** Test tag of the constant speed tab. */
const val SPEED_NORMAL_TAB_TAG = "speed_normal_tab"

/** Test tag of the speed curve tab. */
const val SPEED_CURVE_TAB_TAG = "speed_curve_tab"

/** Test tag of the constant speed slider. */
const val SPEED_SLIDER_TAG = "speed_slider"

/** Test tag of the maintain pitch switch. */
const val SPEED_PITCH_TAG = "speed_pitch_toggle"

/** Test tag prefix of the curve chips; `custom` or the preset's name in lower case follows. */
const val SPEED_PRESET_TAG_PREFIX = "speed_preset_"

/** Test tag of the curve editor. */
const val SPEED_CURVE_EDITOR_TAG = "speed_curve_editor"

/** Test tag of the add or delete point button. */
const val SPEED_ADD_POINT_TAG = "speed_add_point"

/** Test tag of the reset curve button. */
const val SPEED_RESET_TAG = "speed_reset"

/** Height of the curve editor. */
private val CURVE_EDITOR_HEIGHT = 240.dp

/**
 * Shows the speed sheet for the clip [editing] has open in [project] with the playhead at [positionMicros]; each
 * change goes to [dispatch] as one edit. The sheet closes when its clip is gone.
 */
@Composable
internal fun SpeedSheetHost(
    editing: SpeedEditing,
    project: Project,
    positionMicros: Long,
    dispatch: (EditCommand) -> Unit,
) {
    val clipId = editing.clipId
    val clip = clipId?.let { id -> project.tracks.firstNotNullOfOrNull { track -> track.clips.find { it.id == id } } }
    if (clip != null) {
        SpeedSheet(
            clip = clip,
            frameRate = project.frameRate,
            positionMicros = positionMicros,
            onChange = { speed, pitch ->
                if (speed != clip.speed || pitch != clip.maintainPitch) dispatch(SetClipSpeed(clip.id, speed, pitch))
            },
            onDismiss = editing::close,
        )
    } else if (clipId != null) {
        LaunchedEffect(clipId) { editing.close() }
    }
}

/**
 * Bottom sheet for [clip]'s speed in a project at [frameRate] with the playhead at [positionMicros]. A finished
 * slider drag, chip tap, point drag, or button press goes to [onChange] with the new speed, null for normal, and the
 * pitch setting; [onDismiss] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedSheet(
    clip: Clip,
    frameRate: FrameRate,
    positionMicros: Long,
    onChange: (ClipSpeed?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var curveTab by rememberSaveable(clip.id) { mutableStateOf(clip.speed is ClipSpeed.Curve) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(SPEED_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.editor_speed_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PrimaryTabRow(
                selectedTabIndex = if (curveTab) 1 else 0,
                modifier = Modifier.padding(vertical = Spacing.md),
            ) {
                Tab(
                    selected = !curveTab,
                    onClick = { curveTab = false },
                    text = { Text(stringResource(R.string.editor_speed_tab_normal)) },
                    modifier = Modifier.testTag(SPEED_NORMAL_TAB_TAG),
                )
                Tab(
                    selected = curveTab,
                    onClick = { curveTab = true },
                    text = { Text(stringResource(R.string.editor_speed_tab_curve)) },
                    modifier = Modifier.testTag(SPEED_CURVE_TAB_TAG),
                )
            }
            val onSpeed: (ClipSpeed?) -> Unit = { onChange(it, clip.maintainPitch) }
            if (curveTab) {
                CurveSpeed(clip, frameRate, curvePositionAt(clip, frameRate, positionMicros), onSpeed)
            } else {
                NormalSpeed(clip, frameRate, onSpeed)
            }
            PitchSwitch(clip.maintainPitch) { onChange(clip.speed, it) }
        }
    }
}

/**
 * The constant speed of [clip] on a log slider from 0.1x to 100x that settles on 1x with a tick, with the duration
 * it gives in a project at [frameRate]; the finished value goes to [onCommit], null for 1x.
 */
@Composable
private fun NormalSpeed(
    clip: Clip,
    frameRate: FrameRate,
    onCommit: (ClipSpeed?) -> Unit,
) {
    val current = (clip.speed as? ClipSpeed.Constant)?.speedMilli ?: NORMAL_SPEED_MILLI
    var draft by remember(current) { mutableIntStateOf(current) }
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.editor_speed_slider)
    val shown = SpeedLabel.of(draft)
    Text(text = shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = SpeedScale.fractionOf(draft, MIN_SPEED_MILLI, MAX_CONSTANT_SPEED_MILLI),
        onValueChange = { fraction ->
            val next = SpeedScale.speedAt(fraction, MIN_SPEED_MILLI, MAX_CONSTANT_SPEED_MILLI)
            if (next == NORMAL_SPEED_MILLI && draft != NORMAL_SPEED_MILLI) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            draft = next
        },
        onValueChangeFinished = { onCommit(ClipSpeed.Constant(draft).takeUnless { draft == NORMAL_SPEED_MILLI }) },
        modifier =
            Modifier.testTag(SPEED_SLIDER_TAG).semantics {
                contentDescription = label
                stateDescription = shown
            },
    )
    DurationReadout(clip, ClipSpeed.Constant(draft).takeUnless { draft == NORMAL_SPEED_MILLI }, frameRate)
}

/**
 * The curve of [clip] in a project at [frameRate]: chips for a custom curve and each preset, the editor with the
 * playhead at curve position [playhead], the add or delete point and reset buttons, and the duration. Each finished
 * change goes to [onCommit].
 */
@Composable
private fun CurveSpeed(
    clip: Clip,
    frameRate: FrameRate,
    playhead: Int?,
    onCommit: (ClipSpeed?) -> Unit,
) {
    val stored = clip.speed as? ClipSpeed.Curve
    PresetChips(stored, onCommit)
    val curve = stored ?: SpeedPreset.customStart
    var draft by remember(curve) { mutableStateOf(curve) }
    SpeedCurveEditor(
        curve = draft,
        playhead = playhead,
        onDrag = { draft = it },
        onCommit = {
            draft = it
            onCommit(it)
        },
        modifier =
            Modifier
                .padding(vertical = Spacing.md)
                .fillMaxWidth()
                .height(CURVE_EDITOR_HEIGHT)
                .testTag(SPEED_CURVE_EDITOR_TAG),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val (deletes, edited) = pointEditAt(draft, playhead)
        TextButton(
            onClick = { edited?.let(onCommit) },
            enabled = edited != null,
            modifier = Modifier.testTag(SPEED_ADD_POINT_TAG),
        ) {
            Text(stringResource(if (deletes) R.string.editor_speed_delete_point else R.string.editor_speed_add_point))
        }
        TextButton(
            onClick = { onCommit(SpeedCurveEdit.flattened(draft)) },
            modifier = Modifier.testTag(SPEED_RESET_TAG),
        ) {
            Text(stringResource(R.string.editor_speed_reset))
        }
    }
    DurationReadout(clip, draft, frameRate)
}

/** A chip for a custom curve and one per preset, with the one matching [stored] chosen; picks go to [onPick]. */
@Composable
private fun PresetChips(
    stored: ClipSpeed.Curve?,
    onPick: (ClipSpeed?) -> Unit,
) {
    val preset = SpeedPreset.matching(stored)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = stored != null && preset == null,
            onClick = { if (stored == null || preset != null) onPick(SpeedPreset.customStart) },
            label = { Text(stringResource(R.string.editor_speed_preset_custom)) },
            modifier = Modifier.testTag(SPEED_PRESET_TAG_PREFIX + "custom"),
        )
        SpeedPreset.entries.forEach { entry ->
            FilterChip(
                selected = preset == entry,
                onClick = { if (preset != entry) onPick(entry.curve) },
                label = { Text(stringResource(presetLabelOf(entry))) },
                modifier = Modifier.testTag(SPEED_PRESET_TAG_PREFIX + entry.name.lowercase()),
            )
        }
    }
}

/** The clip's length at normal speed and at [speed] in a project at [frameRate], such as "Duration: 5.0s → 2.5s". */
@Composable
private fun DurationReadout(
    clip: Clip,
    speed: ClipSpeed?,
    frameRate: FrameRate,
) {
    val span = clip.sourceSpanMicros
    Text(
        text =
            stringResource(
                R.string.editor_speed_duration,
                TimelineGeometry.formatClipDuration(span),
                TimelineGeometry.formatClipDuration(SpeedMap.durationFor(speed, span, frameRate)),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/** The maintain pitch switch, on when [checked]; a toggle goes to [onToggle]. */
@Composable
private fun PitchSwitch(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(top = Spacing.md)
                .fillMaxWidth()
                .testTag(SPEED_PITCH_TAG)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
    ) {
        Text(
            text = stringResource(R.string.editor_speed_pitch),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Returns the curve position of [clip]'s source that plays at [positionMicros] in a project at [frameRate], or null
 * when the playhead is outside the clip.
 */
internal fun curvePositionAt(
    clip: Clip,
    frameRate: FrameRate,
    positionMicros: Long,
): Int? =
    (positionMicros - clip.startMicros).takeIf { it in 0 until clip.durationMicros }?.let { offset ->
        val source = clip.speedMap(frameRate)?.sourceAt(offset) ?: offset
        (source * CURVE_POSITION_SCALE / clip.sourceSpanMicros).toInt().coerceIn(0, CURVE_POSITION_SCALE)
    }

/**
 * Returns what the point button does to [curve] with the playhead at curve position [playhead]: whether it deletes,
 * because a point is near, and the edited curve, or null when the button is disabled.
 */
internal fun pointEditAt(
    curve: ClipSpeed.Curve,
    playhead: Int?,
): Pair<Boolean, ClipSpeed.Curve?> {
    val near = playhead?.let { SpeedCurveEdit.pointNear(curve, it) }
    val edited =
        when {
            near != null -> {
                if (SpeedCurveEdit.canDelete(curve, near)) SpeedCurveEdit.withoutPoint(curve, near) else null
            }

            playhead != null && SpeedCurveEdit.canAdd(curve, playhead) -> {
                SpeedCurveEdit.withPointAt(curve, playhead)
            }

            else -> {
                null
            }
        }
    return (near != null) to edited
}

/** Returns the label of [preset]. */
@StringRes
private fun presetLabelOf(preset: SpeedPreset): Int =
    when (preset) {
        SpeedPreset.MONTAGE -> R.string.editor_speed_preset_montage
        SpeedPreset.HERO -> R.string.editor_speed_preset_hero
        SpeedPreset.BULLET -> R.string.editor_speed_preset_bullet
        SpeedPreset.JUMP_CUT -> R.string.editor_speed_preset_jump_cut
        SpeedPreset.FLASH_IN -> R.string.editor_speed_preset_flash_in
        SpeedPreset.FLASH_OUT -> R.string.editor_speed_preset_flash_out
    }

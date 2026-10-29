/*
 * The transition sheet: which transition plays at the cut after a video clip, how long it lasts, and applying it to
 * every cut on the track. Every finished change is saved as one edit right away, so the sheet has no Done button.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.ApplyTransitionToAll
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetTransition
import com.framecraft.editor.domain.model.ClipTransition
import com.framecraft.editor.domain.model.DEFAULT_TRANSITION_MICROS
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionGeometry
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.timeline.TimelineGeometry
import com.framecraft.editor.timeline.transitionLabelOf
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToLong

/** Test tag of the transition sheet. */
const val TRANSITION_SHEET_TAG = "transition_sheet"

/** Test tag prefix of the transition chips; `none` or the kind's name in lower case follows. */
const val TRANSITION_CHIP_TAG_PREFIX = "transition_chip_"

/** Test tag of the duration slider. */
const val TRANSITION_SLIDER_TAG = "transition_slider"

/** Test tag of the apply to all button. */
const val TRANSITION_APPLY_ALL_TAG = "transition_apply_all"

/** Test tag of the note shown when a cut cannot take a transition. */
const val TRANSITION_ERROR_TAG = "transition_error_text"

/** Test tag of the note saying how many cuts apply to all reached. */
const val TRANSITION_APPLIED_TAG = "transition_applied_text"

/** How long before a transition the preview lands, so the change plays into view. */
private const val PREVIEW_LEAD_MICROS = 500_000L

/** Which clip's outgoing cut the transition sheet shows; null while the sheet is closed. */
internal class TransitionEditing {
    /** Clip before the cut the sheet shows, or null while it is closed. */
    var clipId by mutableStateOf<String?>(null)
        private set

    /** Opens the sheet on the cut after clip [id]. */
    fun open(id: String) {
        clipId = id
    }

    /** Closes the sheet. */
    fun close() {
        clipId = null
    }
}

/** Returns the transition editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberTransitionEditing(): TransitionEditing = remember { TransitionEditing() }

/**
 * Shows the transition sheet for the cut [editing] has open in [project]; each change goes to [dispatch] as one edit
 * and moves the preview through [onSeek] to just before the transition. The sheet closes when its cut is gone.
 */
@Composable
internal fun TransitionSheetHost(
    editing: TransitionEditing,
    project: Project,
    dispatch: (EditCommand) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val clipId = editing.clipId
    val track = clipId?.let { id -> project.tracks.find { track -> track.clips.any { it.id == id } } }
    val clip = track?.clips?.find { it.id == clipId }
    val next = if (track?.kind == TrackKind.VIDEO && clip != null) TransitionGeometry.nextOf(track, clip) else null
    if (track != null && clip != null && next != null) {
        val cap = TransitionGeometry.capMicros(project, clip, next)
        var applied by remember(clipId) { mutableStateOf<Int?>(null) }
        val stored = clip.outTransition
        val played = stored?.let { TransitionGeometry.clampedMicros(it.durationMicros, cap, project.frameRate) }
        LaunchedEffect(clipId, stored?.kind, played) {
            onSeek((clip.endMicros - (played ?: 0L) / 2 - PREVIEW_LEAD_MICROS).coerceAtLeast(0L))
        }
        TransitionSheet(
            transition = stored?.let { it.copy(durationMicros = played ?: it.durationMicros) },
            capMicros = cap,
            frameRate = project.frameRate,
            applied = applied,
            onChange = { if (it != stored) dispatch(SetTransition(clip.id, it)) },
            onApplyAll = {
                applied = ApplyTransitionToAll.valuesFor(project, track, it).size
                dispatch(ApplyTransitionToAll(track.id, it))
            },
            onDismiss = editing::close,
        )
    } else if (clipId != null) {
        LaunchedEffect(clipId) { editing.close() }
    }
}

/**
 * Bottom sheet for the cut holding [transition], or none, that allows up to [capMicros] in a project at [frameRate].
 * A chip tap or finished slider drag goes to [onChange], null for none; apply to all goes to [onApplyAll] and the
 * sheet then says how many cuts it [applied] to; [onDismiss] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransitionSheet(
    transition: ClipTransition?,
    capMicros: Long,
    frameRate: FrameRate,
    applied: Int?,
    onChange: (ClipTransition?) -> Unit,
    onApplyAll: (ClipTransition) -> Unit,
    onDismiss: () -> Unit,
) {
    val minimum = TransitionGeometry.minimumMicros(frameRate)
    val available = capMicros >= minimum
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(TRANSITION_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.editor_transition_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!available) {
                Text(
                    text = stringResource(R.string.editor_transition_error_handles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TRANSITION_ERROR_TAG),
                )
            }
            KindChips(transition?.kind, available) { kind ->
                onChange(kind?.let { ClipTransition(it, transition?.durationMicros ?: DEFAULT_TRANSITION_MICROS) })
            }
            if (transition != null && available) {
                DurationSlider(transition.durationMicros, minimum, capMicros, frameRate) {
                    onChange(transition.copy(durationMicros = it))
                }
                TextButton(
                    onClick = { onApplyAll(transition) },
                    modifier = Modifier.testTag(TRANSITION_APPLY_ALL_TAG),
                ) {
                    Text(stringResource(R.string.editor_transition_apply_all))
                }
            }
            applied?.let { AppliedNote(it) }
        }
    }
}

/** A chip for no transition and one per kind, with [current] chosen; kinds only pick while [available]. */
@Composable
private fun KindChips(
    current: TransitionKind?,
    available: Boolean,
    onPick: (TransitionKind?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = current == null,
            onClick = { if (current != null) onPick(null) },
            label = { Text(stringResource(R.string.editor_transition_none)) },
            modifier = Modifier.testTag(TRANSITION_CHIP_TAG_PREFIX + "none"),
        )
        TransitionKind.entries.forEach { kind ->
            FilterChip(
                selected = current == kind,
                onClick = { if (current != kind) onPick(kind) },
                label = { Text(stringResource(transitionLabelOf(kind))) },
                enabled = available,
                modifier = Modifier.testTag(TRANSITION_CHIP_TAG_PREFIX + kind.name.lowercase()),
            )
        }
    }
}

/**
 * The transition length [durationMicros] on a slider from [minimumMicros] to [capMicros] that steps by whole frames
 * of [frameRate] with a tick each; the finished value goes to [onCommit].
 */
@Composable
private fun DurationSlider(
    durationMicros: Long,
    minimumMicros: Long,
    capMicros: Long,
    frameRate: FrameRate,
    onCommit: (Long) -> Unit,
) {
    var draft by remember(durationMicros) { mutableLongStateOf(durationMicros) }
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.editor_transition_slider)
    val shown = stringResource(R.string.editor_transition_duration, TimelineGeometry.formatClipDuration(draft))
    Text(text = shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = draft.toFloat(),
        onValueChange = { value ->
            val next = frameRate.snapToNearestFrame(value.roundToLong()).coerceIn(minimumMicros, capMicros)
            if (next != draft) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            draft = next
        },
        onValueChangeFinished = { if (draft != durationMicros) onCommit(draft) },
        valueRange = minimumMicros.toFloat()..maxOf(capMicros, minimumMicros + 1).toFloat(),
        enabled = capMicros > minimumMicros,
        modifier =
            Modifier.testTag(TRANSITION_SLIDER_TAG).semantics {
                contentDescription = label
                stateDescription = shown
            },
    )
}

/** Says politely that apply to all reached [count] cuts. */
@Composable
private fun AppliedNote(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.editor_transition_applied_all, count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .testTag(TRANSITION_APPLIED_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

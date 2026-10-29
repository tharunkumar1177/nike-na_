/*
 * The mixer: one volume slider and mute toggle per lane that plays sound, each saved as one lane volume edit.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetTrackVolumes
import com.framecraft.editor.domain.command.TrackVolumeValue
import com.framecraft.editor.domain.model.MAX_VOLUME_PERCENT
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT
import com.framecraft.editor.domain.model.takesVolume
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToInt

/** Test tag of the mixer sheet's lane list. */
const val MIXER_SHEET_TAG = "mixer_sheet"

/** Returns the test tag of the volume slider of lane [trackId]. */
fun mixerSliderTag(trackId: String): String = "mixer_slider_$trackId"

/** Returns the test tag of the mute toggle of lane [trackId]. */
fun mixerMuteTag(trackId: String): String = "mixer_mute_$trackId"

/** Whether the mixer sheet is open. */
internal class MixerEditing {
    /** True while the sheet shows. */
    var isOpen by mutableStateOf(false)
        private set

    /** Opens the sheet. */
    fun open() {
        isOpen = true
    }

    /** Closes the sheet. */
    fun close() {
        isOpen = false
    }
}

/** Returns the mixer state, kept for as long as the editor shows. */
@Composable
internal fun rememberMixerEditing(): MixerEditing = remember { MixerEditing() }

/**
 * A lane in the mixer: lane [trackId], named by [nameRes] with [number] (zero for the unnumbered main video), at
 * [volumePercent].
 */
data class MixerLane(
    val trackId: String,
    @param:StringRes val nameRes: Int,
    val number: Int,
    val volumePercent: Int,
)

/**
 * Returns the lanes of [project] that play sound and hold clips, top to bottom: the main video, then overlays and
 * audio lanes numbered in order within their kind.
 */
internal fun mixerLanesOf(project: Project): List<MixerLane> {
    val counts = mutableMapOf<TrackKind, Int>()
    return project.tracks.mapNotNull { track ->
        val number = (counts[track.kind] ?: 0) + 1
        counts[track.kind] = number
        val name =
            when (track.kind) {
                TrackKind.VIDEO -> R.string.mixer_main_video
                TrackKind.OVERLAY -> R.string.mixer_overlay
                TrackKind.AUDIO -> R.string.mixer_audio
                TrackKind.TEXT -> null
            }
        name?.takeIf { track.kind.takesVolume && track.clips.isNotEmpty() }?.let {
            MixerLane(track.id, it, if (track.kind == TrackKind.VIDEO) 0 else number, track.volumePercent)
        }
    }
}

/** Shows the mixer for [project] while [editing] has it open; each finished change goes to [dispatch] as one edit. */
@Composable
internal fun MixerSheetHost(
    editing: MixerEditing,
    project: Project,
    dispatch: (EditCommand) -> Unit,
) {
    if (!editing.isOpen) return
    MixerSheet(
        lanes = mixerLanesOf(project),
        onCommit = { trackId, volume -> dispatch(SetTrackVolumes(listOf(TrackVolumeValue(trackId, volume)))) },
        onDismiss = editing::close,
    )
}

/** Bottom sheet with a row per lane in [lanes]; a finished slider drag or mute tap goes to [onCommit] once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MixerSheet(
    lanes: List<MixerLane>,
    onCommit: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .testTag(MIXER_SHEET_TAG)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.mixer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (lanes.isEmpty()) {
                Text(
                    text = stringResource(R.string.mixer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            lanes.forEach { lane -> MixerLaneRow(lane) { onCommit(lane.trackId, it) } }
        }
    }
}

/**
 * [lane]'s name and volume over a 0 to 200% slider, and a mute toggle that switches between silence and the last
 * audible volume; each finished change goes to [onCommit].
 */
@Composable
private fun MixerLaneRow(
    lane: MixerLane,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(lane.trackId, lane.volumePercent) { mutableIntStateOf(lane.volumePercent) }
    var lastAudible by remember(lane.trackId) {
        mutableIntStateOf(lane.volumePercent.takeIf { it > 0 } ?: UNITY_VOLUME_PERCENT)
    }
    val name = if (lane.number == 0) stringResource(lane.nameRes) else stringResource(lane.nameRes, lane.number)
    val label = stringResource(R.string.mixer_volume_label, name)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.mixer_lane_value, name, draft),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = draft.toFloat(),
                onValueChange = { draft = it.roundToInt() },
                onValueChangeFinished = {
                    if (draft > 0) lastAudible = draft
                    onCommit(draft)
                },
                valueRange = 0f..MAX_VOLUME_PERCENT.toFloat(),
                modifier = Modifier.testTag(mixerSliderTag(lane.trackId)).semantics { contentDescription = label },
            )
        }
        val muted = draft == 0
        IconButton(
            onClick = {
                if (!muted) lastAudible = draft
                draft = if (muted) lastAudible else 0
                onCommit(draft)
            },
            modifier = Modifier.testTag(mixerMuteTag(lane.trackId)),
        ) {
            val icon = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume
            val description = if (muted) R.string.mixer_unmute else R.string.mixer_mute
            Icon(painterResource(icon), stringResource(description, name))
        }
    }
}

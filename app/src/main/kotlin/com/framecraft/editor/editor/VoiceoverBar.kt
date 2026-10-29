/*
 * The voiceover recorder over the preview: the microphone permission, the record, countdown, and recording pill,
 * and stopping a take when the app leaves the screen.
 */
package com.framecraft.editor.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framecraft.editor.R
import com.framecraft.editor.domain.time.Timecode
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.ui.spokenDuration
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the recorder pill. */
const val VOICEOVER_BAR_TAG = "editor_voiceover_bar"

/** Test tag of the record button. */
const val VOICEOVER_RECORD_TAG = "editor_voiceover_record"

/** Test tag of the stop button. */
const val VOICEOVER_STOP_TAG = "editor_voiceover_stop"

/** Test tag of the close button. */
const val VOICEOVER_CLOSE_TAG = "editor_voiceover_close"

/** Red of the record button and the recording dot. */
private val RECORD_RED = Color(0xFFD32F2F)

/** Size of the recording dot. */
private val DOT_SIZE = 12.dp

/** Tonal elevation that lifts the pill off the picture. */
private val BAR_ELEVATION = 3.dp

/** Width of the input level meter. */
private val METER_WIDTH = 96.dp

/**
 * Opens [recording] once the microphone may be used, asking for it first; [showRationale] turns true when the
 * request was refused.
 */
internal class VoiceoverStarter(
    val recording: VoiceoverRecording,
) {
    /** True while the explanation of why the microphone is needed shows. */
    var showRationale by mutableStateOf(false)

    /** Opens the recorder, asking for the microphone first when needed. */
    var start: () -> Unit = {}
}

/** Returns the starter of [recording], wired to the system microphone permission request. */
@Composable
internal fun rememberVoiceoverStarter(recording: VoiceoverRecording): VoiceoverStarter {
    val context = LocalContext.current
    val starter = remember(recording) { VoiceoverStarter(recording) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recording.open() else starter.showRationale = true
        }
    starter.start = {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            recording.open()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return starter
}

/**
 * Keeps [starter]'s recorder in step with the screen: a take stops and is kept when the app leaves the screen, and
 * a refused microphone explains itself with a way to the app's settings.
 */
@Composable
internal fun VoiceoverHost(starter: VoiceoverStarter) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { starter.recording.interrupt() }
    if (!starter.showRationale) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { starter.showRationale = false },
        icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null) },
        title = { Text(stringResource(R.string.voiceover_permission_title)) },
        text = { Text(stringResource(R.string.voiceover_permission_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    starter.showRationale = false
                    val uri = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                },
            ) { Text(stringResource(R.string.voiceover_permission_settings)) }
        },
        dismissButton = {
            TextButton(onClick = { starter.showRationale = false }) {
                Text(stringResource(R.string.voiceover_permission_dismiss))
            }
        },
    )
}

/**
 * Returns the recorder layer for [starter] over [preview] of project [projectId] while the recorder shows, or null
 * when it is closed. Playback runs while a take records so the take lines up with the picture.
 */
@Composable
internal fun voiceoverLayer(
    starter: VoiceoverStarter,
    preview: ProjectPreview,
    projectId: String,
): (@Composable (Modifier) -> Unit)? {
    val recording = starter.recording
    val phase by recording.phase.collectAsStateWithLifecycle()
    if (phase == VoiceoverPhase.Closed) return null
    val rolling = phase is VoiceoverPhase.Recording
    LaunchedEffect(rolling) {
        if (rolling != preview.state.value.isPlaying) {
            if (rolling) preview.togglePlayPause() else preview.pause()
        }
    }
    BackHandler { if (phase == VoiceoverPhase.Armed) recording.close() else recording.stop() }
    val actions =
        VoiceoverBarActions(
            onRecord = { recording.record(projectId, preview.state.value.positionMicros) },
            onStop = recording::stop,
            onClose = recording::close,
        )
    return { layerModifier ->
        Box(layerModifier, contentAlignment = Alignment.BottomCenter) {
            VoiceoverBar(phase, actions, Modifier.padding(Spacing.md))
        }
    }
}

/** What the recorder pill does: [onRecord] starts the countdown, [onStop] ends a take, [onClose] hides it. */
class VoiceoverBarActions(
    val onRecord: () -> Unit,
    val onStop: () -> Unit,
    val onClose: () -> Unit,
)

/** The recorder pill for [phase]: record and close when armed, the countdown, or the running take with Stop. */
@Composable
fun VoiceoverBar(
    phase: VoiceoverPhase,
    actions: VoiceoverBarActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(VOICEOVER_BAR_TAG),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = BAR_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (phase) {
                VoiceoverPhase.Closed, VoiceoverPhase.Armed -> ArmedContent(actions)
                is VoiceoverPhase.Countdown -> CountdownContent(phase.secondsLeft, actions.onStop)
                is VoiceoverPhase.Recording -> RecordingContent(phase, actions.onStop)
                VoiceoverPhase.Saving -> SavingContent()
            }
        }
    }
}

/** Close, the headphones hint, and the record button. */
@Composable
private fun ArmedContent(actions: VoiceoverBarActions) {
    IconButton(onClick = actions.onClose, modifier = Modifier.testTag(VOICEOVER_CLOSE_TAG)) {
        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.voiceover_close))
    }
    Icon(painterResource(R.drawable.ic_headphones), contentDescription = null)
    Text(
        text = stringResource(R.string.voiceover_headphones_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    FilledIconButton(
        onClick = actions.onRecord,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = RECORD_RED),
        modifier = Modifier.testTag(VOICEOVER_RECORD_TAG),
    ) {
        Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voiceover_record))
    }
}

/** The seconds left before recording, announced as they change, and a button that cancels through [onCancel]. */
@Composable
private fun CountdownContent(
    secondsLeft: Int,
    onCancel: () -> Unit,
) {
    Text(
        text = secondsLeft.toString(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
    TextButton(onClick = onCancel, modifier = Modifier.testTag(VOICEOVER_STOP_TAG)) {
        Text(stringResource(R.string.voiceover_cancel))
    }
}

/** The recording dot, elapsed time, input level, and Stop, which goes to [onStop]. */
@Composable
private fun RecordingContent(
    phase: VoiceoverPhase.Recording,
    onStop: () -> Unit,
) {
    val elapsed = spokenDuration(LocalResources.current, phase.elapsedMicros)
    val spoken = stringResource(R.string.voiceover_elapsed, elapsed)
    Box(Modifier.size(DOT_SIZE).background(RECORD_RED, CircleShape))
    Text(
        text = Timecode.format(phase.elapsedMicros),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
    LinearProgressIndicator(progress = { phase.level }, modifier = Modifier.width(METER_WIDTH))
    FilledIconButton(onClick = onStop, modifier = Modifier.testTag(VOICEOVER_STOP_TAG)) {
        Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.voiceover_stop))
    }
}

/** Progress while the take is finished and added. */
@Composable
private fun SavingContent() {
    CircularProgressIndicator(Modifier.size(DOT_SIZE * 2))
    Text(
        text = stringResource(R.string.voiceover_saving),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Returns the message for [notice]. */
@StringRes
internal fun voiceoverNoticeText(notice: VoiceoverNotice): Int =
    when (notice) {
        VoiceoverNotice.TOO_SHORT -> R.string.voiceover_too_short
        VoiceoverNotice.STORAGE_FULL -> R.string.voiceover_storage_full
        VoiceoverNotice.MIC_FAILED -> R.string.voiceover_mic_failed
        VoiceoverNotice.NO_ROOM -> R.string.voiceover_no_room
        VoiceoverNotice.SAVED -> R.string.voiceover_saved
    }

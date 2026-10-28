/*
 * Editor screen: preview, playback bar, and the zones reserved for the timeline and tools.
 */
package com.framecraft.editor.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.framecraft.editor.R
import com.framecraft.editor.domain.time.Timecode
import com.framecraft.editor.engine.PreviewState
import com.framecraft.editor.ui.theme.Spacing

/** Share of the editor height given to the preview. */
private const val PREVIEW_WEIGHT = 0.45f

/** Share of the editor height reserved for the timeline. */
private const val TIMELINE_WEIGHT = 0.40f

/** Share of the editor height reserved for the tool bar. */
private const val TOOLS_WEIGHT = 0.15f

/**
 * Stateless editor screen. [previewSurface] renders the video into the modifier it receives;
 * back navigation asks for confirmation before [onDiscard].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectName: String,
    previewState: PreviewState,
    aspectRatio: Float,
    skippedCount: Int,
    onSkippedShown: () -> Unit,
    onPlayPause: () -> Unit,
    onDiscard: () -> Unit,
    previewSurface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    EditorNotices(snackbarHostState, skippedCount, previewState.errorCode, onSkippedShown)
    BackHandler { confirmDiscard = true }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = { confirmDiscard = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.editor_navigate_up),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PreviewArea(aspectRatio, previewSurface)
            PlaybackBar(previewState, onPlayPause)
            ReservedZones()
        }
    }
    if (confirmDiscard) {
        DiscardDialog(
            onCancel = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onDiscard()
            },
        )
    }
}

/** Shows snackbars for skipped files and playback errors. */
@Composable
private fun EditorNotices(
    snackbarHostState: SnackbarHostState,
    skippedCount: Int,
    errorCode: String?,
    onSkippedShown: () -> Unit,
) {
    val skippedMessage = stringResource(R.string.editor_skipped_files)
    val playbackMessage = stringResource(R.string.editor_playback_failed)
    LaunchedEffect(skippedCount) {
        if (skippedCount > 0) {
            snackbarHostState.showSnackbar(skippedMessage)
            onSkippedShown()
        }
    }
    LaunchedEffect(errorCode) {
        if (errorCode != null) {
            snackbarHostState.showSnackbar(playbackMessage)
        }
    }
}

/** Black stage that letterboxes the preview at [aspectRatio]. */
@Composable
private fun ColumnScope.PreviewArea(
    aspectRatio: Float,
    previewSurface: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(PREVIEW_WEIGHT)
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        previewSurface(Modifier.aspectRatio(aspectRatio))
    }
}

/** Play/pause button and the position over duration timecode. */
@Composable
private fun PlaybackBar(
    state: PreviewState,
    onPlayPause: () -> Unit,
) {
    val spokenProgress =
        stringResource(
            R.string.editor_time_of,
            spokenDuration(state.positionMicros),
            spokenDuration(state.durationMicros),
        )
    Row(
        modifier = Modifier.fillMaxWidth().height(Spacing.xxl).padding(end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val label = if (state.isPlaying) R.string.editor_pause else R.string.editor_play
        IconButton(onClick = onPlayPause) {
            Icon(painter = painterResource(icon), contentDescription = stringResource(label))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = Timecode.formatProgress(state.positionMicros, state.durationMicros),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { contentDescription = spokenProgress },
        )
    }
}

/** Zones kept free for the timeline and the tool bar. */
@Composable
private fun ColumnScope.ReservedZones() {
    Box(
        Modifier
            .fillMaxWidth()
            .weight(TIMELINE_WEIGHT)
            .background(MaterialTheme.colorScheme.surface),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .weight(TOOLS_WEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Confirms leaving the editor, which discards the unsaved project. */
@Composable
private fun DiscardDialog(
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.editor_discard_title)) },
        text = { Text(stringResource(R.string.editor_discard_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.editor_discard_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.editor_discard_cancel)) }
        },
    )
}

/** Spoken form of a duration, for example "1 minute 23 seconds". */
@Composable
private fun spokenDuration(micros: Long): String {
    val parts = Timecode.parts(micros)
    val hours = pluralStringResource(R.plurals.duration_hours, parts.hours.toInt(), parts.hours)
    val minutes = pluralStringResource(R.plurals.duration_minutes, parts.minutes.toInt(), parts.minutes)
    val seconds = pluralStringResource(R.plurals.duration_seconds, parts.seconds.toInt(), parts.seconds)
    return buildList {
        if (parts.hours > 0) add(hours)
        if (parts.minutes > 0) add(minutes)
        if (parts.seconds > 0 || isEmpty()) add(seconds)
    }.joinToString(" ")
}

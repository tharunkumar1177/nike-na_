/*
 * Connects the editor view model to the loading, failure, and editing screens.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.DeleteClip
import com.framecraft.editor.domain.command.SplitClip
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.engine.PreviewSurface
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.timeline.ScrubCallbacks
import com.framecraft.editor.timeline.TimelineStrip
import com.framecraft.editor.ui.theme.Spacing

/** Preview aspect ratio used when a project has no visual clip. */
private const val DEFAULT_ASPECT_RATIO = 16f / 9f

/** Shows the screen for the current non-idle [state]. */
@Composable
fun EditorRoute(
    viewModel: EditorViewModel,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        EditorState.Idle -> {
            Unit
        }

        EditorState.Loading -> {
            LoadingScreen(modifier)
        }

        EditorState.AllFailed -> {
            FailureScreen(
                titleRes = R.string.editor_import_failed_title,
                bodyRes = R.string.editor_import_failed_body,
                onGoBack = viewModel::close,
                modifier = modifier,
            )
        }

        is EditorState.OpenFailed -> {
            OpenFailedScreen(state.reason, viewModel::close, modifier)
        }

        is EditorState.Ready -> {
            ReadyEditor(viewModel, state, modifier)
        }
    }
}

/** Editor for an open project, wired to its preview player and saving session. */
@Composable
private fun ReadyEditor(
    viewModel: EditorViewModel,
    state: EditorState.Ready,
    modifier: Modifier = Modifier,
) {
    val preview = viewModel.preview ?: return
    val thumbnails = viewModel.thumbnails ?: return
    val previewState by preview.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val project = state.project
    val clips = project.tracks.flatMap { it.clips }
    val aspectRatio = clips.firstOrNull()?.let { project.assetOf(it).displayAspectRatio } ?: DEFAULT_ASPECT_RATIO
    val selected = clips.firstOrNull { it.id == state.selectedClipId }
    val position = previewState.positionMicros
    EditorScreen(
        projectName = project.name,
        previewState = previewState,
        aspectRatio = aspectRatio,
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        availability =
            ToolAvailability(
                hasSelection = selected != null,
                canSplit = selected != null && SplitClip.isSplittable(selected, position, project.frameRate),
            ),
        notices = EditorNotices(state.skippedCount, state.droppedEditCount, state.saveFailed),
        actions = EditorActions(viewModel::close, viewModel::undo, viewModel::redo, viewModel::acknowledge),
        playback = playbackActions(preview, project.frameRate, position),
        tools = toolActions(viewModel, selected, position),
        previewSurface = { surfaceModifier -> PreviewSurface(preview, surfaceModifier) },
        timeline = { timelineModifier ->
            TimelineStrip(
                project = project,
                positionMicros = { previewState.positionMicros },
                thumbnails = thumbnails,
                callbacks = ScrubCallbacks(preview::beginScrub, preview::seekTo, preview::endScrub),
                selectedClipId = state.selectedClipId,
                onClipSelected = viewModel::selectClip,
                onEdit = viewModel::dispatch,
                onHaptic = haptics::performHapticFeedback,
                modifier = timelineModifier,
            )
        },
        modifier = modifier,
    )
}

/** Play/pause and frame steps on [preview]; a step pauses and moves one [frameRate] frame from [positionMicros]. */
private fun playbackActions(
    preview: ProjectPreview,
    frameRate: FrameRate,
    positionMicros: Long,
): PlaybackActions {
    /** Pauses and moves the playhead by [frames] whole frames. */
    fun step(frames: Long) {
        preview.pause()
        preview.seekTo(frameRate.stepFrames(positionMicros, frames))
    }
    return PlaybackActions(
        onPlayPause = preview::togglePlayPause,
        onFrameBack = { step(-1L) },
        onFrameForward = { step(1L) },
    )
}

/** Split at [positionMicros] and delete for the [selected] clip; both do nothing without a selection. */
private fun toolActions(
    viewModel: EditorViewModel,
    selected: Clip?,
    positionMicros: Long,
): ToolActions =
    ToolActions(
        onSplitClip = {
            selected?.let { viewModel.dispatch(SplitClip(it.id, positionMicros, viewModel.idGenerator.next())) }
        },
        onDeleteClip = { selected?.let { viewModel.dispatch(DeleteClip(it.id)) } },
    )

/** Full-screen progress while picked media is probed or a project opens. */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.editor_preparing_media),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

/** Explains why a project could not be opened or created. */
@Composable
private fun OpenFailedScreen(
    reason: OpenFailure,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) =
        when (reason) {
            OpenFailure.NEWER_VERSION -> R.string.editor_newer_title to R.string.editor_newer_body
            OpenFailure.UNREADABLE -> R.string.editor_corrupt_title to R.string.editor_corrupt_body
            OpenFailure.STORAGE -> R.string.editor_storage_title to R.string.editor_storage_body
        }
    FailureScreen(titleRes = title, bodyRes = body, onGoBack = onGoBack, modifier = modifier)
}

/** A dialog with [titleRes] and [bodyRes] whose only action returns home. */
@Composable
fun FailureScreen(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AlertDialog(
            onDismissRequest = onGoBack,
            icon = { Icon(painter = painterResource(R.drawable.ic_error_outline), contentDescription = null) },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = onGoBack) { Text(stringResource(R.string.editor_import_failed_action)) }
            },
        )
    }
}

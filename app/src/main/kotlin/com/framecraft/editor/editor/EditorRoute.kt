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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.DeleteClip
import com.framecraft.editor.engine.PreviewSurface
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
    val project = state.project
    val firstClip = project.tracks.flatMap { it.clips }.firstOrNull()
    val aspectRatio = firstClip?.let { project.assetOf(it).displayAspectRatio } ?: DEFAULT_ASPECT_RATIO
    EditorScreen(
        projectName = project.name,
        previewState = previewState,
        aspectRatio = aspectRatio,
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        hasSelection = state.selectedClipId != null,
        notices = EditorNotices(state.skippedCount, state.droppedEditCount, state.saveFailed),
        actions =
            EditorActions(
                onPlayPause = preview::togglePlayPause,
                onClose = viewModel::close,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onDeleteClip = { state.selectedClipId?.let { viewModel.dispatch(DeleteClip(it)) } },
                onNoticeShown = viewModel::acknowledge,
            ),
        previewSurface = { surfaceModifier -> PreviewSurface(preview, surfaceModifier) },
        timeline = { timelineModifier ->
            TimelineStrip(
                project = project,
                positionMicros = { previewState.positionMicros },
                thumbnails = thumbnails,
                callbacks = ScrubCallbacks(preview::beginScrub, preview::seekTo, preview::endScrub),
                selectedClipId = state.selectedClipId,
                onClipSelected = viewModel::selectClip,
                modifier = timelineModifier,
            )
        },
        modifier = modifier,
    )
}

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

/*
 * Connects the editor view model to the loading, failure, and editing screens.
 */
package com.framecraft.editor.editor

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
import com.framecraft.editor.engine.PreviewSurface
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
        EditorState.Idle -> Unit
        EditorState.Loading -> LoadingScreen(modifier)
        EditorState.AllFailed -> ImportFailedScreen(onGoBack = viewModel::close, modifier = modifier)
        is EditorState.Ready -> ReadyEditor(viewModel, state, modifier)
    }
}

/** Editor for an open project, wired to its preview player. */
@Composable
private fun ReadyEditor(
    viewModel: EditorViewModel,
    state: EditorState.Ready,
    modifier: Modifier = Modifier,
) {
    val preview = viewModel.preview ?: return
    val previewState by preview.state.collectAsStateWithLifecycle()
    val project = state.project
    val firstClip = project.tracks.firstOrNull()?.clips?.firstOrNull()
    val aspectRatio = firstClip?.let { project.assetOf(it).displayAspectRatio } ?: DEFAULT_ASPECT_RATIO
    EditorScreen(
        projectName = project.name,
        previewState = previewState,
        aspectRatio = aspectRatio,
        skippedCount = state.skippedCount,
        onSkippedShown = viewModel::acknowledgeSkipped,
        onPlayPause = preview::togglePlayPause,
        onDiscard = viewModel::close,
        previewSurface = { surfaceModifier -> PreviewSurface(preview, surfaceModifier) },
        modifier = modifier,
    )
}

/** Full-screen progress while picked media is probed. */
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

/** Explains that none of the picked files could be read. */
@Composable
fun ImportFailedScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AlertDialog(
            onDismissRequest = onGoBack,
            icon = { Icon(painter = painterResource(R.drawable.ic_error_outline), contentDescription = null) },
            title = { Text(stringResource(R.string.editor_import_failed_title)) },
            text = { Text(stringResource(R.string.editor_import_failed_body)) },
            confirmButton = {
                TextButton(onClick = onGoBack) { Text(stringResource(R.string.editor_import_failed_action)) }
            },
        )
    }
}

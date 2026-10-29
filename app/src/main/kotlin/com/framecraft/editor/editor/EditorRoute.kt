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
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.engine.PreviewSurface
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.export.ExportViewModel
import com.framecraft.editor.timeline.ScrubCallbacks
import com.framecraft.editor.timeline.TimelineStrip
import com.framecraft.editor.ui.theme.Spacing

/** Shows the screen for the current non-idle [state]. */
@Composable
fun EditorRoute(
    viewModel: EditorViewModel,
    exportViewModel: ExportViewModel,
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
            ReadyEditor(viewModel, exportViewModel, state, modifier)
        }
    }
}

/** Editor for an open project, wired to its preview player and saving session. */
@Composable
private fun ReadyEditor(
    viewModel: EditorViewModel,
    exportViewModel: ExportViewModel,
    state: EditorState.Ready,
    modifier: Modifier = Modifier,
) {
    val preview = viewModel.preview ?: return
    val media = viewModel.media ?: return
    val previewState by preview.state.collectAsStateWithLifecycle()
    val overlayBusy by viewModel.overlays.busy.collectAsStateWithLifecycle()
    val audioBusy by viewModel.audio.busy.collectAsStateWithLifecycle()
    val voiceoverNotice by viewModel.voiceover.notice.collectAsStateWithLifecycle()
    val project = state.project
    val selectedTrack = project.tracks.firstOrNull { track -> track.clips.any { it.id == state.selectedClipId } }
    val selected = selectedTrack?.clips?.firstOrNull { it.id == state.selectedClipId }
    val kind = selectedTrack?.kind
    val importing = overlayBusy || audioBusy
    val position = previewState.positionMicros
    val export = rememberExportControls(exportViewModel, project)
    val pickers = rememberMediaPickers(viewModel, preview)
    val canvas = rememberCanvasEditing()
    EditorSheets(pickers, canvas, project, position, viewModel::dispatch, preview::seekTo)
    EditorScreen(
        projectName = project.name,
        previewState = previewState,
        aspectRatio = canvasAspectRatio(project),
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        availability =
            toolAvailability(project, selected, kind, position, importing)
                .copy(canvas = canvasToolState(project, selected, kind, importing, canvas)),
        notices =
            EditorNotices(
                state.skippedCount,
                state.droppedEditCount,
                state.saveFailed,
                state.editRejected,
                voiceoverNotice,
            ),
        actions = editorActions(viewModel, export.open),
        playback = playbackActions(preview, project.frameRate, position),
        tools =
            withCanvasActions(
                toolActions(viewModel, selected, kind, position, pickers),
                selected,
                canvas,
                viewModel::dispatch,
            ),
        previewSurface = { surfaceModifier -> PreviewSurface(preview, surfaceModifier) },
        timeline = timelineSlot(viewModel, state, { previewState.positionMicros }, media, pickers.transition),
        modifier = modifier,
        export = export.indicator,
        overlayLayer =
            voiceoverLayer(pickers.voiceover, preview, project.id)
                ?: selectionLayer(project, selected, kind, position, viewModel, canvas, pickers.text),
    )
}

/**
 * Returns the timeline of [state]'s project with the playhead at [positionMicros], drawing [media] and scrubbing
 * [viewModel]'s preview; edits go to [viewModel] and a tapped cut opens [transition].
 */
private fun timelineSlot(
    viewModel: EditorViewModel,
    state: EditorState.Ready,
    positionMicros: () -> Long,
    media: TimelineMedia,
    transition: TransitionEditing,
): @Composable (Modifier) -> Unit =
    { timelineModifier ->
        val haptics = LocalHapticFeedback.current
        val preview = viewModel.preview
        if (preview != null) {
            TimelineStrip(
                project = state.project,
                positionMicros = positionMicros,
                thumbnails = media.thumbnails,
                waveforms = media.waveforms,
                callbacks = ScrubCallbacks(preview::beginScrub, preview::seekTo, preview::endScrub),
                selectedClipId = state.selectedClipId,
                onClipSelected = viewModel::selectClip,
                onEdit = viewModel::dispatch,
                onHaptic = haptics::performHapticFeedback,
                modifier = timelineModifier,
                onCutSelected = transition::open,
            )
        }
    }

/**
 * Shows whichever of the text, canvas, speed, transition, look, and mixer sheets is open over [project] with the
 * playhead at [positionMicros], and keeps the voiceover recorder in step with the screen; edits go to [dispatch]
 * and the transition sheet moves the preview through [onSeek].
 */
@Composable
private fun EditorSheets(
    pickers: MediaPickers,
    canvas: CanvasEditing,
    project: Project,
    positionMicros: Long,
    dispatch: (EditCommand) -> Unit,
    onSeek: (Long) -> Unit,
) {
    TextEditHost(pickers.text, project)
    CanvasSheetHost(canvas, project, dispatch)
    SpeedSheetHost(pickers.speed, project, positionMicros, dispatch)
    TransitionSheetHost(pickers.transition, project, dispatch, onSeek)
    LookSheetHost(pickers.look, project, dispatch)
    MixerSheetHost(pickers.mixer, project, dispatch)
    VoiceoverHost(pickers.voiceover)
}

/**
 * Returns the preview handles for [selected] on a track of [kind] of [project] while the playhead at
 * [positionMicros] is inside it: the overlay or text handles, or a base clip's placement handles while [canvas] has
 * them showing. Edits go to [viewModel]; double-tapping text opens [text]. Null shows no handles.
 */
@Composable
private fun selectionLayer(
    project: Project,
    selected: Clip?,
    kind: TrackKind?,
    positionMicros: Long,
    viewModel: EditorViewModel,
    canvas: CanvasEditing,
    text: TextEditing,
): (@Composable (Modifier) -> Unit)? {
    val haptics = LocalHapticFeedback.current
    val clip = selected?.takeIf { positionMicros >= it.startMicros && positionMicros < it.endMicros } ?: return null
    return when {
        kind?.isPlaced == true -> {
            overlayLayer(clip, project, viewModel::dispatch, haptics) { text.open(clip.id, TextSheetTab.STYLE) }
        }

        kind == TrackKind.VIDEO && canvas.isAdjusting(clip.id) -> {
            baseLayer(clip, project, viewModel::dispatch, haptics)
        }

        else -> {
            null
        }
    }
}

/** Top bar and notice actions on [viewModel]; export opens the sheet through [onExport]. */
private fun editorActions(
    viewModel: EditorViewModel,
    onExport: () -> Unit,
): EditorActions =
    EditorActions(
        onClose = viewModel::close,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onExport = onExport,
        onNoticeShown = viewModel::acknowledge,
    )

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

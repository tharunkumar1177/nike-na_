/*
 * Editor screen: top bar with undo and redo, preview, playback bar, timeline, and the contextual tools zone.
 */
package com.framecraft.editor.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.time.Timecode
import com.framecraft.editor.engine.PreviewState
import com.framecraft.editor.ui.spokenDuration
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the undo action. */
const val UNDO_TAG = "editor_undo"

/** Test tag of the redo action. */
const val REDO_TAG = "editor_redo"

/** Test tag of the delete clip tool. */
const val TOOL_DELETE_TAG = "editor_tool_delete"

/** Test tag of the split clip tool. */
const val TOOL_SPLIT_TAG = "editor_tool_split"

/** Test tag of the previous frame action. */
const val FRAME_BACK_TAG = "editor_frame_back"

/** Test tag of the next frame action. */
const val FRAME_FORWARD_TAG = "editor_frame_forward"

/** Opacity of a disabled tool. */
private const val DISABLED_ALPHA = 0.38f

/** Share of the editor height given to the preview. */
private const val PREVIEW_WEIGHT = 0.45f

/** Share of the editor height reserved for the timeline. */
private const val TIMELINE_WEIGHT = 0.40f

/** Share of the editor height reserved for the tool bar. */
private const val TOOLS_WEIGHT = 0.15f

/** Size of a tool button in the tools zone. */
private val TOOL_SIZE = 64.dp

/** One-shot notices the editor shows as snackbars. */
enum class EditorNotice {
    /** Some picked files could not be read. */
    SKIPPED_FILES,

    /** Edits at the end of the journal were lost in a crash. */
    DROPPED_EDITS,

    /** An edit could not be saved. */
    SAVE_FAILED,
}

/** Notices waiting to be shown: [skippedCount] unreadable files, [droppedEditCount] lost edits, and [saveFailed]. */
data class EditorNotices(
    val skippedCount: Int = 0,
    val droppedEditCount: Int = 0,
    val saveFailed: Boolean = false,
)

/** What the editor's top bar and notices do. */
class EditorActions(
    val onClose: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onNoticeShown: (EditorNotice) -> Unit,
)

/** What the playback bar does. */
class PlaybackActions(
    val onPlayPause: () -> Unit,
    val onFrameBack: () -> Unit,
    val onFrameForward: () -> Unit,
)

/** What the selected-clip tools do. */
class ToolActions(
    val onSplitClip: () -> Unit,
    val onDeleteClip: () -> Unit,
)

/** Which tools apply: [hasSelection] when a clip is selected, [canSplit] when the playhead is inside it. */
data class ToolAvailability(
    val hasSelection: Boolean = false,
    val canSplit: Boolean = false,
)

/**
 * Stateless editor screen. [previewSurface] renders the video and [timeline] the timeline into the
 * modifiers they receive. Every edit is already saved, so back closes the editor without asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectName: String,
    previewState: PreviewState,
    aspectRatio: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    availability: ToolAvailability,
    notices: EditorNotices,
    actions: EditorActions,
    playback: PlaybackActions,
    tools: ToolActions,
    previewSurface: @Composable (Modifier) -> Unit,
    timeline: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorSnackbars(snackbarHostState, notices, previewState.errorCode, actions.onNoticeShown)
    BackHandler(onBack = actions.onClose)
    Scaffold(
        modifier = modifier,
        topBar = { EditorTopBar(projectName, canUndo, canRedo, actions) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PreviewArea(aspectRatio, previewSurface)
            PlaybackBar(previewState, playback)
            timeline(Modifier.fillMaxWidth().weight(TIMELINE_WEIGHT))
            ToolsZone(availability, tools)
        }
    }
}

/** Back on the start edge, the project name, and undo and redo on the end edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    actions: EditorActions,
) {
    TopAppBar(
        title = { Text(projectName) },
        navigationIcon = {
            IconButton(onClick = actions.onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.editor_navigate_up),
                )
            }
        },
        actions = {
            IconButton(onClick = actions.onUndo, enabled = canUndo, modifier = Modifier.testTag(UNDO_TAG)) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.editor_undo))
            }
            IconButton(onClick = actions.onRedo, enabled = canRedo, modifier = Modifier.testTag(REDO_TAG)) {
                Icon(painterResource(R.drawable.ic_redo), contentDescription = stringResource(R.string.editor_redo))
            }
        },
    )
}

/** Shows each pending notice once, then reports it shown; playback errors show whenever the code changes. */
@Composable
private fun EditorSnackbars(
    snackbarHostState: SnackbarHostState,
    notices: EditorNotices,
    errorCode: String?,
    onShown: (EditorNotice) -> Unit,
) {
    val resources = LocalResources.current
    LaunchedEffect(notices.skippedCount) {
        if (notices.skippedCount > 0) {
            snackbarHostState.showSnackbar(resources.getString(R.string.editor_skipped_files))
            onShown(EditorNotice.SKIPPED_FILES)
        }
    }
    LaunchedEffect(notices.droppedEditCount) {
        val count = notices.droppedEditCount
        if (count > 0) {
            val message = resources.getQuantityString(R.plurals.editor_recovered_edits, count, count)
            snackbarHostState.showSnackbar(message)
            onShown(EditorNotice.DROPPED_EDITS)
        }
    }
    LaunchedEffect(notices.saveFailed) {
        if (notices.saveFailed) {
            snackbarHostState.showSnackbar(resources.getString(R.string.editor_save_failed))
            onShown(EditorNotice.SAVE_FAILED)
        }
    }
    LaunchedEffect(errorCode) {
        if (errorCode != null) {
            snackbarHostState.showSnackbar(resources.getString(R.string.editor_playback_failed))
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

/** Previous frame, play/pause, and next frame buttons, then the position over duration timecode. */
@Composable
private fun PlaybackBar(
    state: PreviewState,
    playback: PlaybackActions,
) {
    val resources = LocalResources.current
    val spokenProgress =
        stringResource(
            R.string.editor_time_of,
            spokenDuration(resources, state.positionMicros),
            spokenDuration(resources, state.durationMicros),
        )
    Row(
        modifier = Modifier.fillMaxWidth().height(Spacing.xxl).padding(end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val label = if (state.isPlaying) R.string.editor_pause else R.string.editor_play
        IconButton(
            onClick = playback.onFrameBack,
            enabled = state.positionMicros > 0L,
            modifier = Modifier.testTag(FRAME_BACK_TAG),
        ) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.editor_previous_frame))
        }
        IconButton(onClick = playback.onPlayPause) {
            Icon(painter = painterResource(icon), contentDescription = stringResource(label))
        }
        IconButton(
            onClick = playback.onFrameForward,
            enabled = state.positionMicros < state.durationMicros,
            modifier = Modifier.testTag(FRAME_FORWARD_TAG),
        ) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.editor_next_frame))
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

/** Contextual tools for the selection, Split then Delete; empty when nothing is selected. */
@Composable
private fun ColumnScope.ToolsZone(
    availability: ToolAvailability,
    tools: ToolActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(TOOLS_WEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (availability.hasSelection) {
            ToolButton(
                iconRes = R.drawable.ic_content_cut,
                label = stringResource(R.string.editor_tool_split),
                description = stringResource(R.string.editor_tool_split_description),
                onClick = tools.onSplitClip,
                enabled = availability.canSplit,
                modifier = Modifier.testTag(TOOL_SPLIT_TAG),
            )
            ToolButton(
                iconRes = R.drawable.ic_delete,
                label = stringResource(R.string.editor_tool_delete),
                description = stringResource(R.string.editor_tool_delete_description),
                onClick = tools.onDeleteClip,
                modifier = Modifier.testTag(TOOL_DELETE_TAG),
            )
        }
    }
}

/** 64dp tool with an icon over a label, read by TalkBack as [description]; dimmed when not [enabled]. */
@Composable
private fun ToolButton(
    @DrawableRes iconRes: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .size(TOOL_SIZE)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = MaterialTheme.colorScheme.onSurface
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = color)
        Spacer(Modifier.height(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

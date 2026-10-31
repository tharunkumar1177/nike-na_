/*
 * Editor screen: top bar with undo and redo, preview, playback bar, timeline, and the contextual tools zone.
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

/** Test tag of the add overlay tool. */
const val TOOL_OVERLAY_TAG = "editor_tool_overlay"

/** Test tag of the selected overlay's opacity slider. */
const val TOOL_OPACITY_TAG = "editor_tool_opacity"

/** Test tag of the add text tool. */
const val TOOL_TEXT_TAG = "editor_tool_text"

/** Test tag of the edit text tool. */
const val TOOL_EDIT_TEXT_TAG = "editor_tool_edit_text"

/** Test tag of the animate text tool. */
const val TOOL_ANIMATE_TEXT_TAG = "editor_tool_animate_text"

/** Test tag of the canvas tool. */
const val TOOL_CANVAS_TAG = "editor_tool_canvas"

/** Test tag of the base clip fit and fill tool. */
const val TOOL_FIT_FILL_TAG = "editor_tool_fit_fill"

/** Test tag of the base clip placement tool. */
const val TOOL_ADJUST_TAG = "editor_tool_adjust"

/** Test tag of the speed tool. */
const val TOOL_SPEED_TAG = "editor_tool_speed"

/** Test tag of the look tool. */
const val TOOL_LOOK_TAG = "editor_tool_look"

/** Test tag of the mask tool. */
const val TOOL_MASK_TAG = "editor_tool_mask"

/** Test tag of the chroma key tool. */
const val TOOL_CHROMA_TAG = "editor_tool_chroma"

/** Test tag of the auto captions tool. */
const val TOOL_CAPTIONS_TAG = "editor_tool_captions"

/** Test tag of the previous frame action. */
const val FRAME_BACK_TAG = "editor_frame_back"

/** Test tag of the next frame action. */
const val FRAME_FORWARD_TAG = "editor_frame_forward"

/** Test tag of the export action in the top bar. */
const val EXPORT_TOP_ICON_TAG = "export_top_icon"

/** Size of the top bar's export progress ring. */
private val EXPORT_RING_SIZE = 24.dp

/** Stroke width of the top bar's export progress ring. */
private val EXPORT_RING_STROKE = 3.dp

/** Divides a percent into a 0 to 1 fraction. */
private const val PERCENT_SCALE = 100f

/** Share of the editor height given to the preview. */
private const val PREVIEW_WEIGHT = 0.45f

/** Share of the editor height reserved for the timeline. */
private const val TIMELINE_WEIGHT = 0.40f

/** Share of the editor height reserved for the tool bar. */
private const val TOOLS_WEIGHT = 0.15f

/** One-shot notices the editor shows as snackbars. */
enum class EditorNotice {
    /** Some picked files could not be read. */
    SKIPPED_FILES,

    /** Edits at the end of the journal were lost in a crash. */
    DROPPED_EDITS,

    /** An edit could not be saved. */
    SAVE_FAILED,

    /** An edit was refused because it would have broken the project. */
    EDIT_REJECTED,

    /** An undo or redo was refused because it no longer applies; history and project are unchanged. */
    HISTORY_REJECTED,

    /** A voiceover take finished with an outcome worth reporting. */
    VOICEOVER,
}

/**
 * Notices waiting to be shown: [skippedCount] unreadable files, [droppedEditCount] lost edits, [saveFailed],
 * [editRejected], [historyRejected], and the [voiceover] take outcome.
 */
data class EditorNotices(
    val skippedCount: Int = 0,
    val droppedEditCount: Int = 0,
    val saveFailed: Boolean = false,
    val editRejected: Boolean = false,
    val historyRejected: Boolean = false,
    val voiceover: VoiceoverNotice? = null,
)

/** What the editor's top bar and notices do. */
class EditorActions(
    val onClose: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onExport: () -> Unit,
    val onNoticeShown: (EditorNotice) -> Unit,
)

/** The top bar's export button: [percent] of a running export (null while preparing), shown when [exporting]. */
data class ExportIndicator(
    val exporting: Boolean = false,
    val percent: Int? = null,
)

/** What the playback bar does. */
class PlaybackActions(
    val onPlayPause: () -> Unit,
    val onFrameBack: () -> Unit,
    val onFrameForward: () -> Unit,
)

/**
 * What the tools do: split and delete the selection, add an overlay, text, or music, save the selected overlay's or
 * text's opacity, edit or animate the selected text, extract the selected clip's audio, save its volume and fades,
 * open the canvas sheet, toggle the selected base clip's fit and its placement handles, open the speed and look
 * sheets and the mask and chroma key panels, open the mixer, record a voiceover, and open the caption sheet.
 */
data class ToolActions(
    val onSplitClip: () -> Unit,
    val onDeleteClip: () -> Unit,
    val onAddOverlay: () -> Unit = {},
    val onOpacityCommit: (Float) -> Unit = {},
    val onAddAudio: () -> Unit = {},
    val onExtractAudio: () -> Unit = {},
    val onAudioCommit: (ClipAudio) -> Unit = {},
    val onAddText: () -> Unit = {},
    val onEditText: () -> Unit = {},
    val onAnimateText: () -> Unit = {},
    val onOpenCanvas: () -> Unit = {},
    val onToggleFit: () -> Unit = {},
    val onToggleAdjust: () -> Unit = {},
    val onOpenSpeed: () -> Unit = {},
    val onOpenLook: () -> Unit = {},
    val onOpenMask: () -> Unit = {},
    val onOpenChromaKey: () -> Unit = {},
    val onOpenMixer: () -> Unit = {},
    val onRecordVoiceover: () -> Unit = {},
    val onOpenCaptions: () -> Unit = {},
)

/**
 * The canvas tools: [show] when the canvas tool belongs in the row and [enabled] when it can open now, [isBase] when
 * the selection is a base clip, [fill] when that clip fills the canvas, and [adjusting] while its handles show.
 */
data class CanvasToolState(
    val show: Boolean = false,
    val enabled: Boolean = false,
    val isBase: Boolean = false,
    val fill: Boolean = false,
    val adjusting: Boolean = false,
)

/**
 * Which tools apply: [hasSelection] when clip [selectedClipId] is selected, [canSplit] when the playhead is inside
 * it, [canAddOverlay] when an overlay can be added now, [showAddOverlay] when the overlay tool belongs in the row,
 * [overlayOpacity] when the selection is an overlay or text, [showAddText] when the text tool belongs in the row and
 * [canAddText] when text can be added now, [isText] when the selection is text, [canAddAudio] when music can be added
 * now, [showExtract] when the selection is a video whose sound could be lifted and [canExtract] when that is possible
 * now, [showSpeed] when the selection is a video whose speed can change, [showLook] when the selection is a picture
 * that can take a look, mask, and chroma key, [maskActive] and [chromaKeyActive] when it has them, [audio] when the
 * selection plays sound, [canRecord] when a voiceover can start at the playhead, [canCaption] when the project has
 * sound to caption or captions to change, and the [canvas] tools.
 */
data class ToolAvailability(
    val hasSelection: Boolean = false,
    val canSplit: Boolean = false,
    val canAddOverlay: Boolean = false,
    val overlayOpacity: Float? = null,
    val selectedClipId: String? = null,
    val showAddOverlay: Boolean = true,
    val showAddText: Boolean = false,
    val canAddText: Boolean = false,
    val isText: Boolean = false,
    val canAddAudio: Boolean = false,
    val showExtract: Boolean = false,
    val canExtract: Boolean = false,
    val showSpeed: Boolean = false,
    val showLook: Boolean = false,
    val maskActive: Boolean = false,
    val chromaKeyActive: Boolean = false,
    val audio: ClipAudio? = null,
    val canRecord: Boolean = false,
    val canCaption: Boolean = false,
    val canvas: CanvasToolState = CanvasToolState(),
)

/**
 * Stateless editor screen. [previewSurface] renders the video, [overlayLayer] the selected overlay's handles on top
 * of it, and [timeline] the timeline into the modifiers they receive. Every edit is already saved, so back closes
 * the editor without asking.
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
    export: ExportIndicator = ExportIndicator(),
    overlayLayer: (@Composable (Modifier) -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorSnackbars(snackbarHostState, notices, previewState.errorCode, actions.onNoticeShown)
    BackHandler(onBack = actions.onClose)
    Scaffold(
        modifier = modifier,
        topBar = { EditorTopBar(projectName, canUndo, canRedo, export, actions) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PreviewArea(aspectRatio, previewSurface, overlayLayer)
            PlaybackBar(previewState, playback)
            timeline(Modifier.fillMaxWidth().weight(TIMELINE_WEIGHT))
            ToolsZone(availability, tools, Modifier.fillMaxWidth().weight(TOOLS_WEIGHT))
        }
    }
}

/** Back on the start edge, the project name, and undo, redo, and export on the end edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    export: ExportIndicator,
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
            ExportButton(export, actions.onExport)
        },
    )
}

/** Export action; while an export runs it shows the progress ring and reopens the export sheet. */
@Composable
private fun ExportButton(
    export: ExportIndicator,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(EXPORT_TOP_ICON_TAG)) {
        if (export.exporting) {
            val percent = export.percent
            val spoken = stringResource(R.string.export_progress_description, percent ?: 0)
            val ringModifier = Modifier.size(EXPORT_RING_SIZE).semantics { contentDescription = spoken }
            if (percent == null) {
                CircularProgressIndicator(ringModifier, strokeWidth = EXPORT_RING_STROKE)
            } else {
                CircularProgressIndicator(
                    progress = { percent / PERCENT_SCALE },
                    modifier = ringModifier,
                    strokeWidth = EXPORT_RING_STROKE,
                )
            }
        } else {
            Icon(painterResource(R.drawable.ic_export), stringResource(R.string.export_button_description))
        }
    }
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
    LaunchedEffect(notices.editRejected) {
        if (notices.editRejected) {
            snackbarHostState.showSnackbar(resources.getString(R.string.editor_edit_rejected))
            onShown(EditorNotice.EDIT_REJECTED)
        }
    }
    LaunchedEffect(notices.historyRejected) {
        if (notices.historyRejected) {
            snackbarHostState.showSnackbar(resources.getString(R.string.editor_history_rejected))
            onShown(EditorNotice.HISTORY_REJECTED)
        }
    }
    LaunchedEffect(notices.voiceover) {
        notices.voiceover?.let { notice ->
            snackbarHostState.showSnackbar(resources.getString(voiceoverNoticeText(notice)))
            onShown(EditorNotice.VOICEOVER)
        }
    }
    LaunchedEffect(errorCode) {
        if (errorCode != null) {
            snackbarHostState.showSnackbar(resources.getString(R.string.editor_playback_failed))
        }
    }
}

/** Black stage that letterboxes the preview at [aspectRatio], with [overlayLayer] over the same area. */
@Composable
private fun ColumnScope.PreviewArea(
    aspectRatio: Float,
    previewSurface: @Composable (Modifier) -> Unit,
    overlayLayer: (@Composable (Modifier) -> Unit)?,
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
        overlayLayer?.invoke(Modifier.aspectRatio(aspectRatio))
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

/*
 * Timeline strip: fixed center playhead, scrub, pinch zoom, tap selection, trim and reorder drags, and the snap,
 * zoom to playhead, and zoom to fit controls.
 */
package com.framecraft.editor.timeline

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import com.framecraft.editor.engine.ThumbnailSource
import com.framecraft.editor.engine.WaveformSource
import com.framecraft.editor.ui.spokenDuration
import com.framecraft.editor.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Test tag of the timeline canvas. */
const val TIMELINE_TAG = "timeline_canvas"

/** Test tag of the snap toggle. */
const val SNAP_TOGGLE_TAG = "editor_snap_toggle"

/** Test tag of the zoom to playhead toggle. */
const val ZOOM_PLAYHEAD_TAG = "editor_zoom_playhead"

/** How long a finished drag keeps its preview while waiting for the saved project. */
private const val SETTLE_TIMEOUT_MS = 1_000L

/** Callbacks a scrub gesture drives. */
class ScrubCallbacks(
    val onStart: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onEnd: () -> Unit,
)

/**
 * Draws [project] around a fixed center playhead at [positionMicros]. Touching pauses via
 * [ScrubCallbacks.onStart]; dragging seeks frame by frame; pinching zooms around the playhead. A tap
 * selects the clip under it, or clears the selection on empty space, through [onClipSelected]. Dragging a
 * handle of the selected clip trims it and holding a clip lifts it for reordering; each drag reports one
 * command to [onEdit] when the finger lifts. [onHaptic] plays snap and pickup feedback. Audio lanes show the loudness
 * of their sounds from [waveforms]. Tapping the badge on a video cut reports the clip before it to [onCutSelected].
 */
@Composable
fun TimelineStrip(
    project: Project,
    positionMicros: () -> Long,
    thumbnails: ThumbnailSource,
    waveforms: WaveformSource,
    callbacks: ScrubCallbacks,
    selectedClipId: String?,
    onClipSelected: (String?) -> Unit,
    onEdit: (EditCommand) -> Unit,
    onHaptic: (HapticFeedbackType) -> Unit,
    modifier: Modifier = Modifier,
    onCutSelected: (String) -> Unit = {},
) {
    val state = rememberTimelineState(project.id)
    val currentSelection by rememberUpdatedState(selectedClipId)
    val edits = rememberEditCallbacks(onClipSelected, onEdit, onHaptic, onCutSelected)
    val pending = state.preview
    LaunchedEffect(pending) {
        if (pending?.settling == true) {
            delay(SETTLE_TIMEOUT_MS)
            state.preview = null
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportWidthDp = maxWidth.value
        ThumbnailPrefetch(project, state, positionMicros, thumbnails, constraints.maxWidth.toFloat())
        LaunchedEffect(project, waveforms) { waveforms.request(audioWaveformUris(project)) }
        TimelineCanvas(
            project = project,
            preview = pending?.takeIf { it.base === project },
            state = state,
            positionMicros = positionMicros,
            sources = remember(thumbnails, waveforms) { TimelineSources(thumbnails, waveforms) },
            selectedClipId = selectedClipId,
            onSeek = callbacks.onSeek,
            onEdit = edits.onCommit,
            onCut = edits.onCut,
            modifier =
                Modifier.pointerInput(project) {
                    val context = TimelineGestureContext(state, project, edits) { currentSelection }
                    timelineGestures(context, positionMicros, callbacks)
                },
        )
        TimelineControls(
            state = state,
            onZoomToFit = { state.zoomTo(TimelineGeometry.fitZoom(project.durationMicros, viewportWidthDp)) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Remembers edit callbacks that always call the latest [onSelect], [onCommit], [onHaptic], and [onCut]. */
@Composable
private fun rememberEditCallbacks(
    onSelect: (String?) -> Unit,
    onCommit: (EditCommand) -> Unit,
    onHaptic: (HapticFeedbackType) -> Unit,
    onCut: (String) -> Unit,
): TimelineEditCallbacks {
    val select by rememberUpdatedState(onSelect)
    val commit by rememberUpdatedState(onCommit)
    val haptic by rememberUpdatedState(onHaptic)
    val cut by rememberUpdatedState(onCut)
    return remember { TimelineEditCallbacks({ select(it) }, { commit(it) }, { haptic(it) }, { cut(it) }) }
}

/**
 * The drawn timeline: [preview] replaces [project] while a drag is in progress. TalkBack can nudge a selected overlay
 * through [onEdit] and open the transition of each video cut through [onCut].
 */
@Composable
private fun TimelineCanvas(
    project: Project,
    preview: TimelinePreview?,
    state: TimelineState,
    positionMicros: () -> Long,
    sources: TimelineSources,
    selectedClipId: String?,
    onSeek: (Long) -> Unit,
    onEdit: (EditCommand) -> Unit,
    onCut: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = rememberTimelineStyle()
    val resources = LocalResources.current
    val thumbnailVersion by sources.thumbnails.version.collectAsState()
    val waveformVersion by sources.waveforms.version.collectAsState()
    val shown = preview?.project ?: project
    val nudges = overlayNudges(project, selectedClipId, onEdit) + cutActions(resources, project, onCut)
    val cuts = remember(shown) { TimelineCuts.of(shown) }
    Canvas(
        modifier
            .fillMaxSize()
            .testTag(TIMELINE_TAG)
            .timelineSemantics(resources, project, positionMicros, onSeek, nudges),
    ) {
        // Reading the versions here redraws the canvas whenever a thumbnail or waveform finishes loading.
        thumbnailVersion
        waveformVersion
        val frame =
            TimelineFrame(
                project = shown,
                centerMicros = if (state.scrubbing) state.centerMicros else positionMicros(),
                pxPerSecond = state.dpPerSecond.dp.toPx(),
                metrics = TimelineMetrics(this),
                style = style,
                sources = sources,
            )
        drawRuler(frame)
        shown.tracks.forEachIndexed { index, track ->
            drawTrack(frame, track, frame.metrics.rowTop(index), selectedClipId)
        }
        if (preview == null) drawCutBadges(frame, cuts)
        drawSelection(frame, selectedClipId)
        if (preview?.floating == null) drawTrimHandles(frame, selectedClipId)
        drawFloatingClip(frame, preview?.floating)
        drawSnapGuide(frame, preview?.snapMicros)
        drawPlayhead(frame)
    }
}

/** Remembers the colors, text measurer, and icons the timeline draws with. */
@Composable
private fun rememberTimelineStyle(): TimelineStyle {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val brokenImage = painterResource(R.drawable.ic_broken_image)
    val link = painterResource(R.drawable.ic_link)
    val linkOff = painterResource(R.drawable.ic_link_off)
    return remember(scheme, textMeasurer, labelStyle, brokenImage, link, linkOff) {
        TimelineStyle(
            colors =
                TimelineColors(
                    ruler = scheme.onSurfaceVariant,
                    clip = scheme.surfaceVariant,
                    clipLabel = scheme.onSurface,
                    playhead = scheme.primary,
                    selection = scheme.primary,
                ),
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            brokenImage = brokenImage,
            overlay = OverlayStyle(scheme.primaryContainer, link, linkOff, linkOffTint = scheme.error),
            audio = AudioStyle(clip = scheme.tertiaryContainer, wave = scheme.onTertiaryContainer),
            text = TextLaneStyle(clip = scheme.secondaryContainer, label = scheme.onSecondaryContainer),
        )
    }
}

/** Requests the thumbnails visible in a viewport [widthPx] wide whenever the view moves or zooms. */
@Composable
private fun ThumbnailPrefetch(
    project: Project,
    state: TimelineState,
    positionMicros: () -> Long,
    thumbnails: ThumbnailSource,
    widthPx: Float,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { TimelineRowHeight.toPx() }
    LaunchedEffect(project, thumbnails, widthPx, rowHeightPx) {
        val viewports =
            snapshotFlow {
                TimelineViewport(
                    centerMicros = if (state.scrubbing) state.centerMicros else positionMicros(),
                    pxPerSecond = with(density) { state.dpPerSecond.dp.toPx() },
                    widthPx = widthPx,
                    rowHeightPx = rowHeightPx,
                )
            }
        viewports.collect { viewport -> thumbnails.request(visibleThumbnailKeys(project, viewport)) }
    }
}

/** The snap toggle, zoom to playhead toggle, and zoom to fit button, in that order. */
@Composable
private fun TimelineControls(
    state: TimelineState,
    onZoomToFit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ControlToggle(
            checked = state.snapEnabled,
            onCheckedChange = { state.snapEnabled = it },
            iconRes = R.drawable.ic_snap,
            descriptionRes = R.string.timeline_snap,
            modifier = Modifier.testTag(SNAP_TOGGLE_TAG),
        )
        ControlToggle(
            checked = state.zoomBeforePlayhead != null,
            onCheckedChange = { state.toggleZoomToPlayhead() },
            iconRes = R.drawable.ic_zoom_in,
            descriptionRes = R.string.timeline_zoom_to_playhead,
            modifier = Modifier.testTag(ZOOM_PLAYHEAD_TAG),
        )
        FilledIconButton(
            onClick = onZoomToFit,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            modifier = Modifier.size(Spacing.xxl),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_zoom_out_map),
                contentDescription = stringResource(R.string.timeline_zoom_to_fit),
            )
        }
    }
}

/** 48dp on/off control: primary when [checked], muted otherwise; TalkBack reads its state. */
@Composable
private fun ControlToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes iconRes: Int,
    @StringRes descriptionRes: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors =
            IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = scheme.surfaceVariant,
                contentColor = scheme.onSurfaceVariant,
                checkedContainerColor = scheme.primaryContainer,
                checkedContentColor = scheme.primary,
            ),
        modifier = modifier.size(Spacing.xxl),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = stringResource(descriptionRes))
    }
}

/** Exposes the timeline to TalkBack as an adjustable position with a spoken summary and the [nudges] actions. */
private fun Modifier.timelineSemantics(
    resources: Resources,
    project: Project,
    positionMicros: () -> Long,
    onSeek: (Long) -> Unit,
    nudges: List<CustomAccessibilityAction>,
): Modifier =
    semantics {
        contentDescription = describeTimeline(resources, project, positionMicros())
        if (nudges.isNotEmpty()) customActions = nudges
        progressBarRangeInfo =
            ProgressBarRangeInfo(
                current = positionMicros().toFloat() / MICROS_PER_SECOND,
                range = 0f..(project.durationMicros.toFloat() / MICROS_PER_SECOND),
            )
        setProgress { seconds ->
            onSeek(project.frameRate.snapToNearestFrame((seconds * MICROS_PER_SECOND).toLong()))
            true
        }
    }

/** Builds the TalkBack description: playhead position, clip counts per track, and how to seek. */
private fun describeTimeline(
    resources: Resources,
    project: Project,
    positionMicros: Long,
): String {
    val tracks =
        project.tracks.mapIndexed { index, track ->
            resources.getQuantityString(R.plurals.timeline_track_clips, track.clips.size, track.clips.size, index + 1)
        }
    return resources.getString(
        R.string.timeline_description,
        spokenDuration(resources, positionMicros),
        tracks.joinToString(" "),
    )
}

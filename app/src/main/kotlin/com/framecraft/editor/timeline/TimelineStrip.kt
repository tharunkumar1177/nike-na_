/*
 * Read-only timeline strip: fixed center playhead, drag to scrub, pinch to zoom, and zoom to fit.
 */
package com.framecraft.editor.timeline

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import com.framecraft.editor.engine.ThumbnailSource
import com.framecraft.editor.ui.spokenDuration
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToLong

/** Test tag of the timeline canvas. */
const val TIMELINE_TAG = "timeline_canvas"

/** Callbacks a scrub gesture drives. */
class ScrubCallbacks(
    val onStart: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onEnd: () -> Unit,
)

/**
 * Draws [project] around a fixed center playhead at [positionMicros]. Touching pauses via
 * [ScrubCallbacks.onStart]; dragging seeks frame by frame; pinching zooms around the playhead.
 */
@Composable
fun TimelineStrip(
    project: Project,
    positionMicros: () -> Long,
    thumbnails: ThumbnailSource,
    callbacks: ScrubCallbacks,
    modifier: Modifier = Modifier,
) {
    val state = rememberTimelineState(project.id)
    val style = rememberTimelineStyle()
    val resources = LocalResources.current
    val thumbnailVersion by thumbnails.version.collectAsState()
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportWidthDp = maxWidth.value
        ThumbnailPrefetch(project, state, positionMicros, thumbnails, constraints.maxWidth.toFloat())
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(TIMELINE_TAG)
                .pointerInput(project) { scrubGestures(state, project, positionMicros, callbacks) }
                .timelineSemantics(resources, project, positionMicros, callbacks.onSeek),
        ) {
            // Reading the version here redraws the canvas whenever a thumbnail finishes loading.
            thumbnailVersion
            val frame =
                TimelineFrame(
                    project = project,
                    centerMicros = if (state.scrubbing) state.centerMicros else positionMicros(),
                    pxPerSecond = state.dpPerSecond.dp.toPx(),
                    metrics = TimelineMetrics(this),
                    style = style,
                    thumbnails = thumbnails,
                )
            drawRuler(frame)
            project.tracks.forEachIndexed { index, track -> drawTrack(frame, track, frame.metrics.rowTop(index)) }
            drawPlayhead(frame)
        }
        ZoomToFitButton(
            onClick = { state.dpPerSecond = TimelineGeometry.fitZoom(project.durationMicros, viewportWidthDp) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Remembers the colors, text measurer, and icons the timeline draws with. */
@Composable
private fun rememberTimelineStyle(): TimelineStyle {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val brokenImage = painterResource(R.drawable.ic_broken_image)
    return remember(scheme, textMeasurer, labelStyle, brokenImage) {
        TimelineStyle(
            colors =
                TimelineColors(
                    ruler = scheme.onSurfaceVariant,
                    clip = scheme.surfaceVariant,
                    clipLabel = scheme.onSurface,
                    playhead = scheme.primary,
                ),
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            brokenImage = brokenImage,
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

/** 48dp button that zooms so the whole project fits the viewport. */
@Composable
private fun ZoomToFitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        modifier = modifier.padding(Spacing.md).size(Spacing.xxl),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_zoom_out_map),
            contentDescription = stringResource(R.string.timeline_zoom_to_fit),
        )
    }
}

/** Exposes the timeline to TalkBack as an adjustable position with a spoken summary. */
private fun Modifier.timelineSemantics(
    resources: Resources,
    project: Project,
    positionMicros: () -> Long,
    onSeek: (Long) -> Unit,
): Modifier =
    semantics {
        contentDescription = describeTimeline(resources, project, positionMicros())
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

/** Handles one gesture at a time: one finger scrubs, two fingers zoom around the playhead. */
private suspend fun PointerInputScope.scrubGestures(
    state: TimelineState,
    project: Project,
    positionMicros: () -> Long,
    callbacks: ScrubCallbacks,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        state.beginScrub(positionMicros())
        callbacks.onStart()
        var lastSeek = state.centerMicros
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                state.dpPerSecond = TimelineGeometry.clampZoom(state.dpPerSecond * event.calculateZoom())
            } else {
                val pxPerSecond = state.dpPerSecond.dp.toPx()
                val delta = (-event.calculatePan().x / pxPerSecond * MICROS_PER_SECOND).roundToLong()
                state.centerMicros = (state.centerMicros + delta).coerceIn(0L, project.durationMicros)
                val snapped = project.frameRate.snapToNearestFrame(state.centerMicros)
                if (snapped != lastSeek) {
                    lastSeek = snapped
                    callbacks.onSeek(snapped)
                }
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })
        state.endScrub()
        callbacks.onEnd()
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

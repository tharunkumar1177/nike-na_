/*
 * Cut badges on video tracks: where each cut sits, whether it plays a transition, drawing, touch, and TalkBack.
 */
package com.framecraft.editor.timeline

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.TransitionGeometry
import com.framecraft.editor.domain.model.TransitionKind
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import com.framecraft.editor.ui.spokenDuration
import kotlin.math.abs
import kotlin.math.roundToLong

/** Opacity of a bare cut's outline. */
private const val BARE_OUTLINE_ALPHA = 0.8f

/** Opacity of a cut that cannot take a transition. */
private const val UNAVAILABLE_ALPHA = 0.38f

/** Share of the badge the transition glyph's squares take. */
private const val GLYPH_SHARE = 0.45f

/** What a cut's badge shows. */
enum class CutState {
    /** A plain cut that can take a transition. */
    NONE,

    /** A cut that plays a transition. */
    SET,

    /** A cut without the hidden source a transition needs, or too short for one. */
    UNAVAILABLE,
}

/** The cut on track row [trackIndex] from [left] into [right], badged as [state]. */
data class TimelineCut(
    val trackIndex: Int,
    val left: Clip,
    val right: Clip,
    val state: CutState,
)

/** Finds cuts and their badges. */
object TimelineCuts {
    /** Returns every cut between touching clips on the video tracks of [project], in row and time order. */
    fun of(project: Project): List<TimelineCut> {
        val playing =
            project.tracks
                .flatMap { TransitionGeometry.cutsOf(project, it) }
                .map { it.left.id }
                .toSet()
        val minimum = TransitionGeometry.minimumMicros(project.frameRate)
        return project.tracks.flatMapIndexed { index, track ->
            if (track.kind != TrackKind.VIDEO) {
                emptyList()
            } else {
                track.clips
                    .zipWithNext()
                    .filter { (left, right) -> left.endMicros == right.startMicros }
                    .map { (left, right) ->
                        val state =
                            when {
                                left.id in playing -> CutState.SET
                                TransitionGeometry.capMicros(project, left, right) < minimum -> CutState.UNAVAILABLE
                                else -> CutState.NONE
                            }
                        TimelineCut(index, left, right, state)
                    }
            }
        }
    }

    /** Returns the cut in [cuts] on row [row] nearest [timeMicros] and within [toleranceMicros] of it, or null. */
    fun near(
        cuts: List<TimelineCut>,
        row: Int,
        timeMicros: Long,
        toleranceMicros: Long,
    ): TimelineCut? =
        cuts
            .filter { it.trackIndex == row }
            .minByOrNull { abs(it.left.endMicros - timeMicros) }
            ?.takeIf { abs(it.left.endMicros - timeMicros) <= toleranceMicros }
}

/** Returns the label of transition [kind]. */
@StringRes
internal fun transitionLabelOf(kind: TransitionKind): Int =
    when (kind) {
        TransitionKind.DISSOLVE -> R.string.editor_transition_dissolve
        TransitionKind.FADE_BLACK -> R.string.editor_transition_fade_black
        TransitionKind.FADE_WHITE -> R.string.editor_transition_fade_white
        TransitionKind.SLIDE_LEFT -> R.string.editor_transition_slide_left
        TransitionKind.PUSH_LEFT -> R.string.editor_transition_push_left
        TransitionKind.ZOOM -> R.string.editor_transition_zoom
    }

/** Returns the cut whose badge touch zone holds [position] in [context], or null. */
internal fun AwaitPointerEventScope.cutAt(
    context: TimelineGestureContext,
    position: Offset,
): TimelineCut? {
    val metrics = TimelineMetrics(this)
    val row = TimelineGeometry.rowAt(position.y, metrics.rulerHeight, metrics.rowHeight, metrics.rowGap)
    val state = context.state
    val pxPerSecond = state.dpPerSecond.dp.toPx()
    val time = TimelineGeometry.xToTime(position.x, state.centerMicros, pxPerSecond, size.width.toFloat())
    val tolerance = (metrics.handleZone / 2 / pxPerSecond * MICROS_PER_SECOND).roundToLong()
    return row?.let { TimelineCuts.near(TimelineCuts.of(context.project), it, time, tolerance) }
}

/** Draws the badge of every cut in [cuts] centered on its cut in [frame]. */
internal fun DrawScope.drawCutBadges(
    frame: TimelineFrame,
    cuts: List<TimelineCut>,
) {
    val side = frame.metrics.badgeSize
    cuts.forEach { cut ->
        val centerX = x(frame, cut.left.endMicros)
        if (centerX >= -side && centerX <= size.width + side) {
            val center = Offset(centerX, frame.metrics.rowTop(cut.trackIndex) + frame.metrics.rowHeight / 2)
            drawCutBadge(frame, center, cut.state)
        }
    }
}

/** Draws one badge in [state] at [center]: a bar for a plain cut, overlapping squares for a transition. */
private fun DrawScope.drawCutBadge(
    frame: TimelineFrame,
    center: Offset,
    state: CutState,
) {
    val colors = frame.style.colors
    val side = frame.metrics.badgeSize
    val topLeft = Offset(center.x - side / 2, center.y - side / 2)
    val corner = CornerRadius(side / 4)
    val stroke = Stroke(width = frame.metrics.tickWidth * 2)
    when (state) {
        CutState.SET -> {
            drawRoundRect(colors.clipLabel, topLeft, Size(side, side), corner)
            val glyph = Size(side * GLYPH_SHARE, side * GLYPH_SHARE)
            val back = Offset(center.x - glyph.width * 3 / 4, center.y - glyph.height * 3 / 4)
            val front = Offset(center.x - glyph.width / 4, center.y - glyph.height / 4)
            drawRect(colors.clip, back, glyph)
            drawRect(colors.clip, front, glyph, style = stroke)
        }

        CutState.NONE, CutState.UNAVAILABLE -> {
            val alpha = if (state == CutState.NONE) BARE_OUTLINE_ALPHA else UNAVAILABLE_ALPHA
            drawRoundRect(colors.clip, topLeft, Size(side, side), corner)
            drawRoundRect(colors.clipLabel.copy(alpha = alpha), topLeft, Size(side, side), corner, style = stroke)
            val bar = Offset(center.x, center.y - side / 4)
            drawLine(colors.clipLabel.copy(alpha = alpha), bar, bar.copy(y = center.y + side / 4), stroke.width)
        }
    }
}

/** Returns a TalkBack action per cut of [project] that opens its transition through [onCut]. */
internal fun cutActions(
    resources: Resources,
    project: Project,
    onCut: (String) -> Unit,
): List<CustomAccessibilityAction> =
    TimelineCuts.of(project).map { cut ->
        val current =
            when (cut.state) {
                CutState.SET -> {
                    val transition = requireNotNull(cut.left.outTransition)
                    resources.getString(transitionLabelOf(transition.kind))
                }

                CutState.NONE -> {
                    resources.getString(R.string.editor_transition_none)
                }

                CutState.UNAVAILABLE -> {
                    resources.getString(R.string.editor_transition_unavailable)
                }
            }
        val at = spokenDuration(resources, cut.left.endMicros)
        CustomAccessibilityAction(resources.getString(R.string.timeline_cut_action, at, current)) {
            onCut(cut.left.id)
            true
        }
    }

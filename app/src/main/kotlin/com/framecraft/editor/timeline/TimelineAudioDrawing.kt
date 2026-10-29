/*
 * Canvas drawing for audio lanes: the sound's loudness as bars, a flat line while it loads, and fade ramps.
 */
package com.framecraft.editor.timeline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.speedMap
import com.framecraft.editor.domain.time.MICROS_PER_SECOND
import com.framecraft.editor.engine.ThumbnailSource
import com.framecraft.editor.engine.Waveform
import com.framecraft.editor.engine.WaveformSource
import kotlin.math.max
import kotlin.math.min

/** Opacity of waveform bars on a clip that is not selected. */
private const val WAVE_ALPHA = 0.5f

/** Opacity of the shading over a fade ramp. */
private const val FADE_ALPHA = 0.4f

/** Where the timeline's pictures and sounds come from. */
class TimelineSources(
    val thumbnails: ThumbnailSource,
    val waveforms: WaveformSource,
)

/** How audio clips look: their fill [clip] and their waveform color [wave]. */
class AudioStyle(
    val clip: Color,
    val wave: Color,
)

/**
 * Draws the inside of audio [clip] in [block]: its waveform when loaded, else a flat line, then the shading of its
 * fades. A [selected] clip shows its waveform at full strength.
 */
internal fun DrawScope.drawAudioClip(
    frame: TimelineFrame,
    clip: Clip,
    block: Rect,
    selected: Boolean,
) {
    val style = frame.style.audio
    drawRect(color = style.clip, topLeft = block.topLeft, size = block.size)
    val wave = style.wave.copy(alpha = if (selected) 1f else WAVE_ALPHA)
    val waveform = frame.sources.waveforms.get(frame.project.assetOf(clip).uri)
    if (waveform == null) {
        drawLine(wave, block.centerLeft, block.centerRight, frame.metrics.waveBarWidth)
    } else {
        drawWaveform(frame, clip, block, waveform, wave)
    }
    drawFades(frame, clip, block)
}

/** Draws one bar of [waveform] per bar slot of the visible part of [block], as tall as the loudness it covers. */
private fun DrawScope.drawWaveform(
    frame: TimelineFrame,
    clip: Clip,
    block: Rect,
    waveform: Waveform,
    color: Color,
) {
    val metrics = frame.metrics
    val barWidth = metrics.waveBarWidth
    val maxHalf = block.height / 2f - metrics.labelPadding
    val centerY = block.center.y
    var barX = max(block.left, 0f) + barWidth / 2f
    val end = min(block.right, size.width)
    val speedMap = clip.speedMap(frame.project.frameRate)
    while (barX < end) {
        val offsetMicros = ((barX - block.left) / frame.pxPerSecond * MICROS_PER_SECOND).toLong()
        val sourceMicros = clip.trimStartMicros + (speedMap?.sourceAt(offsetMicros) ?: offsetMicros)
        val half = max(waveform.levelAt(sourceMicros) * maxHalf, barWidth / 2f)
        drawLine(color, Offset(barX, centerY - half), Offset(barX, centerY + half), barWidth)
        barX += barWidth + metrics.waveBarGap
    }
}

/** Shades the part above each fade ramp of [clip], so the rest of [block] traces its volume rising and falling. */
private fun DrawScope.drawFades(
    frame: TimelineFrame,
    clip: Clip,
    block: Rect,
) {
    val shade = Color.Black.copy(alpha = FADE_ALPHA)
    if (clip.fadeInMicros > 0) {
        val rampEnd = block.left + clip.fadeInMicros * frame.pxPerSecond / MICROS_PER_SECOND
        drawPath(triangle(block.topLeft, Offset(rampEnd, block.top), block.bottomLeft), shade)
    }
    if (clip.fadeOutMicros > 0) {
        val rampStart = block.right - clip.fadeOutMicros * frame.pxPerSecond / MICROS_PER_SECOND
        drawPath(triangle(block.topRight, Offset(rampStart, block.top), block.bottomRight), shade)
    }
}

/** Returns a closed triangle through [a], [b], and [c]. */
private fun triangle(
    a: Offset,
    b: Offset,
    c: Offset,
): Path =
    Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }

/** Returns the source URIs of the clips on audio lanes, whose waveforms the timeline draws. */
fun audioWaveformUris(project: Project): List<String> =
    project.tracks
        .filter { it.kind == TrackKind.AUDIO }
        .flatMap { track -> track.clips.map { project.assetOf(it).uri } }
        .distinct()

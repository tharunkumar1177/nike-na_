/*
 * Pure math for the chroma key's eyedropper: which source frame lies under the playhead and which opaque color a tap
 * on a picture picks from it.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.geometry.Offset
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.SpeedMap
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.engine.FramePixels
import kotlin.math.roundToInt

/** Opaque alpha bits of an ARGB color. */
private const val OPAQUE = 0xFF shl 24

/** Bit offset of the red channel in an ARGB color. */
private const val RED_SHIFT = 16

/** Bit offset of the green channel in an ARGB color. */
private const val GREEN_SHIFT = 8

/** Mask of one 8-bit channel. */
private const val CHANNEL = 0xFF

/** Eyedropper math. */
object KeyColorMath {
    /** Pixels on each side of the tapped one that the picked color averages, so noise does not skew it. */
    const val SAMPLE_RADIUS: Int = 2

    /**
     * Returns the opaque average color of [frame] around picture point [point], where -1 and 1 are the frame's edges,
     * x right and y down. Points off the frame pick from its nearest edge.
     */
    fun colorAt(
        frame: FramePixels,
        point: Offset,
    ): Int {
        val column = ((point.x + 1f) / 2f * frame.width).toInt()
        val row = ((point.y + 1f) / 2f * frame.height).toInt()
        val samples =
            (-SAMPLE_RADIUS..SAMPLE_RADIUS).flatMap { dy ->
                (-SAMPLE_RADIUS..SAMPLE_RADIUS).map { dx -> frame.at(column + dx, row + dy) }
            }
        val red = samples.map { it shr RED_SHIFT and CHANNEL }.average().roundToInt()
        val green = samples.map { it shr GREEN_SHIFT and CHANNEL }.average().roundToInt()
        val blue = samples.map { it and CHANNEL }.average().roundToInt()
        return OPAQUE or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    /**
     * Returns the source time [clip] shows at timeline [positionMicros] in a project at [frameRate], following its
     * speed; a playhead off the clip reads its nearest frame.
     */
    fun sourceMicrosAt(
        clip: Clip,
        positionMicros: Long,
        frameRate: FrameRate,
    ): Long {
        val offset = (positionMicros - clip.startMicros).coerceIn(0L, clip.durationMicros - 1)
        val speed = clip.speed ?: return clip.trimStartMicros + offset
        return clip.trimStartMicros + SpeedMap.of(speed, clip.sourceSpanMicros, frameRate).sourceAt(offset)
    }
}

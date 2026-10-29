/*
 * Pure math for the preview's mask handles: where a mask's outline and edges fall on screen, and how gestures move,
 * resize, and turn it.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.geometry.Offset
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MAX_MASK_SIZE
import com.framecraft.editor.domain.model.MaskShape
import com.framecraft.editor.engine.MaskOutline
import kotlin.math.max

/** Converts between screen pixels and a mask on a clip's picture. */
object MaskGeometry {
    /** Smallest half-extent a gesture can shrink a mask to, so it stays visible and grabbable. */
    const val MIN_HALF_SIZE: Float = 0.05f

    /** Farthest a gesture can move a mask center from the picture center: the picture edge. */
    const val MAX_CENTER: Float = 1f

    /** How far each edge line of a linear or mirror mask runs from its middle, in picture heights per unit of width. */
    private const val LINE_REACH = 3f

    /** Returns the screen position of the point [x], [y] in [mask]'s upright frame on [box]. */
    fun toScreen(
        mask: ClipMask,
        box: PictureBox,
        x: Float,
        y: Float,
    ): Offset {
        val turnedPoint = turned(Offset(x, y), mask.rotationDegrees)
        return box.toScreen(Offset(mask.centerX + turnedPoint.x / box.aspect, mask.centerY + turnedPoint.y))
    }

    /** Returns the screen position of [mask]'s center on [box]. */
    fun centerOf(
        mask: ClipMask,
        box: PictureBox,
    ): Offset = box.toScreen(Offset(mask.centerX, mask.centerY))

    /** Returns the screen points around [mask]'s closed shape on [box]; edges and bands have none. */
    fun outlineOf(
        mask: ClipMask,
        box: PictureBox,
    ): List<Offset> = MaskOutline.closedOutlineOf(mask).map { toScreen(mask, box, it.x, it.y) }

    /** Returns the screen ends of each edge line of a linear or mirror [mask] on [box]; closed shapes have none. */
    fun linesOf(
        mask: ClipMask,
        box: PictureBox,
    ): List<Pair<Offset, Offset>> {
        val reach = LINE_REACH * max(box.aspect, 1f)
        val edges =
            when (mask.shape) {
                MaskShape.LINEAR -> listOf(0f)
                MaskShape.MIRROR -> listOf(-mask.halfWidth, mask.halfWidth)
                MaskShape.CIRCLE, MaskShape.RECTANGLE, MaskShape.HEART, MaskShape.STAR -> emptyList()
            }
        return edges.map { x -> toScreen(mask, box, x, -reach) to toScreen(mask, box, x, reach) }
    }

    /**
     * Returns [mask] moved by [pan] screen pixels, scaled by [zoom], and turned clockwise by [turnDegrees] on [box].
     * The center stays on the picture and each half-extent between [MIN_HALF_SIZE] and the maximum.
     */
    fun transformed(
        mask: ClipMask,
        box: PictureBox,
        pan: Offset,
        zoom: Float,
        turnDegrees: Float,
    ): ClipMask {
        val delta = box.toPictureDelta(pan)
        return mask.copy(
            centerX = (mask.centerX + delta.x).coerceIn(-MAX_CENTER, MAX_CENTER),
            centerY = (mask.centerY + delta.y).coerceIn(-MAX_CENTER, MAX_CENTER),
            halfWidth = (mask.halfWidth * zoom).coerceIn(MIN_HALF_SIZE, MAX_MASK_SIZE),
            halfHeight = (mask.halfHeight * zoom).coerceIn(MIN_HALF_SIZE, MAX_MASK_SIZE),
            rotationDegrees = OverlayGeometry.normalized(mask.rotationDegrees + turnDegrees),
        )
    }

    /**
     * Returns the mask a newly chosen [shape] starts as when switching from [current]: the shape's own size, feather,
     * and rounding at the current center, turn, and inversion. A null shape removes the mask.
     */
    fun switched(
        current: ClipMask?,
        shape: MaskShape?,
    ): ClipMask? {
        if (shape == null) return null
        val fresh = ClipMask.defaultFor(shape)
        return current?.let {
            fresh.copy(
                centerX = it.centerX,
                centerY = it.centerY,
                rotationDegrees = it.rotationDegrees,
                invert = it.invert,
            )
        } ?: fresh
    }
}

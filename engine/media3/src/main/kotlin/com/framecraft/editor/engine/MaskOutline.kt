/*
 * The outline of a mask shape, traced from the same distance math the mask shader uses, so handles drawn on the
 * preview match the cut the picture shows.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MaskShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Points traced around a closed shape. */
private const val OUTLINE_POINTS = 72

/** Steps of the outward search along each ray before the edge is refined. */
private const val SEARCH_STEPS = 64

/** Halvings that refine each edge point. */
private const val REFINEMENTS = 12

/** A point in a mask's upright frame: picture half-heights from its center, x right and y down. */
data class MaskPoint(
    val x: Float,
    val y: Float,
)

/** Traces mask outlines. */
object MaskOutline {
    /**
     * Returns points around the edge of [mask]'s closed shape in its upright frame, in order around the center. A
     * linear edge or mirror band has no closed shape and returns none.
     */
    fun closedOutlineOf(mask: ClipMask): List<MaskPoint> {
        if (mask.shape == MaskShape.LINEAR || mask.shape == MaskShape.MIRROR) return emptyList()
        val reach = 2f * max(mask.halfWidth, mask.halfHeight)
        return (0 until OUTLINE_POINTS).map { index ->
            val angle = 2.0 * PI * index / OUTLINE_POINTS
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val radius = edgeAlong(mask, dx, dy, reach)
            MaskPoint(dx * radius, dy * radius)
        }
    }

    /** Returns the signed distance of the point [x], [y] in [mask]'s upright frame from its edge; negative inside. */
    fun distanceOf(
        mask: ClipMask,
        x: Float,
        y: Float,
    ): Float = MaskMath.distanceOf(mask, x, y)

    /** Returns how far from the center the ray toward [dx], [dy] leaves [mask], searching up to [reach]. */
    private fun edgeAlong(
        mask: ClipMask,
        dx: Float,
        dy: Float,
        reach: Float,
    ): Float {
        val step = reach / SEARCH_STEPS
        var inside = 0f
        var outside = step
        while (outside < reach && MaskMath.distanceOf(mask, dx * outside, dy * outside) < 0f) {
            inside = outside
            outside += step
        }
        repeat(REFINEMENTS) {
            val middle = (inside + outside) / 2f
            if (MaskMath.distanceOf(mask, dx * middle, dy * middle) < 0f) inside = middle else outside = middle
        }
        return (inside + outside) / 2f
    }
}

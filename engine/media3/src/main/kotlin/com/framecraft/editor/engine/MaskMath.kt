/*
 * The math of shape masks, shared by the mask shader's uniforms and a CPU mirror of the shader that tests and the
 * editor's handles rely on.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MAX_MASK_PERCENT
import com.framecraft.editor.domain.model.MaskShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Feather width at 100 percent, in picture half-heights. */
internal const val MAX_FEATHER_WIDTH = 0.25f

/** Narrowest edge, in picture half-heights, so a hard edge still smooths over about one pixel. */
internal const val MIN_FEATHER_WIDTH = 0.002f

/** Half-width of the heart's natural shape, which the mask stretches to its half-width. */
internal const val HEART_HALF_WIDTH = 0.6036f

/** Half-height of the heart's natural shape, which the mask stretches to its half-height. */
internal const val HEART_HALF_HEIGHT = 0.5518f

/** How far the star's inner corners sit from its center, relative to its points. */
internal const val STAR_INNER_RATIO = 0.5f

/** Radius of the round lobes of the heart's natural shape. */
private const val HEART_LOBE_RADIUS = 0.35355339f

/** Center of the right lobe of the heart's natural shape. */
private const val HEART_LOBE_X = 0.25f

/** Height of the lobe centers of the heart's natural shape. */
private const val HEART_LOBE_Y = 0.75f

/** Cosine of the star's 36 degree half-angle between points. */
private const val STAR_K1_X = 0.809017f

/** Negated sine of the star's 36 degree half-angle between points. */
private const val STAR_K1_Y = -0.58778524f

/** Where the heart's fold line meets its diagonal, as a share of the distance along it. */
private const val HEART_FOLD = 0.5f

/** The constant term of the smoothstep polynomial. */
private const val SMOOTH_STEP_RISE = 3f

/** The mask shader's values for one mask, in the order the shader takes them. */
internal class MaskUniforms(
    val shapeIndex: Int,
    val center: FloatArray,
    val halfSize: FloatArray,
    val turn: FloatArray,
    val featherWidth: Float,
    val cornerFraction: Float,
    val invert: Float,
)

/** Mask math. */
internal object MaskMath {
    /** Returns the uniforms that draw [mask]. */
    fun uniformsOf(mask: ClipMask): MaskUniforms {
        val radians = Math.toRadians(mask.rotationDegrees.toDouble())
        return MaskUniforms(
            shapeIndex = mask.shape.ordinal,
            center = floatArrayOf(mask.centerX, mask.centerY),
            halfSize = floatArrayOf(mask.halfWidth, mask.halfHeight),
            turn = floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat()),
            featherWidth = featherWidthOf(mask.featherPercent),
            cornerFraction = mask.cornerPercent.toFloat() / MAX_MASK_PERCENT,
            invert = if (mask.invert) 1f else 0f,
        )
    }

    /** Returns the edge half-width of [featherPercent], in picture half-heights. */
    fun featherWidthOf(featherPercent: Int): Float =
        max(MAX_FEATHER_WIDTH * featherPercent / MAX_MASK_PERCENT, MIN_FEATHER_WIDTH)

    /**
     * Returns how visible [mask] leaves the point at [x] and [y] of a picture [aspect] wide per unit of height, where
     * -1 and 1 are the picture's edges and y runs down: 1 fully shown, 0 hidden. It matches the mask shader.
     */
    fun alphaAt(
        mask: ClipMask,
        x: Float,
        y: Float,
        aspect: Float,
    ): Float {
        val uniforms = uniformsOf(mask)
        val px = (x - mask.centerX) * aspect
        val py = y - mask.centerY
        val (cosine, sine) = uniforms.turn
        val qx = cosine * px + sine * py
        val qy = -sine * px + cosine * py
        val distance = distanceOf(mask, qx, qy)
        val width = uniforms.featherWidth
        val shown = 1f - smoothStep(-width, width, distance)
        return if (mask.invert) 1f - shown else shown
    }

    /** Returns the signed distance of the point [qx], [qy] in the upright frame of [mask] from its edge. */
    fun distanceOf(
        mask: ClipMask,
        qx: Float,
        qy: Float,
    ): Float {
        val halfWidth = mask.halfWidth
        val halfHeight = mask.halfHeight
        val small = min(halfWidth, halfHeight)
        val nx = qx / halfWidth
        val ny = qy / halfHeight
        val corner = mask.cornerPercent * small / MAX_MASK_PERCENT
        return when (mask.shape) {
            MaskShape.LINEAR -> qx
            MaskShape.MIRROR -> abs(qx) - halfWidth
            MaskShape.CIRCLE -> (hypot(nx, ny) - 1f) * small
            MaskShape.RECTANGLE -> roundBox(qx, qy, halfWidth, halfHeight, corner)
            MaskShape.HEART -> heart(nx * HEART_HALF_WIDTH, HEART_HALF_HEIGHT * (1f - ny)) * small / HEART_HALF_HEIGHT
            MaskShape.STAR -> star(nx, -ny) * small
        }
    }

    /** Returns the signed distance of [qx], [qy] from a box of half-extents [bx], [by] with corners of [radius]. */
    private fun roundBox(
        qx: Float,
        qy: Float,
        bx: Float,
        by: Float,
        radius: Float,
    ): Float {
        val dx = abs(qx) - bx + radius
        val dy = abs(qy) - by + radius
        return hypot(max(dx, 0f), max(dy, 0f)) + min(max(dx, dy), 0f) - radius
    }

    /** Returns the signed distance of [x], [y] from a heart with its point at the origin, upward with y up. */
    private fun heart(
        x: Float,
        y: Float,
    ): Float {
        val ax = abs(x)
        if (ax + y > 1f) return hypot(ax - HEART_LOBE_X, y - HEART_LOBE_Y) - HEART_LOBE_RADIUS
        val toTop = ax * ax + (y - 1f) * (y - 1f)
        val fold = HEART_FOLD * max(ax + y, 0f)
        val toFold = (ax - fold) * (ax - fold) + (y - fold) * (y - fold)
        return sqrt(min(toTop, toFold)) * sign(ax - y)
    }

    /** Returns the signed distance of [x], [y] from a five-pointed star of unit radius, a point up with y up. */
    private fun star(
        x: Float,
        y: Float,
    ): Float {
        var px = abs(x)
        var py = y
        val firstFold = 2f * max(STAR_K1_X * px + STAR_K1_Y * py, 0f)
        px -= firstFold * STAR_K1_X
        py -= firstFold * STAR_K1_Y
        val secondFold = 2f * max(-STAR_K1_X * px + STAR_K1_Y * py, 0f)
        px -= secondFold * -STAR_K1_X
        py -= secondFold * STAR_K1_Y
        px = abs(px)
        py -= 1f
        val bax = STAR_INNER_RATIO * -STAR_K1_Y
        val bay = STAR_INNER_RATIO * STAR_K1_X - 1f
        val along = ((px * bax + py * bay) / (bax * bax + bay * bay)).coerceIn(0f, 1f)
        return hypot(px - bax * along, py - bay * along) * sign(py * bax - px * bay)
    }

    /** Returns GLSL's smoothstep of [value] between [edge0] and [edge1]. */
    private fun smoothStep(
        edge0: Float,
        edge1: Float,
        value: Float,
    ): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (SMOOTH_STEP_RISE - 2f * t)
    }
}

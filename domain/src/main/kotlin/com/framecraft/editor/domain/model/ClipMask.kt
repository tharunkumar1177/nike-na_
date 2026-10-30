/*
 * A shape mask on a picture clip: which part of the clip's own picture stays visible.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Largest half-extent of a mask shape, in picture half-heights. */
const val MAX_MASK_SIZE: Float = 2f

/** Highest feather and corner rounding, in percent. */
const val MAX_MASK_PERCENT: Int = 100

/** The shapes a mask can take. */
enum class MaskShape {
    /** A straight edge through the center; the left side stays visible when upright. */
    LINEAR,

    /** A band between two parallel edges, upright when vertical. */
    MIRROR,

    /** An ellipse. */
    CIRCLE,

    /** A rectangle with optionally rounded corners. */
    RECTANGLE,

    /** A heart, point down when upright. */
    HEART,

    /** A five-pointed star, point up when upright. */
    STAR,
}

/**
 * A mask of [shape] centered at [centerX] and [centerY], where -1 and 1 are the edges of the clip's picture, x runs
 * right, and y runs down. [halfWidth] and [halfHeight] are the shape's half-extents in picture half-heights, so equal
 * values draw a circle on any picture shape; a mirror band uses only [halfWidth] and a linear edge neither. The shape
 * turns clockwise by [rotationDegrees], softens its edge by [featherPercent], rounds rectangle corners by
 * [cornerPercent], and shows the outside instead of the inside when [invert] is set.
 */
@Serializable
data class ClipMask(
    val shape: MaskShape,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val halfWidth: Float = DEFAULT_HALF_SIZE,
    val halfHeight: Float = DEFAULT_HALF_SIZE,
    val rotationDegrees: Float = 0f,
    val featherPercent: Int = 0,
    val cornerPercent: Int = 0,
    val invert: Boolean = false,
) {
    init {
        require(centerX in -MAX_OVERLAY_CENTER..MAX_OVERLAY_CENTER) { "Mask center x out of range: $centerX" }
        require(centerY in -MAX_OVERLAY_CENTER..MAX_OVERLAY_CENTER) { "Mask center y out of range: $centerY" }
        require(halfWidth > 0f && halfWidth <= MAX_MASK_SIZE) { "Mask width out of range: $halfWidth" }
        require(halfHeight > 0f && halfHeight <= MAX_MASK_SIZE) { "Mask height out of range: $halfHeight" }
        require(rotationDegrees.isFinite()) { "Mask rotation must be finite, was $rotationDegrees" }
        require(featherPercent in 0..MAX_MASK_PERCENT) { "Mask feather out of range: $featherPercent" }
        require(cornerPercent in 0..MAX_MASK_PERCENT) { "Mask corner out of range: $cornerPercent" }
    }

    /** Masks as they start when a shape is first chosen. */
    companion object {
        /** Half-extent of a shape before any shape-specific default. */
        private const val DEFAULT_HALF_SIZE = 0.5f

        /** Half-extent of a new rectangle. */
        private const val RECTANGLE_HALF_SIZE = 0.7f

        /** Half-extent of a new heart or star. */
        private const val FIGURE_HALF_SIZE = 0.6f

        /** Half-width of a new mirror band. */
        private const val BAND_HALF_WIDTH = 0.25f

        /** Feather of a new circle. */
        private const val CIRCLE_FEATHER = 20

        /** Feather of a new linear edge or mirror band. */
        private const val EDGE_FEATHER = 25

        /** Corner rounding of a new rectangle. */
        private const val RECTANGLE_CORNER = 20

        /** Returns the mask a newly chosen [shape] starts as: centered, upright, and sized for that shape. */
        fun defaultFor(shape: MaskShape): ClipMask =
            when (shape) {
                MaskShape.LINEAR -> {
                    ClipMask(shape, featherPercent = EDGE_FEATHER)
                }

                MaskShape.MIRROR -> {
                    ClipMask(shape, halfWidth = BAND_HALF_WIDTH, featherPercent = EDGE_FEATHER)
                }

                MaskShape.CIRCLE -> {
                    ClipMask(shape, featherPercent = CIRCLE_FEATHER)
                }

                MaskShape.RECTANGLE -> {
                    ClipMask(
                        shape,
                        halfWidth = RECTANGLE_HALF_SIZE,
                        halfHeight = RECTANGLE_HALF_SIZE,
                        cornerPercent = RECTANGLE_CORNER,
                    )
                }

                MaskShape.HEART, MaskShape.STAR -> {
                    ClipMask(shape, halfWidth = FIGURE_HALF_SIZE, halfHeight = FIGURE_HALF_SIZE)
                }
            }
    }
}

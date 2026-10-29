/*
 * Where an overlay clip sits on the canvas: position, size, rotation, and opacity.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Largest distance of an overlay center from the canvas center, in half-canvas units. */
const val MAX_OVERLAY_CENTER: Float = 2f

/** Largest overlay scale relative to its size when fitted inside the canvas. */
const val MAX_OVERLAY_SCALE: Float = 4f

/** Scale given to a newly added overlay. */
const val DEFAULT_OVERLAY_SCALE: Float = 0.4f

/**
 * Placement of an overlay on the canvas. [centerX] and [centerY] are normalized so -1 and 1 are the canvas edges,
 * with the origin at the center, x to the right, and y downward. [scale] is the overlay size relative to the largest
 * size at which it fits inside the canvas, [rotationDegrees] turns it clockwise, and [opacity] runs from 0
 * (invisible) to 1.
 */
@Serializable
data class OverlayTransform(
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
) {
    init {
        require(centerX in -MAX_OVERLAY_CENTER..MAX_OVERLAY_CENTER) { "Overlay center x out of range: $centerX" }
        require(centerY in -MAX_OVERLAY_CENTER..MAX_OVERLAY_CENTER) { "Overlay center y out of range: $centerY" }
        require(scale > 0f && scale <= MAX_OVERLAY_SCALE) { "Overlay scale out of range: $scale" }
        require(rotationDegrees.isFinite()) { "Overlay rotation must be finite, was $rotationDegrees" }
        require(opacity in 0f..1f) { "Overlay opacity out of range: $opacity" }
    }

    /** Placement for a newly added overlay: centered, at [DEFAULT_OVERLAY_SCALE], upright, and opaque. */
    companion object {
        /** The default placement. */
        val DEFAULT = OverlayTransform(scale = DEFAULT_OVERLAY_SCALE)
    }
}

/*
 * Pure math for the preview's overlay handles: where an overlay sits on screen and how gestures change it.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.framecraft.editor.domain.model.MAX_OVERLAY_SCALE
import com.framecraft.editor.domain.model.OverlayTransform
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts between screen pixels and the overlay's normalized placement on a canvas. */
object OverlayGeometry {
    /** Smallest scale a gesture can shrink an overlay to, so it stays visible and grabbable. */
    const val MIN_SCALE: Float = 0.1f

    /** Farthest a gesture can move an overlay center from the canvas center: the canvas edge. */
    const val MAX_CENTER: Float = 1f

    /** Rotation snaps to a quarter turn when within this many degrees of it. */
    const val ROTATION_SNAP_DEGREES: Float = 5f

    /** Degrees in a quarter turn. */
    private const val QUARTER_TURN: Float = 90f

    /** Degrees in a full turn. */
    private const val FULL_TURN: Float = 360f

    /**
     * Returns the on-screen size of an overlay of aspect [overlayAspect] (width over height) at [scale] on a canvas
     * of [canvas] pixels: scale 1 is the largest size that fits inside the canvas.
     */
    fun boxSize(
        canvas: Size,
        overlayAspect: Float,
        scale: Float,
    ): Size {
        val canvasAspect = canvas.width / canvas.height
        val fitWidth = if (overlayAspect >= canvasAspect) canvas.width else canvas.height * overlayAspect
        return Size(fitWidth * scale, fitWidth / overlayAspect * scale)
    }

    /**
     * Returns the on-screen size of a picture of aspect [pictureAspect] (width over height) at [scale] on a canvas of
     * [canvas] pixels when scale 1 is the smallest size that covers the whole canvas.
     */
    fun fillBoxSize(
        canvas: Size,
        pictureAspect: Float,
        scale: Float,
    ): Size {
        val canvasAspect = canvas.width / canvas.height
        val fillWidth = if (pictureAspect >= canvasAspect) canvas.height * pictureAspect else canvas.width
        return Size(fillWidth * scale, fillWidth / pictureAspect * scale)
    }

    /**
     * Returns the on-screen size of text at [scale] on a canvas of [canvas] pixels, where at scale 1 it takes
     * [widthFraction] of the canvas width and [heightFraction] of its height: text is drawn at its natural size.
     */
    fun textBoxSize(
        canvas: Size,
        widthFraction: Float,
        heightFraction: Float,
        scale: Float,
    ): Size = Size(canvas.width * widthFraction * scale, canvas.height * heightFraction * scale)

    /** Returns the screen position of [transform]'s center on a canvas of [canvas] pixels. */
    fun center(
        canvas: Size,
        transform: OverlayTransform,
    ): Offset = Offset((transform.centerX + 1f) / 2f * canvas.width, (transform.centerY + 1f) / 2f * canvas.height)

    /**
     * Returns [transform] moved by [pan] pixels, scaled by [zoom], and turned by [rotationDegrees] clockwise on a
     * canvas of [canvas] pixels. The center stays on the canvas and the scale between [MIN_SCALE] and the maximum.
     */
    fun transformed(
        transform: OverlayTransform,
        canvas: Size,
        pan: Offset,
        zoom: Float,
        rotationDegrees: Float,
    ): OverlayTransform =
        transform.copy(
            centerX = (transform.centerX + pan.x * 2f / canvas.width).coerceIn(-MAX_CENTER, MAX_CENTER),
            centerY = (transform.centerY + pan.y * 2f / canvas.height).coerceIn(-MAX_CENTER, MAX_CENTER),
            scale = (transform.scale * zoom).coerceIn(MIN_SCALE, MAX_OVERLAY_SCALE),
            rotationDegrees = normalized(transform.rotationDegrees + rotationDegrees),
        )

    /** Returns [degrees] snapped to the nearest quarter turn when within [ROTATION_SNAP_DEGREES] of it. */
    fun snappedRotation(degrees: Float): Float {
        val quarter = (degrees / QUARTER_TURN).roundToInt() * QUARTER_TURN
        return if (abs(degrees - quarter) <= ROTATION_SNAP_DEGREES) normalized(quarter) else degrees
    }

    /** Returns [degrees] wrapped into [0, 360). */
    fun normalized(degrees: Float): Float = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
}

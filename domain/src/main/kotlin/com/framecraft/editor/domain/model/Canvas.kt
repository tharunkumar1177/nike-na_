/*
 * The project canvas: its aspect ratio, what fills it behind the base clips, and where each base clip sits on it.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Alpha bits of a fully opaque ARGB color. */
private const val OPAQUE_ALPHA: Int = 0xFF

/** Bit offset of the alpha channel in an ARGB color. */
private const val ALPHA_SHIFT: Int = 24

/**
 * Shape of the output picture. [ORIGINAL] follows the first base clip's displayed size; every other value fixes the
 * width to height ratio to [widthRatio] by [heightRatio].
 */
@Serializable
enum class CanvasAspect(
    val widthRatio: Int,
    val heightRatio: Int,
) {
    @SerialName("original")
    ORIGINAL(0, 0),

    @SerialName("9:16")
    PORTRAIT_9_16(9, 16),

    @SerialName("16:9")
    LANDSCAPE_16_9(16, 9),

    @SerialName("1:1")
    SQUARE(1, 1),

    @SerialName("4:5")
    PORTRAIT_4_5(4, 5),

    @SerialName("4:3")
    LANDSCAPE_4_3(4, 3),
    ;

    /** Width divided by height, or null for [ORIGINAL], whose shape comes from the media. */
    val ratio: Float?
        get() = if (this == ORIGINAL) null else widthRatio.toFloat() / heightRatio
}

/** What shows on the canvas where no base clip covers it. */
@Serializable
sealed interface CanvasBackground {
    /** Plain black, the default. */
    @Serializable
    @SerialName("black")
    data object Black : CanvasBackground

    /** One opaque color, [argb] packed as alpha, red, green, blue. */
    @Serializable
    @SerialName("solid")
    data class Solid(
        val argb: Int,
    ) : CanvasBackground {
        init {
            require(argb ushr ALPHA_SHIFT == OPAQUE_ALPHA) { "Canvas color must be opaque, was ${argb.toUInt()}" }
        }
    }

    /** A blurred copy of the first base track, filling the canvas. */
    @Serializable
    @SerialName("blur")
    data object Blur : CanvasBackground
}

/** Project-wide canvas settings; the defaults reproduce a project without a canvas choice. */
@Serializable
data class CanvasSettings(
    val aspect: CanvasAspect = CanvasAspect.ORIGINAL,
    val background: CanvasBackground = CanvasBackground.Black,
)

/** How a base clip is sized to the canvas before its own scale: whole picture inside, or covering the canvas. */
@Serializable
enum class CanvasFit {
    @SerialName("fit")
    FIT,

    @SerialName("fill")
    FILL,
}

/**
 * Placement of a base clip on the canvas. [fit] sizes the picture to the canvas, then [scale] multiplies that size.
 * [centerX] and [centerY] are normalized so -1 and 1 are the canvas edges, with the origin at the center, x to the
 * right, and y downward; [rotationDegrees] turns the clip clockwise.
 */
@Serializable
data class CanvasPlacement(
    val fit: CanvasFit = CanvasFit.FIT,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
) {
    init {
        require(centerX in -MAX_OVERLAY_CENTER..MAX_OVERLAY_CENTER) { "Placement center x out of range: $centerX" }
        require(centerY in -MAX_OVERLAY_CENTER..MAX_OVERLAY_CENTER) { "Placement center y out of range: $centerY" }
        require(scale > 0f && scale <= MAX_OVERLAY_SCALE) { "Placement scale out of range: $scale" }
        require(rotationDegrees.isFinite()) { "Placement rotation must be finite, was $rotationDegrees" }
    }

    /** True when only [fit] differs from a clip with no placement: centered, unscaled, and upright. */
    val isCentered: Boolean
        get() = centerX == 0f && centerY == 0f && scale == 1f && rotationDegrees == 0f
}

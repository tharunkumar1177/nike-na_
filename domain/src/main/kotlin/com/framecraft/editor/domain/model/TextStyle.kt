/*
 * How a text clip looks: font, size relative to the canvas, color, outline, shadow, background, and alignment.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Default text height, as a fraction of the canvas height. */
const val DEFAULT_TEXT_SIZE_FRACTION: Float = 0.08f

/** Smallest text height, as a fraction of the canvas height. */
const val MIN_TEXT_SIZE_FRACTION: Float = 0.02f

/** Largest text height, as a fraction of the canvas height. */
const val MAX_TEXT_SIZE_FRACTION: Float = 0.3f

/** Opaque white, as ARGB. */
const val WHITE_ARGB: Int = -0x1

/** Opaque black, as ARGB. */
const val BLACK_ARGB: Int = -0x1000000

/** Largest outline or shadow length, as a fraction of the text size. */
private const val MAX_EFFECT_FRACTION = 1f

/** Typefaces a text clip can use. */
@Serializable
enum class TextFont {
    /** The system sans-serif. */
    DEFAULT,

    /** The system sans-serif in bold. */
    BOLD,

    /** The system serif. */
    SERIF,

    /** The system monospace. */
    MONOSPACE,
}

/** How the lines of a text clip line up. */
@Serializable
enum class TextAlignment {
    /** Lines start at the leading edge. */
    START,

    /** Lines are centered. */
    CENTER,

    /** Lines end at the trailing edge. */
    END,
}

/** A stroke around each glyph in [colorArgb], [widthFraction] of the text size wide. */
@Serializable
data class TextOutline(
    val colorArgb: Int = BLACK_ARGB,
    val widthFraction: Float = DEFAULT_OUTLINE_FRACTION,
) {
    init {
        require(widthFraction > 0f && widthFraction <= MAX_EFFECT_FRACTION) { "Outline width out of range" }
    }

    /** Defaults for a newly enabled outline. */
    companion object {
        /** Outline width used when the user turns the outline on. */
        const val DEFAULT_OUTLINE_FRACTION: Float = 0.08f
    }
}

/**
 * A soft shadow in [colorArgb], blurred by [radiusFraction] and moved by [offsetXFraction] and [offsetYFraction]
 * (y downward), all as fractions of the text size.
 */
@Serializable
data class TextShadow(
    val colorArgb: Int = DEFAULT_SHADOW_ARGB,
    val radiusFraction: Float = DEFAULT_RADIUS_FRACTION,
    val offsetXFraction: Float = DEFAULT_OFFSET_FRACTION,
    val offsetYFraction: Float = DEFAULT_OFFSET_FRACTION,
) {
    init {
        require(radiusFraction > 0f && radiusFraction <= MAX_EFFECT_FRACTION) { "Shadow radius out of range" }
        require(offsetXFraction in -MAX_EFFECT_FRACTION..MAX_EFFECT_FRACTION) { "Shadow x offset out of range" }
        require(offsetYFraction in -MAX_EFFECT_FRACTION..MAX_EFFECT_FRACTION) { "Shadow y offset out of range" }
    }

    /** Defaults for a newly enabled shadow. */
    companion object {
        /** Black at 60% opacity. */
        const val DEFAULT_SHADOW_ARGB: Int = -0x67000000

        /** Blur used when the user turns the shadow on. */
        const val DEFAULT_RADIUS_FRACTION: Float = 0.12f

        /** Offset used when the user turns the shadow on. */
        const val DEFAULT_OFFSET_FRACTION: Float = 0.04f
    }
}

/**
 * The look of a text clip. [sizeFraction] is the text height as a fraction of the canvas height, so text keeps its
 * proportions at any output resolution. A [backgroundArgb] draws a box behind each line.
 */
@Serializable
data class TextStyle(
    val font: TextFont = TextFont.DEFAULT,
    val sizeFraction: Float = DEFAULT_TEXT_SIZE_FRACTION,
    val colorArgb: Int = WHITE_ARGB,
    val outline: TextOutline? = null,
    val shadow: TextShadow? = null,
    val backgroundArgb: Int? = null,
    val alignment: TextAlignment = TextAlignment.CENTER,
) {
    init {
        require(sizeFraction in MIN_TEXT_SIZE_FRACTION..MAX_TEXT_SIZE_FRACTION) { "Text size out of range" }
    }

    /** Defaults for a newly enabled background box. */
    companion object {
        /** Black at 60% opacity. */
        const val DEFAULT_BACKGROUND_ARGB: Int = -0x67000000
    }
}

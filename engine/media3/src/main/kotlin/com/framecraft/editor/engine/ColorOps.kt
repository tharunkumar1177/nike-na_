/*
 * Small color operations over red, green, and blue in 0 to 1, composed into the catalog's filters.
 */
package com.framecraft.editor.engine

import kotlin.math.pow

/** Red weight of the BT.709 luma. */
private const val LUMA_RED = 0.2126f

/** Green weight of the BT.709 luma. */
private const val LUMA_GREEN = 0.7152f

/** Blue weight of the BT.709 luma. */
private const val LUMA_BLUE = 0.0722f

/** How far one step of warmth moves red up and blue down. */
private const val WARMTH_STEP = 0.08f

/** The gray a fade lifts the blacks toward. */
private const val FADE_FLOOR = 0.18f

/** A color of [r], [g], and [b], nominally from 0 to 1. */
internal data class Rgb(
    val r: Float,
    val g: Float,
    val b: Float,
) {
    /** BT.709 luma of the color. */
    val luma: Float
        get() = LUMA_RED * r + LUMA_GREEN * g + LUMA_BLUE * b

    /** Returns the color with [op] applied to each channel. */
    fun map(op: (Float) -> Float): Rgb = Rgb(op(r), op(g), op(b))
}

/** Maps one color to another. */
internal typealias ColorTransform = (Rgb) -> Rgb

/** Returns a transform that moves each channel away from the luma by [amount]: 0 is gray, 1 unchanged. */
internal fun saturate(amount: Float): ColorTransform =
    { color ->
        val luma = color.luma
        color.map { luma + (it - luma) * amount }
    }

/** Returns a transform that scales each channel's distance from [pivot] by [amount]. */
internal fun contrast(
    amount: Float,
    pivot: Float,
): ColorTransform = { color -> color.map { (it - pivot) * amount + pivot } }

/** Returns a transform that multiplies red, green, and blue by [red], [green], and [blue]. */
internal fun gain(
    red: Float,
    green: Float,
    blue: Float,
): ColorTransform = { color -> Rgb(color.r * red, color.g * green, color.b * blue) }

/** Returns a transform that warms the color by [amount] steps, raising red and lowering blue; negative cools it. */
internal fun warmth(amount: Float): ColorTransform =
    { color -> Rgb(color.r + WARMTH_STEP * amount, color.g, color.b - WARMTH_STEP * amount) }

/** Returns a transform that lifts the blacks toward a dark gray by [amount] from 0 to 1. */
internal fun fade(amount: Float): ColorTransform = { color -> color.map { it * (1f - amount) + FADE_FLOOR * amount } }

/** Returns a transform that raises each channel to 1 over [gamma]; above 1 brightens the midtones. */
internal fun gamma(gamma: Float): ColorTransform = { color -> color.map { it.coerceAtLeast(0f).pow(1f / gamma) } }

/**
 * Returns a transform that tints shadows by [shadow] and highlights by [highlight], each weighted by how dark or
 * bright the color is, scaled by [amount].
 */
internal fun splitTone(
    shadow: Rgb,
    highlight: Rgb,
    amount: Float,
): ColorTransform =
    { color ->
        val luma = color.luma
        Rgb(
            color.r + amount * ((1f - luma) * shadow.r + luma * highlight.r),
            color.g + amount * ((1f - luma) * shadow.g + luma * highlight.g),
            color.b + amount * ((1f - luma) * shadow.b + luma * highlight.b),
        )
    }

/** Returns a transform whose red, green, and blue are the input weighted by the rows [red], [green], and [blue]. */
internal fun mix(
    red: Rgb,
    green: Rgb,
    blue: Rgb,
): ColorTransform =
    { color ->
        Rgb(
            red.r * color.r + red.g * color.g + red.b * color.b,
            green.r * color.r + green.g * color.g + green.b * color.b,
            blue.r * color.r + blue.g * color.g + blue.b * color.b,
        )
    }

/** Returns a transform that applies [steps] in order. */
internal fun chain(vararg steps: ColorTransform): ColorTransform =
    { color -> steps.fold(color) { current, step -> step(current) } }

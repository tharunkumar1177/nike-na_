/*
 * Region color checks for rendered frames: compares the mean color of a rectangle with an expected color, so a
 * failure names the region that went wrong instead of a raw pixel difference.
 */
package com.framecraft.editor.engine.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs

/** Largest per-channel difference, out of 255, tolerated after H.264 encoding and color conversion. */
internal const val COLOR_TOLERANCE = 24

/** Mean red, green, and blue of a region, each from 0 to 255. */
internal data class MeanColor(
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    /** Returns true when every channel is within [tolerance] of [argb]. */
    fun matches(
        argb: Int,
        tolerance: Int,
    ): Boolean =
        abs(red - Color.red(argb)) <= tolerance &&
            abs(green - Color.green(argb)) <= tolerance &&
            abs(blue - Color.blue(argb)) <= tolerance

    /** Formats the color as `#RRGGBB`. */
    override fun toString(): String = "#%02X%02X%02X".format(red, green, blue)
}

/** Region assertions over software bitmaps. */
internal object RegionAssert {
    /** Returns the mean color of [region] in [bitmap]. */
    fun meanColor(
        bitmap: Bitmap,
        region: Rect,
    ): MeanColor {
        require(Rect(0, 0, bitmap.width, bitmap.height).contains(region)) {
            "Region $region lies outside the ${bitmap.width}x${bitmap.height} frame"
        }
        val pixels = IntArray(region.width() * region.height())
        bitmap.getPixels(pixels, 0, region.width(), region.left, region.top, region.width(), region.height())
        var red = 0L
        var green = 0L
        var blue = 0L
        pixels.forEach { pixel ->
            red += Color.red(pixel)
            green += Color.green(pixel)
            blue += Color.blue(pixel)
        }
        val count = pixels.size
        return MeanColor((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    /** Fails naming [label] unless the mean color of [region] in [bitmap] is within tolerance of [argb]. */
    fun assertMeanColor(
        bitmap: Bitmap,
        region: Rect,
        argb: Int,
        label: String,
    ) {
        val actual = meanColor(bitmap, region)
        if (!actual.matches(argb, COLOR_TOLERANCE)) {
            val expected = MeanColor(Color.red(argb), Color.green(argb), Color.blue(argb))
            throw AssertionError("$label: region $region was $actual, expected $expected within $COLOR_TOLERANCE")
        }
    }

    /** Returns the color halfway between [top] drawn at half opacity and [bottom]. */
    fun halfBlend(
        top: Int,
        bottom: Int,
    ): Int =
        Color.rgb(
            (Color.red(top) + Color.red(bottom)) / 2,
            (Color.green(top) + Color.green(bottom)) / 2,
            (Color.blue(top) + Color.blue(bottom)) / 2,
        )
}

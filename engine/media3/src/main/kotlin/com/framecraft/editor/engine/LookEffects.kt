/*
 * Maps a clip's look to the Media3 video effects that draw it: the filter's color table, then one color matrix for
 * brightness, contrast, exposure, temperature, and tint, then saturation.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SingleColorLut
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.MAX_LOOK_VALUE
import kotlin.math.pow

/** The gray contrast turns around, and the most brightness adds or takes away. */
private const val MIDDLE_GRAY = 0.5f

/** Adjustment value per doubling of contrast or exposure, so the ends of the range are two stops. */
private const val VALUE_PER_STOP = 50f

/** How far the ends of temperature and tint scale a channel. */
private const val WHITE_BALANCE_RANGE = 0.2f

/** Builds the video effects of looks. */
@OptIn(markerClass = [UnstableApi::class])
internal object LookEffects {
    /**
     * Returns the effects that draw [look], in order: the filter's color table when it shows, the color matrix when
     * any of its adjustments is set, and saturation when set. A missing or neutral look needs none.
     */
    fun of(look: ClipLook?): List<Effect> {
        if (look == null) return emptyList()
        val table =
            look.filterId
                ?.takeIf { look.usesFilter }
                ?.let { LookLuts.cubeOf(it, look.filterIntensity) }
                ?.let { SingleColorLut.createFromCube(it) }
        val matrix = matrixOf(look)?.let { values -> RgbMatrix { _, _ -> values } }
        val saturation =
            HslAdjustment
                .Builder()
                .adjustSaturation(look.saturation.toFloat())
                .build()
                .takeIf { look.saturation != 0 }
        return listOfNotNull(table, matrix, saturation)
    }

    /**
     * Returns the column-major 4 by 4 matrix of [look]'s white balance, exposure, contrast, and brightness in that
     * order, or null when all of them are 0.
     */
    fun matrixOf(look: ClipLook): FloatArray? {
        val adjusted = listOf(look.brightness, look.contrast, look.exposure, look.temperature, look.tint)
        if (adjusted.all { it == 0 }) return null
        val contrast = 2f.pow(look.contrast / VALUE_PER_STOP)
        val exposure = 2f.pow(look.exposure / VALUE_PER_STOP)
        val warmth = WHITE_BALANCE_RANGE * look.temperature / MAX_LOOK_VALUE
        val tint = WHITE_BALANCE_RANGE * look.tint / MAX_LOOK_VALUE
        val scale = exposure * contrast
        val offset = MIDDLE_GRAY * (1f - contrast) + MIDDLE_GRAY * look.brightness / MAX_LOOK_VALUE
        return floatArrayOf(
            (1f + warmth) * scale,
            0f,
            0f,
            0f,
            0f,
            (1f - tint) * scale,
            0f,
            0f,
            0f,
            0f,
            (1f - warmth) * scale,
            0f,
            offset,
            offset,
            offset,
            1f,
        )
    }
}

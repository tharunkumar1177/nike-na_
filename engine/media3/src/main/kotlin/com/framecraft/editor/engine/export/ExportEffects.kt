/*
 * Composition-wide video effects that give an export its chosen frame rate and exact output size.
 */
package com.framecraft.editor.engine.export

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import com.framecraft.editor.domain.export.ExportSize
import com.framecraft.editor.domain.time.FrameRate

/** Builds export effects. */
@OptIn(markerClass = [UnstableApi::class])
internal object ExportEffects {
    /**
     * Returns effects that drop frames above [frameRate] (sources can be faster than the project) and then
     * scale every frame to fit [size], letterboxing clips of another aspect as the preview does.
     */
    fun create(
        size: ExportSize,
        frameRate: FrameRate,
    ): List<Effect> {
        val fps = frameRate.numerator.toFloat() / frameRate.denominator
        return listOf(
            FrameDropEffect.createDefaultFrameDropEffect(fps),
            Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT),
        )
    }
}

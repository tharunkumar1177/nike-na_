/*
 * Draws one text span over the composed picture as a Media3 bitmap overlay, animated by TextCurves.
 */
package com.framecraft.editor.engine.text

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.framecraft.editor.engine.placementSettings
import com.framecraft.editor.engine.plan.TextSpan

/** Canvas side assumed until Media3 reports the real size, so a bitmap can always be produced. */
private const val UNKNOWN_CANVAS_SIDE = 1

/**
 * Shows [span] while the frame time is inside it and hides it otherwise. The text is drawn at its natural size for
 * the canvas reported by [configure], so its scale needs no fit factor; the bitmap is redrawn only when the canvas
 * changes or a typing animation reveals another character. Media3 calls it on its GL thread only.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class TextOverlay(
    private val span: TextSpan,
) : BitmapOverlay() {
    private var canvas = Size(UNKNOWN_CANVAS_SIDE, UNKNOWN_CANVAS_SIDE)
    private var bitmap: Bitmap? = null
    private var drawnCount = -1

    /** Records the canvas size and drops any bitmap drawn for another size. */
    override fun configure(videoSize: Size) {
        if (videoSize != canvas) {
            canvas = videoSize
            bitmap = null
        }
    }

    /** Returns the text bitmap at [presentationTimeUs], redrawing it when more or fewer characters show. */
    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val count = frameAt(presentationTimeUs).revealed
        val current = bitmap
        return if (current != null && count == drawnCount) {
            current
        } else {
            TextLayouter.render(span.content, canvas.width, canvas.height, count).also {
                bitmap = it
                drawnCount = count
            }
        }
    }

    /** Returns where the text sits at [presentationTimeUs], fully transparent outside its span. */
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val transform = span.transform
        val frame = frameAt(presentationTimeUs)
        val inside = presentationTimeUs >= span.startMicros && presentationTimeUs < span.endMicros
        return placementSettings(
            centerX = transform.centerX,
            centerY = transform.centerY + frame.offsetY,
            rotationDegrees = transform.rotationDegrees,
            scale = transform.scale * frame.scale,
            alpha = if (inside) transform.opacity * frame.alpha else 0f,
        )
    }

    /** Returns the animation state at timeline time [presentationTimeUs]. */
    private fun frameAt(presentationTimeUs: Long): TextFrame =
        TextCurves.frameAt(span.content, presentationTimeUs - span.startMicros, span.endMicros - span.startMicros)
}

/*
 * Lays out and draws a text clip into a bitmap sized for a canvas: wrapping, outline, shadow, and line boxes.
 */
package com.framecraft.editor.engine.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.CharacterStyle
import com.framecraft.editor.domain.model.TextAlignment
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TextFont
import com.framecraft.editor.domain.model.TextStyle
import com.framecraft.editor.engine.plan.Karaoke
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Widest a text box may grow before its lines wrap, as a fraction of the canvas width. */
private const val MAX_WIDTH_FRACTION = 0.9f

/** Margin around the glyphs, as a fraction of the text size, leaving room for line boxes and outlines. */
private const val MARGIN_FRACTION = 0.3f

/** How far a line box reaches past its glyphs, as a fraction of the text size. */
private const val BOX_PADDING_FRACTION = 0.15f

/** Corner radius of a line box, as a fraction of the text size. */
private const val BOX_RADIUS_FRACTION = 0.15f

/** Size of laid-out text as fractions of the canvas width and height. */
data class TextBox(
    val widthFraction: Float,
    val heightFraction: Float,
)

/** Text laid out for one canvas, [margin] pixels inside a [width] by [height] bitmap. */
private class LaidText(
    val layout: StaticLayout,
    val paint: TextPaint,
    val margin: Float,
    val width: Int,
    val height: Int,
)

/** Hides the characters it covers, including their shadow, without changing how the line is laid out. */
private class HiddenSpan : CharacterStyle() {
    /** Draws the covered characters fully transparent. */
    override fun updateDrawState(paint: TextPaint) {
        paint.color = Color.TRANSPARENT
        paint.clearShadowLayer()
    }
}

/** Fills the characters it covers in [color], leaving their outline pass in the outline color. */
private class HighlightSpan(
    private val color: Int,
) : CharacterStyle() {
    /** Recolors the fill pass only, so the outline keeps its own color. */
    override fun updateDrawState(paint: TextPaint) {
        if (paint.style == Paint.Style.FILL) paint.color = color
    }
}

/** Measures and renders text clips with the system fonts. */
object TextLayouter {
    /** Returns the size [content] takes on a [canvasWidth] by [canvasHeight] canvas at scale 1. */
    fun measure(
        content: TextContent,
        canvasWidth: Int,
        canvasHeight: Int,
    ): TextBox {
        val laid = layOut(content.text, content.style, canvasWidth, canvasHeight)
        return TextBox(laid.width.toFloat() / canvasWidth, laid.height.toFloat() / canvasHeight)
    }

    /**
     * Draws [content] for a [canvasWidth] by [canvasHeight] canvas, showing only its first [revealed] characters and
     * filling word [spokenWord] in the style's highlight color when it has one. The bitmap is the same size whatever
     * [revealed] and [spokenWord] are, so a typing or highlighting text never moves.
     */
    fun render(
        content: TextContent,
        canvasWidth: Int,
        canvasHeight: Int,
        revealed: Int,
        spokenWord: Int = -1,
    ): Bitmap {
        val text = content.text
        val shown = revealed.coerceIn(0, text.length)
        val highlight =
            content.style.highlightArgb?.let { color -> Karaoke.rangeOf(text, spokenWord)?.let { color to it } }
        val visible =
            if (shown >= text.length && highlight == null) {
                text
            } else {
                SpannableString(text).apply {
                    highlight?.let { (color, range) ->
                        setSpan(HighlightSpan(color), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (shown < text.length) setSpan(HiddenSpan(), shown, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        val laid = layOut(visible, content.style, canvasWidth, canvasHeight)
        val bitmap = Bitmap.createBitmap(laid.width, laid.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(laid.margin, laid.margin)
        drawGlyphs(canvas, laid, content.style, shown)
        return bitmap
    }

    /** Draws line boxes, then the outline with the shadow, then the fill; the shadow goes on the first glyph pass. */
    private fun drawGlyphs(
        canvas: Canvas,
        laid: LaidText,
        style: TextStyle,
        shown: Int,
    ) {
        val paint = laid.paint
        val size = paint.textSize
        style.backgroundArgb?.let { drawLineBoxes(canvas, laid.layout, it, size, shown) }
        val shadow = style.shadow
        val outline = style.outline
        if (shadow != null) {
            paint.setShadowLayer(
                shadow.radiusFraction * size,
                shadow.offsetXFraction * size,
                shadow.offsetYFraction * size,
                shadow.colorArgb,
            )
        }
        if (outline != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 2 * outline.widthFraction * size
            paint.color = outline.colorArgb
            laid.layout.draw(canvas)
            paint.clearShadowLayer()
        }
        paint.style = Paint.Style.FILL
        paint.color = style.colorArgb
        laid.layout.draw(canvas)
    }

    /** Draws a rounded box in [color] behind the shown part of each line of [layout]. */
    private fun drawLineBoxes(
        canvas: Canvas,
        layout: StaticLayout,
        color: Int,
        size: Float,
        shown: Int,
    ) {
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val pad = BOX_PADDING_FRACTION * size
        val radius = BOX_RADIUS_FRACTION * size
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val visibleEnd = layout.getLineVisibleEnd(line)
            if (minOf(visibleEnd, shown) > start) {
                var left = layout.getLineLeft(line)
                var right = layout.getLineRight(line)
                if (shown < visibleEnd) {
                    val edge = layout.getPrimaryHorizontal(shown)
                    if (layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT) left = edge else right = edge
                }
                val top = layout.getLineTop(line).toFloat()
                val bottom = layout.getLineBottom(line).toFloat()
                canvas.drawRoundRect(RectF(left - pad, top, right + pad, bottom), radius, radius, box)
            }
        }
    }

    /**
     * Lays [text] out in [style] for a [canvasWidth] by [canvasHeight] canvas: wrapped at most
     * [MAX_WIDTH_FRACTION] of the width, then narrowed to its widest line so alignment works within the box.
     */
    private fun layOut(
        text: CharSequence,
        style: TextStyle,
        canvasWidth: Int,
        canvasHeight: Int,
    ): LaidText {
        val size = style.sizeFraction * canvasHeight
        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = typefaceOf(style.font)
                color = style.colorArgb
            }
        val maxWidth = (canvasWidth * MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
        val wrapped = layoutOf(text, paint, style.alignment, maxWidth)
        val widest = (0 until wrapped.lineCount).maxOfOrNull { wrapped.getLineWidth(it) } ?: 0f
        val layout = layoutOf(text, paint, style.alignment, ceil(widest).toInt().coerceIn(1, maxWidth))
        val margin = size * (MARGIN_FRACTION + effectFraction(style))
        val width = ceil(layout.width + 2 * margin).toInt().coerceAtLeast(1)
        val height = ceil(layout.height + 2 * margin).toInt().coerceAtLeast(1)
        return LaidText(layout, paint, margin, width, height)
    }

    /** Returns how far the outline and shadow of [style] reach past the glyphs, as a fraction of the text size. */
    private fun effectFraction(style: TextStyle): Float {
        val outline = style.outline?.widthFraction ?: 0f
        val shadow =
            style.shadow?.let { it.radiusFraction + maxOf(abs(it.offsetXFraction), abs(it.offsetYFraction)) } ?: 0f
        return outline + shadow
    }

    /** Builds a layout of [text] with [paint], [alignment], and lines at most [width] pixels wide. */
    private fun layoutOf(
        text: CharSequence,
        paint: TextPaint,
        alignment: TextAlignment,
        width: Int,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(alignmentOf(alignment))
            .setIncludePad(false)
            .build()

    /** Maps [font] to its system typeface. */
    private fun typefaceOf(font: TextFont): Typeface =
        when (font) {
            TextFont.DEFAULT -> Typeface.DEFAULT
            TextFont.BOLD -> Typeface.DEFAULT_BOLD
            TextFont.SERIF -> Typeface.SERIF
            TextFont.MONOSPACE -> Typeface.MONOSPACE
        }

    /** Maps [alignment] to the layout alignment, which follows the text direction. */
    private fun alignmentOf(alignment: TextAlignment): Layout.Alignment =
        when (alignment) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
}

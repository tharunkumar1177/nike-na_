/*
 * Timeline drawing for text lanes: each text clip is a colored block labelled with its words.
 */
package com.framecraft.editor.timeline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.framecraft.editor.domain.model.Clip

/** How text clips look: their [clip] fill and the [label] color of their words. */
class TextLaneStyle(
    val clip: Color,
    val label: Color,
)

/** Draws text [clip] filling [block], with its words on one line at the top start, cut off with an ellipsis. */
internal fun DrawScope.drawTextClip(
    frame: TimelineFrame,
    clip: Clip,
    block: Rect,
) {
    val style = frame.style
    val padding = frame.metrics.labelPadding
    drawRect(color = style.text.clip, topLeft = block.topLeft, size = block.size)
    val content = clip.text
    val maxWidth = (block.width - padding * 2).toInt()
    if (content != null && maxWidth > 0) {
        val layout =
            style.textMeasurer.measure(
                text = content.text.replace('\n', ' '),
                style = style.labelStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth),
            )
        drawText(layout, color = style.text.label, topLeft = Offset(block.left + padding, block.top + padding))
    }
}

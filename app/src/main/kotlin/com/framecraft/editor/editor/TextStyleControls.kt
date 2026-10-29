/*
 * The text sheet's Style tab: size, color swatches, outline, shadow, and background toggles, and alignment.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.DEFAULT_TEXT_SIZE_FRACTION
import com.framecraft.editor.domain.model.MAX_TEXT_SIZE_FRACTION
import com.framecraft.editor.domain.model.MIN_TEXT_SIZE_FRACTION
import com.framecraft.editor.domain.model.TextAlignment
import com.framecraft.editor.domain.model.TextOutline
import com.framecraft.editor.domain.model.TextShadow
import com.framecraft.editor.domain.model.TextStyle
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToInt

/** Test tag of the text size slider. */
const val TEXT_SIZE_SLIDER_TAG = "text_sheet_size"

/** Test tag prefix of the color swatches; the swatch's ARGB value in hex follows. */
const val TEXT_SWATCH_TAG_PREFIX = "text_sheet_swatch_"

/** Divides a fraction into a percent. */
private const val PERCENT = 100f

/** Diameter of a color swatch. */
private val SWATCH_SIZE = 32.dp

/** Width of the ring around the chosen swatch. */
private val SWATCH_RING = 2.dp

/** A text color the sheet offers: [argb] named [nameRes] for TalkBack. */
private class Swatch(
    val argb: Int,
    @param:StringRes val nameRes: Int,
)

/** The colors offered, light to dark first so white and black lead. */
private val SWATCHES =
    listOf(
        Swatch(0xFFFFFFFF.toInt(), R.string.editor_text_color_white),
        Swatch(0xFF000000.toInt(), R.string.editor_text_color_black),
        Swatch(0xFFE53935.toInt(), R.string.editor_text_color_red),
        Swatch(0xFFFDD835.toInt(), R.string.editor_text_color_yellow),
        Swatch(0xFF43A047.toInt(), R.string.editor_text_color_green),
        Swatch(0xFF00ACC1.toInt(), R.string.editor_text_color_cyan),
        Swatch(0xFF1E88E5.toInt(), R.string.editor_text_color_blue),
        Swatch(0xFFD81B60.toInt(), R.string.editor_text_color_pink),
    )

/** Label of each alignment. */
@StringRes
private fun alignmentLabel(alignment: TextAlignment): Int =
    when (alignment) {
        TextAlignment.START -> R.string.editor_text_align_start
        TextAlignment.CENTER -> R.string.editor_text_align_center
        TextAlignment.END -> R.string.editor_text_align_end
    }

/** Controls for [style]; every change goes to [onChange] as the new style. */
@Composable
internal fun TextStyleControls(
    style: TextStyle,
    onChange: (TextStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val sizeLabel = stringResource(R.string.editor_text_size)
        val relative = (style.sizeFraction / DEFAULT_TEXT_SIZE_FRACTION * PERCENT).roundToInt()
        Text(
            text = stringResource(R.string.editor_text_size_value, relative),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = style.sizeFraction,
            onValueChange = { onChange(style.copy(sizeFraction = it)) },
            valueRange = MIN_TEXT_SIZE_FRACTION..MAX_TEXT_SIZE_FRACTION,
            modifier = Modifier.testTag(TEXT_SIZE_SLIDER_TAG).semantics { contentDescription = sizeLabel },
        )
        ColorSwatches(style.colorArgb) { onChange(style.copy(colorArgb = it)) }
        EffectChips(style, onChange)
        AlignmentChoice(style.alignment) { onChange(style.copy(alignment = it)) }
    }
}

/** A row of round swatches with [selectedArgb] ringed; tapping one sends its color to [onPick]. */
@Composable
private fun ColorSwatches(
    selectedArgb: Int,
    onPick: (Int) -> Unit,
) {
    val ring = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SWATCHES.forEach { swatch ->
            val chosen = swatch.argb == selectedArgb
            val name = stringResource(swatch.nameRes)
            Box(
                modifier =
                    Modifier
                        .testTag(TEXT_SWATCH_TAG_PREFIX + Integer.toHexString(swatch.argb))
                        .size(SWATCH_SIZE)
                        .border(SWATCH_RING, if (chosen) ring else edge, CircleShape)
                        .padding(SWATCH_RING * 2)
                        .background(Color(swatch.argb), CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(swatch.argb) }
                        .semantics {
                            contentDescription = name
                            selected = chosen
                        },
            )
        }
    }
}

/** Toggles for the outline, shadow, and line background of [style]; each change goes to [onChange]. */
@Composable
private fun EffectChips(
    style: TextStyle,
    onChange: (TextStyle) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = style.outline != null,
            onClick = { onChange(style.copy(outline = if (style.outline == null) TextOutline() else null)) },
            label = { Text(stringResource(R.string.editor_text_outline)) },
        )
        FilterChip(
            selected = style.shadow != null,
            onClick = { onChange(style.copy(shadow = if (style.shadow == null) TextShadow() else null)) },
            label = { Text(stringResource(R.string.editor_text_shadow)) },
        )
        FilterChip(
            selected = style.backgroundArgb != null,
            onClick = {
                val background = if (style.backgroundArgb == null) TextStyle.DEFAULT_BACKGROUND_ARGB else null
                onChange(style.copy(backgroundArgb = background))
            },
            label = { Text(stringResource(R.string.editor_text_background)) },
        )
    }
}

/** Left, center, and right buttons with [selected] chosen; picking one goes to [onPick]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlignmentChoice(
    selected: TextAlignment,
    onPick: (TextAlignment) -> Unit,
) {
    val options = TextAlignment.entries
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, alignment ->
            SegmentedButton(
                selected = alignment == selected,
                onClick = { onPick(alignment) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(alignmentLabel(alignment)))
            }
        }
    }
}

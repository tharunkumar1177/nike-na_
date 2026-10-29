/*
 * The two tabs of the look sheet: filters with their categories and intensity, and the six color adjustment sliders
 * that settle on 0 with a tick and reset one at a time.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.LookCategory
import com.framecraft.editor.domain.model.LookFilterCatalog
import com.framecraft.editor.domain.model.MAX_FILTER_INTENSITY
import com.framecraft.editor.domain.model.MAX_LOOK_VALUE
import com.framecraft.editor.domain.model.MIN_LOOK_VALUE
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/** Test tag of the filter intensity slider. */
const val LOOK_INTENSITY_TAG = "look_intensity_slider"

/** Test tag prefix of the category chips; `all` or the category's name in lower case follows. */
const val LOOK_CATEGORY_TAG_PREFIX = "look_category_"

/** Test tag prefix of the filter chips; `none` or the filter's id follows. */
const val LOOK_FILTER_TAG_PREFIX = "look_filter_"

/** Test tag prefix of the adjustment sliders; the adjustment's key follows. */
const val LOOK_ADJUST_SLIDER_TAG_PREFIX = "look_adjust_slider_"

/** Test tag prefix of the adjustment reset buttons; the adjustment's key follows. */
const val LOOK_ADJUST_RESET_TAG_PREFIX = "look_adjust_reset_"

/** How far from 0 an adjustment still settles on 0. */
private const val NEUTRAL_DETENT = 3

/** Size of an adjustment's reset button. */
private val RESET_BUTTON_SIZE = 40.dp

/** Returns [value], or 0 when it lies within the detent around 0. */
internal fun snapToNeutral(value: Int): Int = if (abs(value) <= NEUTRAL_DETENT) 0 else value

/**
 * The filters of [look]: its intensity while a filter is chosen, the category chips, and the filter chips of the
 * chosen category with none first. Picking a filter resets the intensity to full; each change goes to [onChange].
 */
@Composable
internal fun LookFiltersTab(
    look: ClipLook,
    onChange: (ClipLook) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<LookCategory?>(null) }
    if (look.filterId != null) {
        IntensitySlider(look.filterIntensity) { onChange(look.copy(filterIntensity = it)) }
    }
    CategoryChips(category) { category = it }
    FilterChips(look.filterId, category) { id ->
        if (id != look.filterId) onChange(look.copy(filterId = id, filterIntensity = MAX_FILTER_INTENSITY))
    }
}

/** A chip for every category and one per category, with [current] chosen; a tap goes to [onPick]. */
@Composable
private fun CategoryChips(
    current: LookCategory?,
    onPick: (LookCategory?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        (listOf(null) + LookCategory.entries).forEach { category ->
            FilterChip(
                selected = current == category,
                onClick = { onPick(category) },
                label = { Text(stringResource(categoryLabelOf(category))) },
                modifier = Modifier.testTag(LOOK_CATEGORY_TAG_PREFIX + (category?.name?.lowercase() ?: "all")),
            )
        }
    }
}

/** A chip for no filter and one per filter of [category], or of every category, with [current] chosen. */
@Composable
private fun FilterChips(
    current: String?,
    category: LookCategory?,
    onPick: (String?) -> Unit,
) {
    val shown = LookFilterCatalog.all.filter { category == null || it.category == category }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = current == null,
            onClick = { onPick(null) },
            label = { Text(stringResource(R.string.editor_look_filter_none)) },
            modifier = Modifier.testTag(LOOK_FILTER_TAG_PREFIX + "none"),
        )
        shown.forEach { filter ->
            val label = filterLabelOf(filter.id)
            if (label != null) {
                FilterChip(
                    selected = current == filter.id,
                    onClick = { onPick(filter.id) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag(LOOK_FILTER_TAG_PREFIX + filter.id),
                )
            }
        }
    }
}

/** The filter's [intensity] on a slider from 0 to 100; the finished value goes to [onCommit]. */
@Composable
private fun IntensitySlider(
    intensity: Int,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(intensity) { mutableIntStateOf(intensity) }
    val label = stringResource(R.string.editor_look_intensity)
    val shown = stringResource(R.string.editor_look_intensity_value, draft)
    Text(text = shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = draft.toFloat(),
        onValueChange = { draft = it.roundToInt().coerceIn(0, MAX_FILTER_INTENSITY) },
        onValueChangeFinished = { if (draft != intensity) onCommit(draft) },
        valueRange = 0f..MAX_FILTER_INTENSITY.toFloat(),
        modifier =
            Modifier.testTag(LOOK_INTENSITY_TAG).semantics {
                contentDescription = label
                stateDescription = shown
            },
    )
}

/** The six color adjustments of [look], each finished change going to [onChange] with the whole new look. */
@Composable
internal fun LookAdjustTab(
    look: ClipLook,
    onChange: (ClipLook) -> Unit,
) {
    LookAdjustment.entries.forEach { adjustment ->
        AdjustSlider(adjustment, adjustment.read(look)) { onChange(adjustment.write(look, it)) }
    }
}

/**
 * The [adjustment] at [value] on a slider from -100 to 100 that settles on 0 with a tick, and a reset button while
 * it is not 0; the finished value goes to [onCommit].
 */
@Composable
private fun AdjustSlider(
    adjustment: LookAdjustment,
    value: Int,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableIntStateOf(value) }
    val haptics = LocalHapticFeedback.current
    val name = stringResource(adjustment.label)
    val shown = stringResource(R.string.editor_look_adjust_value, name, draft)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = shown,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (draft != 0) {
            IconButton(
                onClick = {
                    draft = 0
                    if (value != 0) onCommit(0)
                },
                modifier = Modifier.size(RESET_BUTTON_SIZE).testTag(LOOK_ADJUST_RESET_TAG_PREFIX + adjustment.key),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_reset),
                    contentDescription = stringResource(R.string.editor_look_reset, name),
                )
            }
        }
    }
    Slider(
        value = draft.toFloat(),
        onValueChange = { raw ->
            val next = snapToNeutral(raw.roundToInt().coerceIn(MIN_LOOK_VALUE, MAX_LOOK_VALUE))
            if (next == 0 && draft != 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            draft = next
        },
        onValueChangeFinished = { if (draft != value) onCommit(draft) },
        valueRange = MIN_LOOK_VALUE.toFloat()..MAX_LOOK_VALUE.toFloat(),
        modifier =
            Modifier.testTag(LOOK_ADJUST_SLIDER_TAG_PREFIX + adjustment.key).semantics {
                contentDescription = name
                stateDescription = shown
            },
    )
}

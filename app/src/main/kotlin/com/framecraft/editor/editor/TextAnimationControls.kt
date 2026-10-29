/*
 * The text sheet's Animation tab: pick the entrance or exit, its preset, and how long it lasts.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.DEFAULT_TEXT_ANIMATION_MICROS
import com.framecraft.editor.domain.model.TextAnimation
import com.framecraft.editor.domain.model.TextAnimationKind
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToLong

/** Test tag of the animation duration slider. */
const val TEXT_DURATION_SLIDER_TAG = "text_sheet_duration"

/** Test tag of the exit animation choice. */
const val TEXT_ANIMATION_OUT_TAG = "text_sheet_animation_out"

/** Shortest animation the slider sets. */
private const val MIN_ANIMATION_MICROS = 100_000L

/** Longest animation the slider offers. */
private const val MAX_ANIMATION_MICROS = 3_000_000L

/** Microseconds in a second. */
private const val MICROS_PER_SECOND = 1_000_000f

/** Which end of the clip the controls edit. */
private enum class AnimationEnd {
    /** The entrance. */
    IN,

    /** The exit. */
    OUT,
}

/** Label of each end of the clip. */
@StringRes
private fun endLabel(end: AnimationEnd): Int =
    when (end) {
        AnimationEnd.IN -> R.string.editor_text_animation_in
        AnimationEnd.OUT -> R.string.editor_text_animation_out
    }

/** Label of each preset; null is no animation. */
@StringRes
private fun presetLabel(kind: TextAnimationKind?): Int =
    when (kind) {
        null -> R.string.editor_text_animation_none
        TextAnimationKind.FADE -> R.string.editor_text_animation_fade
        TextAnimationKind.SLIDE_UP -> R.string.editor_text_animation_slide
        TextAnimationKind.POP -> R.string.editor_text_animation_pop
        TextAnimationKind.TYPEWRITER -> R.string.editor_text_animation_typewriter
    }

/**
 * Entrance and exit controls for [content] on a clip lasting [clipDurationMicros]; every change goes to [onChange]
 * as the new content. A newly picked preset lasts the default time, or the whole clip when that is shorter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextAnimationControls(
    content: TextContent,
    clipDurationMicros: Long,
    onChange: (TextContent) -> Unit,
) {
    var end by remember { mutableStateOf(AnimationEnd.IN) }
    val current = if (end == AnimationEnd.IN) content.animationIn else content.animationOut

    /** Returns [content] with the edited end set to [animation]. */
    fun withAnimation(animation: TextAnimation?): TextContent =
        if (end == AnimationEnd.IN) content.copy(animationIn = animation) else content.copy(animationOut = animation)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SingleChoiceSegmentedButtonRow {
            AnimationEnd.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == end,
                    onClick = { end = option },
                    shape = SegmentedButtonDefaults.itemShape(index, AnimationEnd.entries.size),
                    modifier = if (option == AnimationEnd.OUT) Modifier.testTag(TEXT_ANIMATION_OUT_TAG) else Modifier,
                ) {
                    Text(stringResource(endLabel(option)))
                }
            }
        }
        val duration = current?.durationMicros ?: minOf(DEFAULT_TEXT_ANIMATION_MICROS, clipDurationMicros)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            (listOf(null) + TextAnimationKind.entries).forEach { kind ->
                FilterChip(
                    selected = current?.kind == kind,
                    onClick = { onChange(withAnimation(kind?.let { TextAnimation(it, duration) })) },
                    label = { Text(stringResource(presetLabel(kind))) },
                )
            }
        }
        if (current != null) {
            DurationSlider(current.durationMicros, minOf(MAX_ANIMATION_MICROS, clipDurationMicros)) {
                onChange(withAnimation(current.copy(durationMicros = it)))
            }
        }
    }
}

/** Labelled slider from 0 to [maxMicros] at [valueMicros], shown in tenths of a second; changes go to [onChange]. */
@Composable
private fun DurationSlider(
    valueMicros: Long,
    maxMicros: Long,
    onChange: (Long) -> Unit,
) {
    val maxSeconds = maxMicros / MICROS_PER_SECOND
    val label = stringResource(R.string.editor_text_animation_duration)
    val locale = LocalConfiguration.current.locales[0]
    val seconds = valueMicros / MICROS_PER_SECOND
    Column {
        Text(
            text = stringResource(R.string.editor_fade_value, label, String.format(locale, "%.1f", seconds)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = seconds.coerceIn(0f, maxSeconds),
            onValueChange = {
                val micros = (it * MICROS_PER_SECOND).roundToLong()
                onChange(micros.coerceAtLeast(MIN_ANIMATION_MICROS).coerceAtMost(maxMicros))
            },
            valueRange = 0f..maxSeconds,
            modifier = Modifier.testTag(TEXT_DURATION_SLIDER_TAG).semantics { contentDescription = label },
        )
    }
}

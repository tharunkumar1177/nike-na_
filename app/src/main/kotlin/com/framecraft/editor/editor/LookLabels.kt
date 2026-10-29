/*
 * Display names for the look sheet: each catalog filter, each filter category, and the six color adjustments with
 * the look field each one reads and writes.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.LookCategory

/** Display name of each catalog filter, by filter id. */
private val FILTER_LABELS: Map<String, Int> =
    mapOf(
        "porcelain" to R.string.editor_look_filter_porcelain,
        "peach" to R.string.editor_look_filter_peach,
        "rose" to R.string.editor_look_filter_rose,
        "alpine" to R.string.editor_look_filter_alpine,
        "emerald" to R.string.editor_look_filter_emerald,
        "dusk" to R.string.editor_look_filter_dusk,
        "coast" to R.string.editor_look_filter_coast,
        "honey" to R.string.editor_look_filter_honey,
        "fresh" to R.string.editor_look_filter_fresh,
        "roast" to R.string.editor_look_filter_roast,
        "seventies" to R.string.editor_look_filter_seventies,
        "polaroid" to R.string.editor_look_filter_polaroid,
        "faded-noir" to R.string.editor_look_filter_faded_noir,
        "sepia" to R.string.editor_look_filter_sepia,
        "silver" to R.string.editor_look_filter_silver,
        "noir" to R.string.editor_look_filter_noir,
        "carbon" to R.string.editor_look_filter_carbon,
        "cyanotype" to R.string.editor_look_filter_cyanotype,
    )

/** Returns the display name of the filter with [id], or null when the app has none for it. */
@StringRes
internal fun filterLabelOf(id: String): Int? = FILTER_LABELS[id]

/** Returns the display name of [category], or of every filter when it is null. */
@StringRes
internal fun categoryLabelOf(category: LookCategory?): Int =
    when (category) {
        null -> R.string.editor_look_category_all
        LookCategory.PORTRAIT -> R.string.editor_look_category_portrait
        LookCategory.SCENERY -> R.string.editor_look_category_scenery
        LookCategory.FOOD -> R.string.editor_look_category_food
        LookCategory.VINTAGE -> R.string.editor_look_category_vintage
        LookCategory.MONO -> R.string.editor_look_category_mono
    }

/**
 * One color adjustment of the look sheet, in sheet order: its [key] in test tags, its display [label], and how it
 * [read]s from and [write]s to a look.
 */
internal enum class LookAdjustment(
    val key: String,
    @StringRes val label: Int,
    val read: (ClipLook) -> Int,
    val write: (ClipLook, Int) -> ClipLook,
) {
    /** Lifts or lowers every tone. */
    BRIGHTNESS(
        "brightness",
        R.string.editor_look_adjust_brightness,
        { it.brightness },
        { look, value -> look.copy(brightness = value) },
    ),

    /** Spreads or squeezes the tones around the middle. */
    CONTRAST(
        "contrast",
        R.string.editor_look_adjust_contrast,
        { it.contrast },
        { look, value -> look.copy(contrast = value) },
    ),

    /** Deepens or drains the colors. */
    SATURATION(
        "saturation",
        R.string.editor_look_adjust_saturation,
        { it.saturation },
        { look, value -> look.copy(saturation = value) },
    ),

    /** Brightens or darkens like a change of exposure stops. */
    EXPOSURE(
        "exposure",
        R.string.editor_look_adjust_exposure,
        { it.exposure },
        { look, value -> look.copy(exposure = value) },
    ),

    /** Warms toward orange or cools toward blue. */
    TEMPERATURE(
        "temperature",
        R.string.editor_look_adjust_temperature,
        { it.temperature },
        { look, value -> look.copy(temperature = value) },
    ),

    /** Shifts toward magenta or green. */
    TINT(
        "tint",
        R.string.editor_look_adjust_tint,
        { it.tint },
        { look, value -> look.copy(tint = value) },
    ),
}

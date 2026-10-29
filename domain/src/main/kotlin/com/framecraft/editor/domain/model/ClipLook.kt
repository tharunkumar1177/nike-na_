/*
 * The look of a picture clip: an optional catalog filter at an intensity, and six color adjustments.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/** Lowest value of a color adjustment. */
const val MIN_LOOK_VALUE: Int = -100

/** Highest value of a color adjustment. */
const val MAX_LOOK_VALUE: Int = 100

/** Full strength of a filter, in percent. */
const val MAX_FILTER_INTENSITY: Int = 100

/**
 * A clip's color: the catalog filter [filterId], or none, blended in at [filterIntensity] percent, then
 * [brightness], [contrast], [saturation], [exposure], [temperature], and [tint], each from -100 to 100 where 0 leaves
 * the picture unchanged. A filter id the catalog does not know is kept and draws as no filter.
 */
@Serializable
data class ClipLook(
    val filterId: String? = null,
    val filterIntensity: Int = MAX_FILTER_INTENSITY,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val exposure: Int = 0,
    val temperature: Int = 0,
    val tint: Int = 0,
) {
    init {
        require(filterId == null || filterId.isNotBlank()) { "Filter id must not be blank" }
        require(filterIntensity in 0..MAX_FILTER_INTENSITY) { "Filter intensity out of range: $filterIntensity" }
        require(adjustments.all { it in MIN_LOOK_VALUE..MAX_LOOK_VALUE }) { "Look adjustment out of range: $this" }
    }

    /** The six adjustments in sheet order. */
    val adjustments: List<Int>
        get() = listOf(brightness, contrast, saturation, exposure, temperature, tint)

    /** True when nothing changes: no filter and every adjustment at 0. Commands store such a look as none. */
    val isNeutral: Boolean
        get() = filterId == null && adjustments.all { it == 0 }

    /** True when a filter is chosen at a visible intensity. */
    val usesFilter: Boolean
        get() = filterId != null && filterIntensity > 0
}

/** True for the track kinds whose clips take a look. */
val TrackKind.takesLook: Boolean
    get() = this == TrackKind.VIDEO || this == TrackKind.OVERLAY

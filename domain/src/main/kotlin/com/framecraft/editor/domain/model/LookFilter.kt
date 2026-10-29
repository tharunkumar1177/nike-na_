/*
 * The filter catalog: each filter's stable id and the category the look sheet groups it under.
 */
package com.framecraft.editor.domain.model

/** A group of filters in the look sheet. */
enum class LookCategory {
    /** Skin-friendly filters. */
    PORTRAIT,

    /** Landscape and outdoor filters. */
    SCENERY,

    /** Warm, rich filters for food. */
    FOOD,

    /** Faded, film-like filters. */
    VINTAGE,

    /** Black and white filters. */
    MONO,
}

/** A catalog filter: its stable stored [id] and its [category]. Display names live with the app. */
data class LookFilter(
    val id: String,
    val category: LookCategory,
)

/** The filters the look sheet offers, in sheet order. Ids are stored in projects, so they are never reused. */
object LookFilterCatalog {
    /** Every filter, grouped by category. */
    val all: List<LookFilter> =
        listOf(
            LookFilter("porcelain", LookCategory.PORTRAIT),
            LookFilter("peach", LookCategory.PORTRAIT),
            LookFilter("rose", LookCategory.PORTRAIT),
            LookFilter("alpine", LookCategory.SCENERY),
            LookFilter("emerald", LookCategory.SCENERY),
            LookFilter("dusk", LookCategory.SCENERY),
            LookFilter("coast", LookCategory.SCENERY),
            LookFilter("honey", LookCategory.FOOD),
            LookFilter("fresh", LookCategory.FOOD),
            LookFilter("roast", LookCategory.FOOD),
            LookFilter("seventies", LookCategory.VINTAGE),
            LookFilter("polaroid", LookCategory.VINTAGE),
            LookFilter("faded-noir", LookCategory.VINTAGE),
            LookFilter("sepia", LookCategory.VINTAGE),
            LookFilter("silver", LookCategory.MONO),
            LookFilter("noir", LookCategory.MONO),
            LookFilter("carbon", LookCategory.MONO),
            LookFilter("cyanotype", LookCategory.MONO),
        )

    /** Returns the filter with [id], or null when the catalog has none. */
    fun find(id: String): LookFilter? = all.firstOrNull { it.id == id }
}

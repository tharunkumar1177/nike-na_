/*
 * The color transform of every catalog filter, built from the color operations.
 */
package com.framecraft.editor.engine

/** Maps catalog filter ids to the transforms their color tables sample. */
internal object LookFilterTransforms {
    /** Every filter's transform by id; ids match the domain catalog. */
    private val transforms: Map<String, ColorTransform> =
        mapOf(
            "porcelain" to chain(fade(0.10f), saturate(0.92f), contrast(1.06f, 0.48f), warmth(0.5f)),
            "peach" to chain(warmth(1f), saturate(1.05f), gain(1.03f, 1f, 0.97f), contrast(1.04f, 0.5f)),
            "rose" to
                chain(
                    gain(1.05f, 0.98f, 1.01f),
                    saturate(1.08f),
                    contrast(1.03f, 0.5f),
                    splitTone(Rgb(0f, 0f, 0.02f), Rgb(0.03f, 0f, 0.02f), 0.5f),
                ),
            "alpine" to
                chain(
                    saturate(1.15f),
                    contrast(1.12f, 0.5f),
                    gain(0.97f, 1f, 1.05f),
                    splitTone(Rgb(0f, 0.01f, 0.04f), Rgb(0f, 0f, 0f), 0.6f),
                ),
            "emerald" to
                chain(saturate(1.10f), contrast(1.08f, 0.5f), splitTone(Rgb(0f, 0.03f, 0f), Rgb(0f, 0.02f, 0f), 0.7f)),
            "dusk" to chain(warmth(1.2f), saturate(1.05f), contrast(1.10f, 0.46f), gain(1.04f, 0.96f, 0.98f)),
            "coast" to
                chain(
                    splitTone(Rgb(-0.02f, 0.03f, 0.05f), Rgb(0.05f, 0.02f, -0.03f), 0.8f),
                    saturate(1.06f),
                    contrast(1.05f, 0.5f),
                ),
            "honey" to chain(warmth(0.8f), saturate(1.12f), contrast(1.05f, 0.5f), gain(1.05f, 1f, 0.92f)),
            "fresh" to chain(saturate(1.18f), contrast(1.06f, 0.5f), gain(0.98f, 1.04f, 0.98f)),
            "roast" to chain(contrast(1.14f, 0.45f), warmth(0.6f), saturate(0.95f), gamma(1.10f)),
            "seventies" to chain(fade(0.25f), saturate(0.75f), warmth(0.7f), contrast(0.94f, 0.5f)),
            "polaroid" to
                chain(
                    fade(0.30f),
                    saturate(0.85f),
                    contrast(1.05f, 0.55f),
                    splitTone(Rgb(0f, 0.02f, 0.04f), Rgb(0.03f, 0.02f, 0f), 0.6f),
                ),
            "faded-noir" to chain(saturate(0.40f), contrast(1.10f, 0.5f), fade(0.15f)),
            "sepia" to
                chain(
                    mix(Rgb(0.393f, 0.769f, 0.189f), Rgb(0.349f, 0.686f, 0.168f), Rgb(0.272f, 0.534f, 0.131f)),
                    saturate(0.90f),
                ),
            "silver" to chain(saturate(0f), contrast(1.08f, 0.5f)),
            "noir" to chain(saturate(0f), contrast(1.25f, 0.45f), gamma(1.15f)),
            "carbon" to chain(saturate(0f), fade(0.20f), contrast(0.95f, 0.5f)),
            "cyanotype" to chain(saturate(0f), gain(0.72f, 0.92f, 1.12f), contrast(1.05f, 0.5f)),
        )

    /** Returns the transform of filter [filterId], or null when this build does not know it. */
    fun transformFor(filterId: String): ColorTransform? = transforms[filterId]
}

/*
 * Ready-made speed curves the editor offers, plus the flat curve a custom curve starts from.
 */
package com.framecraft.editor.domain.model

/** Points in the flat curve a custom curve starts from, spread evenly across the clip. */
private const val CUSTOM_POINT_COUNT: Int = 5

/**
 * A named speed curve. Each preset stores alternating positions and speeds: position in parts of
 * [CURVE_POSITION_SCALE], then speed in thousandths of normal speed.
 */
enum class SpeedPreset(
    private vararg val values: Int,
) {
    /** Fast, slow, fast, slow, fast: cuts between moments. */
    MONTAGE(0, 2500, 25000, 400, 50000, 2500, 75000, 400, 100000, 2500),

    /** Slow build that ends fast. */
    HERO(0, 400, 40000, 600, 70000, 1500, 100000, 4000),

    /** Fast in and out around a long slow middle. */
    BULLET(0, 3000, 30000, 400, 70000, 400, 100000, 3000),

    /** Fast with a brief normal-speed beat in the middle. */
    JUMP_CUT(0, 5000, 40000, 5000, 50000, 1000, 60000, 5000, 100000, 5000),

    /** Rushes in and settles to normal speed. */
    FLASH_IN(0, 8000, 30000, 2500, 60000, 1300, 100000, 1000),

    /** Starts at normal speed and rushes out. */
    FLASH_OUT(0, 1000, 40000, 1300, 70000, 2500, 100000, 8000),
    ;

    /** The preset's curve. */
    val curve: ClipSpeed.Curve
        get() = ClipSpeed.Curve(values.toList().chunked(2) { (position, speed) -> SpeedPoint(position, speed) })

    /** Preset lookup. */
    companion object {
        /** The flat curve at normal speed a custom curve starts from. */
        val customStart: ClipSpeed.Curve =
            ClipSpeed.Curve(
                List(CUSTOM_POINT_COUNT) { index ->
                    SpeedPoint(CURVE_POSITION_SCALE * index / (CUSTOM_POINT_COUNT - 1), NORMAL_SPEED_MILLI)
                },
            )

        /** Returns the preset whose curve is [speed], or null when it is a custom curve or not a curve. */
        fun matching(speed: ClipSpeed?): SpeedPreset? = entries.firstOrNull { it.curve == speed }
    }
}

/*
 * Readable speed text shared by the timeline badge and the speed sheet, such as "2.0x" or "0.4–4.0x".
 */
package com.framecraft.editor.timeline

import com.framecraft.editor.domain.model.ClipSpeed
import java.math.BigDecimal

/** Speeds from this many thousandths up read as whole numbers, such as "10x". */
private const val WHOLE_FROM_MILLI = 10_000

/** Decimal places between thousandths and units of speed. */
private const val MILLI_PLACES = 3

/** Formats speeds for display. */
object SpeedLabel {
    /** Returns [speedMilli] thousandths of normal speed as text: "0.25x", "1.5x", "2.0x", or "10x". */
    fun of(speedMilli: Int): String {
        val units = BigDecimal(speedMilli).movePointLeft(MILLI_PLACES).stripTrailingZeros().toPlainString()
        val shown = if (!units.contains('.') && speedMilli < WHOLE_FROM_MILLI) "$units.0" else units
        return "${shown}x"
    }

    /** Returns the badge of a clip playing at [speed]: its speed, its range for a curve, or null at normal speed. */
    fun badgeOf(speed: ClipSpeed?): String? =
        when (speed) {
            null -> {
                null
            }

            is ClipSpeed.Constant -> {
                of(speed.speedMilli)
            }

            is ClipSpeed.Curve -> {
                val slowest = of(speed.points.minOf { it.speedMilli }).removeSuffix("x")
                "$slowest–${of(speed.maxSpeedMilli)}"
            }
        }
}

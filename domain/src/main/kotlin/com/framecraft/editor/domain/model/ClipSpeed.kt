/*
 * How fast a timed clip plays its source: one constant speed or a curve of speeds across the clip.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Speed that plays the source as recorded, in thousandths of normal speed. */
const val NORMAL_SPEED_MILLI: Int = 1_000

/** Slowest speed, 0.1x, in thousandths of normal speed. */
const val MIN_SPEED_MILLI: Int = 100

/** Fastest constant speed, 100x, in thousandths of normal speed. */
const val MAX_CONSTANT_SPEED_MILLI: Int = 100_000

/** Fastest speed inside a curve, 10x, in thousandths of normal speed. */
const val MAX_CURVE_SPEED_MILLI: Int = 10_000

/** Position of a curve's last point; positions are this many parts of the clip's source span. */
const val CURVE_POSITION_SCALE: Int = 100_000

/** Most points a stored curve may have; splitting a curve can add one point at each cut. */
const val MAX_CURVE_POINTS: Int = 16

/** A curve point: [speedMilli] thousandths of normal speed at [position] parts of [CURVE_POSITION_SCALE]. */
@Serializable
data class SpeedPoint(
    val position: Int,
    val speedMilli: Int,
)

/** A clip's playback speed. Normal speed is no [ClipSpeed] at all. */
@Serializable
sealed interface ClipSpeed {
    /** Fastest speed anywhere in the clip, in thousandths of normal speed. */
    val maxSpeedMilli: Int

    /** One speed for the whole clip, [speedMilli] thousandths of normal speed, from 0.1x to 100x. */
    @Serializable
    @SerialName("constant")
    data class Constant(
        val speedMilli: Int,
    ) : ClipSpeed {
        init {
            require(speedMilli in MIN_SPEED_MILLI..MAX_CONSTANT_SPEED_MILLI) { "Speed out of range: $speedMilli" }
        }

        override val maxSpeedMilli: Int
            get() = speedMilli
    }

    /**
     * Speeds that change across the clip's source: straight lines between [points], which start at position 0, end
     * at [CURVE_POSITION_SCALE], move strictly forward, and stay from 0.1x to 10x.
     */
    @Serializable
    @SerialName("curve")
    data class Curve(
        val points: List<SpeedPoint>,
    ) : ClipSpeed {
        init {
            require(points.size in 2..MAX_CURVE_POINTS) { "A speed curve needs 2 to $MAX_CURVE_POINTS points" }
            require(points.first().position == 0 && points.last().position == CURVE_POSITION_SCALE) {
                "A speed curve must span the whole clip"
            }
            require(points.zipWithNext().all { (a, b) -> a.position < b.position }) {
                "Speed curve points must move forward"
            }
            require(points.all { it.speedMilli in MIN_SPEED_MILLI..MAX_CURVE_SPEED_MILLI }) {
                "Speed curve point out of range"
            }
        }

        override val maxSpeedMilli: Int
            get() = points.maxOf { it.speedMilli }
    }
}

/**
 * Returns the part of this speed that covers source offsets [fromMicros] to [toMicros] of a clip reading
 * [spanMicros] of source. A constant speed is unchanged; a curve gets points at both cuts and keeps the points
 * between them, moved to the new span.
 */
fun ClipSpeed.slice(
    spanMicros: Long,
    fromMicros: Long,
    toMicros: Long,
): ClipSpeed {
    require(fromMicros in 0 until toMicros && toMicros <= spanMicros) { "Speed slice out of range" }
    if (this !is ClipSpeed.Curve) return this
    val length = toMicros - fromMicros
    val inside =
        points
            .map { (it.position.toLong() * spanMicros / CURVE_POSITION_SCALE) to it.speedMilli }
            .filter { (offset, _) -> offset in (fromMicros + 1) until toMicros }
            .map { (offset, speed) -> SpeedPoint(positionOf(offset - fromMicros, length), speed) }
            .filter { it.position in 1 until CURVE_POSITION_SCALE }
            .distinctBy { it.position }
    val first = SpeedPoint(0, speedMilliAt(fromMicros, spanMicros))
    val last = SpeedPoint(CURVE_POSITION_SCALE, speedMilliAt(toMicros, spanMicros))
    return ClipSpeed.Curve(listOf(first) + inside + last)
}

/** Returns the curve's speed at source offset [offsetMicros] of a clip reading [spanMicros], to the nearest unit. */
internal fun ClipSpeed.Curve.speedMilliAt(
    offsetMicros: Long,
    spanMicros: Long,
): Int {
    val position = offsetMicros.toDouble() * CURVE_POSITION_SCALE / spanMicros
    val after = points.indexOfFirst { it.position >= position }.coerceAtLeast(1)
    val a = points[after - 1]
    val b = points[after]
    val share = ((position - a.position) / (b.position - a.position)).coerceIn(0.0, 1.0)
    return (a.speedMilli + (b.speedMilli - a.speedMilli) * share).roundToInt()
}

/** Returns [offsetMicros] of [lengthMicros] as a curve position, rounded half up. */
private fun positionOf(
    offsetMicros: Long,
    lengthMicros: Long,
): Int = ((offsetMicros * CURVE_POSITION_SCALE + lengthMicros / 2) / lengthMicros).toInt()

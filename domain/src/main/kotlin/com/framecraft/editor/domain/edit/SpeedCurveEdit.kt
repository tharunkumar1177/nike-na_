/*
 * Editing rules for speed curves and the logarithmic scale speed controls use.
 */
package com.framecraft.editor.domain.edit

import com.framecraft.editor.domain.model.CURVE_POSITION_SCALE
import com.framecraft.editor.domain.model.ClipSpeed
import com.framecraft.editor.domain.model.MAX_CURVE_SPEED_MILLI
import com.framecraft.editor.domain.model.MIN_SPEED_MILLI
import com.framecraft.editor.domain.model.NORMAL_SPEED_MILLI
import com.framecraft.editor.domain.model.SpeedPoint
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Speed change of one nudge, 0.1x, in thousandths of normal speed. */
private const val NUDGE_MILLI: Int = 100

/** Below this speed, controls step by 0.01x. */
private const val FINE_STEP_BELOW_MILLI: Int = 1_000

/** Below this speed and at or above [FINE_STEP_BELOW_MILLI], controls step by 0.1x; above, by 1x. */
private const val COARSE_STEP_FROM_MILLI: Int = 10_000

/** Step of speeds under 1x, in thousandths. */
private const val FINE_STEP_MILLI: Int = 10

/** Step of speeds from 1x to 10x, in thousandths. */
private const val MIDDLE_STEP_MILLI: Int = 100

/** Step of speeds from 10x, in thousandths. */
private const val COARSE_STEP_MILLI: Int = 1_000

/** Distance on the log scale from 1x within which a control settles on exactly 1x. */
private const val NORMAL_SNAP_DECADES: Double = 0.03

/** Thousandths in one unit of speed. */
private const val MILLI: Double = 1_000.0

/** Base of the speed scale's logarithm. */
private const val LOG_BASE: Double = 10.0

/** Changes to a speed curve that keep it valid. Only the first and last points are fixed. */
object SpeedCurveEdit {
    /** Most points the editor lets a curve have. */
    const val MAX_EDITOR_POINTS: Int = 10

    /** How close, in curve positions, a playhead must be to a point to act on it rather than add one. */
    const val NEAR_POSITION: Int = 2_000

    /** Returns [curve] with point [index] at [speedMilli], kept between 0.1x and 10x. */
    fun withSpeed(
        curve: ClipSpeed.Curve,
        index: Int,
        speedMilli: Int,
    ): ClipSpeed.Curve {
        val speed = speedMilli.coerceIn(MIN_SPEED_MILLI, MAX_CURVE_SPEED_MILLI)
        val points = curve.points.mapIndexed { i, point -> if (i == index) point.copy(speedMilli = speed) else point }
        return ClipSpeed.Curve(points)
    }

    /** Returns [curve] with point [index] 0.1x faster when [faster], otherwise 0.1x slower. */
    fun nudged(
        curve: ClipSpeed.Curve,
        index: Int,
        faster: Boolean,
    ): ClipSpeed.Curve {
        val step = if (faster) NUDGE_MILLI else -NUDGE_MILLI
        return withSpeed(curve, index, curve.points[index].speedMilli + step)
    }

    /** Returns the index of the point of [curve] within [NEAR_POSITION] of [position], or null if there is none. */
    fun pointNear(
        curve: ClipSpeed.Curve,
        position: Int,
    ): Int? =
        curve.points.indices
            .filter { abs(curve.points[it].position - position) <= NEAR_POSITION }
            .minByOrNull { abs(curve.points[it].position - position) }

    /** True when a point can be added to [curve] at [position]: room is left and no point is near. */
    fun canAdd(
        curve: ClipSpeed.Curve,
        position: Int,
    ): Boolean =
        curve.points.size < MAX_EDITOR_POINTS &&
            position in 1 until CURVE_POSITION_SCALE &&
            pointNear(curve, position) == null

    /** Returns [curve] with a point at [position] on the existing line, so the curve keeps its shape. */
    fun withPointAt(
        curve: ClipSpeed.Curve,
        position: Int,
    ): ClipSpeed.Curve {
        require(canAdd(curve, position)) { "No point can be added at $position" }
        val after = curve.points.indexOfFirst { it.position > position }
        val a = curve.points[after - 1]
        val b = curve.points[after]
        val share = (position - a.position).toDouble() / (b.position - a.position)
        val speed = (a.speedMilli + (b.speedMilli - a.speedMilli) * share).roundToInt()
        val points = curve.points.toMutableList().apply { add(after, SpeedPoint(position, speed)) }
        return ClipSpeed.Curve(points)
    }

    /** True when point [index] of [curve] can be deleted: it is neither the first nor the last. */
    fun canDelete(
        curve: ClipSpeed.Curve,
        index: Int,
    ): Boolean = index in 1 until curve.points.lastIndex

    /** Returns [curve] without point [index], which must not be the first or last. */
    fun withoutPoint(
        curve: ClipSpeed.Curve,
        index: Int,
    ): ClipSpeed.Curve {
        require(canDelete(curve, index)) { "Point $index of a curve cannot be deleted" }
        return ClipSpeed.Curve(curve.points.filterIndexed { i, _ -> i != index })
    }

    /** Returns [curve] with every point at normal speed. */
    fun flattened(curve: ClipSpeed.Curve): ClipSpeed.Curve =
        ClipSpeed.Curve(curve.points.map { it.copy(speedMilli = NORMAL_SPEED_MILLI) })
}

/** The logarithmic scale that speed sliders and the curve editor share. */
object SpeedScale {
    /** Returns where [speedMilli] sits between [minMilli] and [maxMilli] on a log scale, from 0 to 1. */
    fun fractionOf(
        speedMilli: Int,
        minMilli: Int,
        maxMilli: Int,
    ): Float {
        val low = log10(minMilli.toDouble())
        val share = (log10(speedMilli.toDouble()) - low) / (log10(maxMilli.toDouble()) - low)
        return share.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Returns the speed at [fraction] of the log scale from [minMilli] to [maxMilli], rounded to a readable step and
     * settled on 1x when close to it.
     */
    fun speedAt(
        fraction: Float,
        minMilli: Int,
        maxMilli: Int,
    ): Int {
        val low = log10(minMilli.toDouble())
        val decades = low + (log10(maxMilli.toDouble()) - low) * fraction.coerceIn(0f, 1f)
        val nearNormal = abs(decades - log10(NORMAL_SPEED_MILLI.toDouble())) < NORMAL_SNAP_DECADES
        val speed = rounded(LOG_BASE.pow(decades).roundToInt()).coerceIn(minMilli, maxMilli)
        return if (nearNormal) NORMAL_SPEED_MILLI else speed
    }

    /** Returns [speedMilli] as a speed in units, such as 2.5 for 2500. */
    fun units(speedMilli: Int): Double = speedMilli / MILLI

    /** Returns [speedMilli] rounded to 0.01x below 1x, 0.1x below 10x, and 1x from there. */
    private fun rounded(speedMilli: Int): Int {
        val step =
            when {
                speedMilli < FINE_STEP_BELOW_MILLI -> FINE_STEP_MILLI
                speedMilli < COARSE_STEP_FROM_MILLI -> MIDDLE_STEP_MILLI
                else -> COARSE_STEP_MILLI
            }
        return (speedMilli + step / 2) / step * step
    }
}

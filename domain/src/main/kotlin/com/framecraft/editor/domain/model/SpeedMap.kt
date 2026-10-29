/*
 * Maps between a sped clip's source time and its timeline time, with durations computed exactly as Media3 computes
 * them so the timeline and the player agree.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate
import kotlin.math.ceil
import kotlin.math.floor

/** Thousandths in one unit of speed. */
private const val MILLI: Double = 1_000.0

/** Middle of a curve step, where its speed is sampled. */
private const val STEP_MIDDLE: Double = 0.5

/**
 * A clip's speed as steps of constant speed over [sourceSpanMicros] of source. Each step starts at a source offset
 * from the clip's first source frame and plays at one speed. A constant speed is one step; a curve is split into
 * steps no longer than one project frame, each playing the curve's speed at its middle.
 */
class SpeedMap private constructor(
    val sourceSpanMicros: Long,
    private val starts: LongArray,
    private val speeds: FloatArray,
) {
    /** Timeline length of the clip: the sum of each step's length over its speed, rounded down. */
    val durationMicros: Long = timelineAt(sourceSpanMicros)

    /** Returns the timeline offset at which source offset [sourceMicros] plays. */
    fun timelineAt(sourceMicros: Long): Long {
        var total = 0.0
        for (index in starts.indices) {
            val start = starts[index]
            if (start >= sourceMicros) break
            total += (minOf(endOf(index), sourceMicros) - start).toDouble() / speeds[index].toDouble()
        }
        return floor(total).toLong()
    }

    /** Returns the source offset that plays at timeline offset [timelineMicros], at most the span. */
    fun sourceAt(timelineMicros: Long): Long {
        var total = 0.0
        for (index in starts.indices) {
            val speed = speeds[index].toDouble()
            val stepTime = (endOf(index) - starts[index]).toDouble() / speed
            if (total + stepTime >= timelineMicros) {
                return (starts[index] + ((timelineMicros - total) * speed).toLong()).coerceAtMost(endOf(index))
            }
            total += stepTime
        }
        return sourceSpanMicros
    }

    /** Returns the speed of the step that plays source offset [sourceMicros]. */
    fun speedAt(sourceMicros: Long): Float = speeds[stepAt(sourceMicros)]

    /** Returns the source offset where the step after the one playing [sourceMicros] begins, or null after the last. */
    fun nextChangeAfter(sourceMicros: Long): Long? = starts.getOrNull(stepAt(sourceMicros) + 1)

    /** Returns the index of the step that plays source offset [sourceMicros]. */
    private fun stepAt(sourceMicros: Long): Int {
        val found = starts.binarySearch(sourceMicros)
        return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
    }

    /** Returns the exclusive source end of step [index]. */
    private fun endOf(index: Int): Long = if (index + 1 < starts.size) starts[index + 1] else sourceSpanMicros

    /** Construction from a clip speed. */
    companion object {
        /** Returns the steps of [speed] over [sourceSpanMicros] of source for a project at [frameRate]. */
        fun of(
            speed: ClipSpeed,
            sourceSpanMicros: Long,
            frameRate: FrameRate,
        ): SpeedMap {
            require(sourceSpanMicros > 0) { "A sped clip must read some source, was $sourceSpanMicros" }
            return when (speed) {
                is ClipSpeed.Constant -> {
                    SpeedMap(sourceSpanMicros, longArrayOf(0L), floatArrayOf((speed.speedMilli / MILLI).toFloat()))
                }

                is ClipSpeed.Curve -> {
                    curveSteps(speed, sourceSpanMicros, frameRate.startOfFrame(1))
                }
            }
        }

        /** Returns the timeline length of [sourceSpanMicros] of source played at [speed]; normal speed keeps it. */
        fun durationFor(
            speed: ClipSpeed?,
            sourceSpanMicros: Long,
            frameRate: FrameRate,
        ): Long = speed?.let { of(it, sourceSpanMicros, frameRate).durationMicros } ?: sourceSpanMicros

        /**
         * Returns the source span whose timeline length at [speed] is closest to [durationMicros], preferring the
         * shorter span on a tie; normal speed keeps the duration.
         */
        fun spanFor(
            speed: ClipSpeed?,
            durationMicros: Long,
            frameRate: FrameRate,
        ): Long {
            if (speed == null) return durationMicros
            var low = 1L
            var high = durationMicros * speed.maxSpeedMilli / NORMAL_SPEED_MILLI + 1
            while (low < high) {
                val middle = low + (high - low) / 2
                if (durationFor(speed, middle, frameRate) >= durationMicros) high = middle else low = middle + 1
            }
            val shorter = (low - 1).coerceAtLeast(1L)
            val below = durationMicros - durationFor(speed, shorter, frameRate)
            val above = durationFor(speed, low, frameRate) - durationMicros
            return if (shorter < low && below <= above) shorter else low
        }

        /** Returns [curve] over [spanMicros] as steps of at most [frameMicros] of source each. */
        private fun curveSteps(
            curve: ClipSpeed.Curve,
            spanMicros: Long,
            frameMicros: Long,
        ): SpeedMap {
            val starts = ArrayList<Long>()
            val speeds = ArrayList<Float>()
            curve.points.zipWithNext().forEach { (a, b) ->
                val from = a.position.toLong() * spanMicros / CURVE_POSITION_SCALE
                val length = b.position.toLong() * spanMicros / CURVE_POSITION_SCALE - from
                val count = ceil(length.toDouble() / frameMicros).toLong().coerceAtLeast(1L)
                for (step in 0 until count) {
                    val start = from + length * step / count
                    val share = (step + STEP_MIDDLE) / count
                    val milli = a.speedMilli + (b.speedMilli - a.speedMilli) * share
                    if (starts.lastOrNull() != start) {
                        starts += start
                        speeds += (milli / MILLI).toFloat()
                    }
                }
            }
            return SpeedMap(spanMicros, starts.toLongArray(), speeds.toFloatArray())
        }
    }
}

/** Returns how this clip's speed maps its time in a project at [frameRate], or null at normal speed. */
fun Clip.speedMap(frameRate: FrameRate): SpeedMap? = speed?.let { SpeedMap.of(it, sourceSpanMicros, frameRate) }

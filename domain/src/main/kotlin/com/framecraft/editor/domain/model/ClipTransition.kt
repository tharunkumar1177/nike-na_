/*
 * A transition at the cut after a clip: which effect blends it into the next clip, and for how long.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shortest transition, 100 ms; a transition also never lasts under two frames. */
const val MIN_TRANSITION_MICROS: Long = 100_000L

/** Longest transition, 4 s. */
const val MAX_TRANSITION_MICROS: Long = 4_000_000L

/** Length a cut's transition starts at, 500 ms. */
const val DEFAULT_TRANSITION_MICROS: Long = 500_000L

/** The effect that carries one clip into the next. */
@Serializable
enum class TransitionKind {
    /** The next clip fades in over the current one. */
    @SerialName("dissolve")
    DISSOLVE,

    /** The current clip fades to black, then the next fades in from black. */
    @SerialName("fadeBlack")
    FADE_BLACK,

    /** The current clip fades to white, then the next fades in from white. */
    @SerialName("fadeWhite")
    FADE_WHITE,

    /** The next clip slides in from the right over the current one. */
    @SerialName("slideLeft")
    SLIDE_LEFT,

    /** The next clip pushes the current one out to the left. */
    @SerialName("pushLeft")
    PUSH_LEFT,

    /** The current clip zooms in while the next fades in over it. */
    @SerialName("zoom")
    ZOOM,
}

/**
 * A [kind] of transition lasting [durationMicros], centered on the cut after the clip that carries it. It plays
 * hidden source past both sides of the cut, so the timeline keeps its length and nothing freezes.
 */
@Serializable
data class ClipTransition(
    val kind: TransitionKind,
    val durationMicros: Long,
) {
    init {
        require(durationMicros > 0) { "A transition must last, was $durationMicros" }
    }
}

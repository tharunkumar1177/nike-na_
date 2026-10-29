/*
 * Keeps a text clip's animations inside the clip when commands change its length.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.TextAnimation
import com.framecraft.editor.domain.model.TextContent

/**
 * Returns this content for a clip [durationMicros] long: each animation takes [inMicros] or [outMicros] when given,
 * and is otherwise shortened to fit the clip on its own.
 */
internal fun TextContent.fitted(
    durationMicros: Long,
    inMicros: Long? = null,
    outMicros: Long? = null,
): TextContent =
    copy(
        animationIn = animationIn?.resized(inMicros, durationMicros),
        animationOut = animationOut?.resized(outMicros, durationMicros),
    )

/** Returns this content without its animations, for comparing two parts of one clip. */
internal fun TextContent.still(): TextContent = copy(animationIn = null, animationOut = null)

/** Returns this animation lasting [exactMicros], or its own length capped at [limitMicros]. */
private fun TextAnimation.resized(
    exactMicros: Long?,
    limitMicros: Long,
): TextAnimation = copy(durationMicros = exactMicros ?: minOf(durationMicros, limitMicros))

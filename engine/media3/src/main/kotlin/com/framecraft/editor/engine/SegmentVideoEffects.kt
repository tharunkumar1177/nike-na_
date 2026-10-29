/*
 * The one place that orders a picture segment's own video effects: chroma key, then mask, then look.
 */
package com.framecraft.editor.engine

import androidx.media3.common.Effect
import com.framecraft.editor.engine.plan.PlanSegment

/** Builds the video effects of a picture segment. */
internal object SegmentVideoEffects {
    /**
     * Returns the effects that draw [segment]'s picture, in order: the chroma key, which must see the source's own
     * colors to match the picked key color; the mask, which only writes alpha; and the look's colors last.
     */
    fun of(segment: PlanSegment.Media): List<Effect> =
        listOfNotNull(segment.chromaKey?.let(::ChromaKeyEffect), segment.mask?.let(::ClipMaskEffect)) +
            LookEffects.of(segment.look)
}

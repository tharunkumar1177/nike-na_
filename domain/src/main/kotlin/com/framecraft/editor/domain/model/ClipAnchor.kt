/*
 * The link that keeps a clip in sync with the clip it is attached to.
 */
package com.framecraft.editor.domain.model

import kotlinx.serialization.Serializable

/**
 * Attaches a clip to [parentClipId]: the clip starts [offsetMicros] after the parent starts. The offset may be
 * negative as long as the resolved start is not before zero.
 */
@Serializable
data class ClipAnchor(
    val parentClipId: String,
    val offsetMicros: Long,
) {
    init {
        require(parentClipId.isNotBlank()) { "Anchor parent id must not be blank" }
    }
}

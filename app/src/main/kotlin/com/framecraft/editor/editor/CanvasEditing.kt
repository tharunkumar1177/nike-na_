/*
 * Editor-only canvas state: whether the canvas sheet is open and which base clip shows its placement handles.
 */
package com.framecraft.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Whether the canvas sheet is open and which base clip, if any, shows its placement handles. */
internal class CanvasEditing {
    /** True while the canvas sheet shows. */
    var sheetOpen by mutableStateOf(false)

    /** Base clip whose handles show; selecting another clip hides them. */
    private var adjustingClipId by mutableStateOf<String?>(null)

    /** Returns true when the handles of clip [clipId] show. */
    fun isAdjusting(clipId: String?): Boolean = clipId != null && adjustingClipId == clipId

    /** Shows the handles of clip [clipId], or hides them when they already show. */
    fun toggleAdjust(clipId: String?) {
        adjustingClipId = if (isAdjusting(clipId)) null else clipId
    }
}

/** Returns the canvas editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberCanvasEditing(): CanvasEditing = remember { CanvasEditing() }

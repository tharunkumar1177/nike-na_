/*
 * Editor-only speed state: which clip, if any, the speed sheet shows.
 */
package com.framecraft.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Which clip the speed sheet shows; null while the sheet is closed. */
internal class SpeedEditing {
    /** Clip whose speed the sheet shows, or null while it is closed. */
    var clipId by mutableStateOf<String?>(null)
        private set

    /** Opens the sheet on clip [id]; a null id leaves it closed. */
    fun open(id: String?) {
        clipId = id
    }

    /** Closes the sheet. */
    fun close() {
        clipId = null
    }
}

/** Returns the speed editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberSpeedEditing(): SpeedEditing = remember { SpeedEditing() }

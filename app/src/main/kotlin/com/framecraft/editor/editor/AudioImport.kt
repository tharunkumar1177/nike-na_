/*
 * Adds a picked sound file to the open project as music at the playhead.
 */
package com.framecraft.editor.editor

import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.media.ImportResult
import com.framecraft.editor.media.MediaImporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Probes a picked sound file with [importer] on [ioDispatcher] and hands the placed music clip to [dispatch], which
 * selects it once saved. Unreadable files raise the skipped-files notice on [state]; a project whose audio lanes are
 * all taken at the playhead raises the rejection notice.
 */
class AudioImport(
    private val scope: CoroutineScope,
    private val importer: MediaImporter,
    private val idGenerator: IdGenerator,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: MutableStateFlow<EditorState>,
    private val dispatch: (EditCommand, String?) -> Unit,
) {
    private val mutableBusy = MutableStateFlow(false)

    /** True while a picked file is being probed; further picks are ignored until it finishes. */
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    /** Adds the sound file at [uri] as music at [playheadMicros]; ignored while busy or when no project is open. */
    fun add(
        uri: String,
        playheadMicros: Long,
    ) {
        if (mutableBusy.value || state.value !is EditorState.Ready) return
        mutableBusy.value = true
        scope.launch {
            try {
                val result = withContext(ioDispatcher) { importer.importUri(uri) }
                val project = (state.value as? EditorState.Ready)?.project
                if (result is ImportResult.Imported && project != null) {
                    val command = AudioPlacement.proposeMusic(project, playheadMicros, result.asset, idGenerator)
                    if (command == null) {
                        state.updateReady { it.copy(editRejected = true) }
                    } else {
                        dispatch(command, command.clip.id)
                    }
                } else {
                    state.updateReady { it.copy(skippedCount = it.skippedCount + 1) }
                }
            } finally {
                mutableBusy.value = false
            }
        }
    }
}

/*
 * Editor state holder: creates or opens a saved project, applies edits through its session, and owns its preview.
 */
package com.framecraft.editor.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.framecraft.editor.data.project.DispatchResult
import com.framecraft.editor.data.project.OpenProjectResult
import com.framecraft.editor.data.project.ProjectSession
import com.framecraft.editor.data.project.ProjectStore
import com.framecraft.editor.data.project.SessionState
import com.framecraft.editor.di.AppContainer
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.media.ImportResult
import com.framecraft.editor.media.MediaImporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Why a project could not be opened. */
enum class OpenFailure {
    /** Saved by a newer version of the app. */
    NEWER_VERSION,

    /** The project's files could not be read. */
    UNREADABLE,

    /** The project could not be written to storage. */
    STORAGE,
}

/** What the editor is showing. */
sealed interface EditorState {
    /** No project is open; the home screen shows. */
    data object Idle : EditorState

    /** Media is being probed or a project is being opened. */
    data object Loading : EditorState

    /**
     * [project] is open. The counters and flags are one-shot notices not yet shown: [skippedCount] picked
     * files could not be read, [droppedEditCount] edits were lost in a crash, [saveFailed] an edit
     * could not be saved, and [editRejected] an edit would have broken the project.
     */
    data class Ready(
        val project: Project,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val selectedClipId: String? = null,
        val skippedCount: Int = 0,
        val droppedEditCount: Int = 0,
        val saveFailed: Boolean = false,
        val editRejected: Boolean = false,
    ) : EditorState

    /** None of the picked files could be read. */
    data object AllFailed : EditorState

    /** The project could not be opened or created, for [reason]. */
    data class OpenFailed(
        val reason: OpenFailure,
    ) : EditorState
}

/**
 * Holds the open project, its saving session, and its preview across configuration changes. [idGenerator] also
 * makes the ids of clips that edits create.
 */
class EditorViewModel(
    private val importer: MediaImporter,
    val idGenerator: IdGenerator,
    private val store: ProjectStore,
    private val previewFactory: () -> ProjectPreview,
    private val mediaFactory: () -> TimelineMedia,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<EditorState>(EditorState.Idle)
    private var open: OpenProject? = null

    /** Current editor state. */
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    /** Preview of the open project; non-null while the state is [EditorState.Ready]. */
    val preview: ProjectPreview?
        get() = open?.preview

    /** Adds picked media to the open project as overlays. */
    val overlays: OverlayImport =
        OverlayImport(viewModelScope, importer, idGenerator, ioDispatcher, mutableState, ::dispatch)

    /** Adds picked sound files to the open project as music. */
    val audio: AudioImport = AudioImport(viewModelScope, importer, idGenerator, ioDispatcher, mutableState, ::dispatch)

    /** Timeline thumbnails and waveforms of the open project; non-null while the state is [EditorState.Ready]. */
    val media: TimelineMedia?
        get() = open?.media

    /** Imports [uris] into a new saved project named [projectName]; ignored when empty or a project is open. */
    fun importMedia(
        uris: List<String>,
        projectName: String,
    ) {
        if (uris.isEmpty() || mutableState.value != EditorState.Idle) return
        mutableState.value = EditorState.Loading
        viewModelScope.launch {
            val results = withContext(ioDispatcher) { uris.map(importer::importUri) }
            val assets = results.filterIsInstance<ImportResult.Imported>().map { it.asset }
            if (assets.isEmpty()) {
                mutableState.value = EditorState.AllFailed
            } else {
                val project = ProjectFactory.create(projectName, assets, idGenerator)
                try {
                    attach(store.create(project), skippedCount = results.size - assets.size, droppedEditCount = 0)
                } catch (ignored: IOException) {
                    mutableState.value = EditorState.OpenFailed(OpenFailure.STORAGE)
                }
            }
        }
    }

    /** Opens the saved project [projectId]; ignored while another project is open. */
    fun openProject(projectId: String) {
        if (mutableState.value != EditorState.Idle) return
        mutableState.value = EditorState.Loading
        viewModelScope.launch {
            val result =
                try {
                    store.open(projectId)
                } catch (ignored: IOException) {
                    null
                }
            when (result) {
                is OpenProjectResult.Opened -> attach(result.session, 0, result.droppedRecordCount)
                OpenProjectResult.NotFound -> mutableState.value = EditorState.Idle
                is OpenProjectResult.TooNew -> mutableState.value = EditorState.OpenFailed(OpenFailure.NEWER_VERSION)
                is OpenProjectResult.Corrupt -> mutableState.value = EditorState.OpenFailed(OpenFailure.UNREADABLE)
                null -> mutableState.value = EditorState.OpenFailed(OpenFailure.STORAGE)
            }
        }
    }

    /**
     * Applies and saves [command], then selects [selectClipId] when given. A rejected command changes nothing and
     * raises the rejection notice.
     */
    fun dispatch(
        command: EditCommand,
        selectClipId: String? = null,
    ) {
        edit(selectClipId) { session ->
            when (val result = session.dispatch(command)) {
                is DispatchResult.Applied -> {
                    result.state
                }

                is DispatchResult.Rejected -> {
                    mutableState.updateReady { it.copy(editRejected = true) }
                    null
                }
            }
        }
    }

    /** Undoes the last edit. */
    fun undo() {
        edit { it.undo() }
    }

    /** Redoes the last undone edit. */
    fun redo() {
        edit { it.redo() }
    }

    /** Selects the clip [clipId], or clears the selection when null. */
    fun selectClip(clipId: String?) {
        mutableState.updateReady { it.copy(selectedClipId = clipId) }
    }

    /** Marks [notice] as shown so it does not show again. */
    fun acknowledge(notice: EditorNotice) {
        mutableState.updateReady { ready ->
            when (notice) {
                EditorNotice.SKIPPED_FILES -> ready.copy(skippedCount = 0)
                EditorNotice.DROPPED_EDITS -> ready.copy(droppedEditCount = 0)
                EditorNotice.SAVE_FAILED -> ready.copy(saveFailed = false)
                EditorNotice.EDIT_REJECTED -> ready.copy(editRejected = false)
            }
        }
    }

    /** Closes the open project, already saved, and returns to the home screen. */
    fun close() {
        open?.let { closing ->
            closing.release()
            viewModelScope.launch { closing.closeSession() }
        }
        open = null
        mutableState.value = EditorState.Idle
    }

    /** Releases the preview and timeline media when the view model is destroyed; edits are already saved. */
    override fun onCleared() {
        open?.release()
    }

    /** Makes [opened] the open project and shows it. */
    private fun attach(
        opened: ProjectSession,
        skippedCount: Int,
        droppedEditCount: Int,
    ) {
        val current = opened.state
        val preview = previewFactory().also { it.setProject(current.project) }
        open = OpenProject(opened, preview, mediaFactory())
        mutableState.value =
            EditorState.Ready(
                project = current.project,
                canUndo = current.canUndo,
                canRedo = current.canRedo,
                skippedCount = skippedCount,
                droppedEditCount = droppedEditCount,
            )
    }

    /**
     * Runs [change] on the open project after earlier changes and shows its result, selecting [selectClipId] when
     * given, while that project is still open; a failed save raises the notice.
     */
    private fun edit(
        selectClipId: String? = null,
        change: suspend (ProjectSession) -> SessionState?,
    ) {
        val target = open ?: return
        viewModelScope.launch {
            try {
                val next = target.change(change)
                if (next != null && open === target) {
                    target.preview.setProject(next.project)
                    mutableState.updateReady { ready ->
                        ready.copy(selectedClipId = selectClipId ?: ready.selectedClipId).showing(next)
                    }
                }
            } catch (ignored: IOException) {
                mutableState.updateReady { it.copy(saveFailed = true) }
            }
        }
    }

    /** Factory wiring. */
    companion object {
        /** Returns a factory that builds the view model from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EditorViewModel(
                        importer = container.mediaImporter,
                        idGenerator = container.idGenerator,
                        store = container.projectStore,
                        previewFactory = container::createPreview,
                        mediaFactory = container::createTimelineMedia,
                    )
                }
            }
    }
}

/** Applies [transform] when a project is open. */
internal fun MutableStateFlow<EditorState>.updateReady(transform: (EditorState.Ready) -> EditorState.Ready) {
    update { state -> if (state is EditorState.Ready) transform(state) else state }
}

/** Returns this state showing [next], dropping a selection whose clip no longer exists. */
private fun EditorState.Ready.showing(next: SessionState): EditorState.Ready {
    val tracks = next.project.tracks
    val clipIds = tracks.flatMap { track -> track.clips.map { it.id } }.toSet()
    return copy(
        project = next.project,
        canUndo = next.canUndo,
        canRedo = next.canRedo,
        selectedClipId = selectedClipId?.takeIf { it in clipIds },
    )
}

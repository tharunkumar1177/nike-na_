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
import com.framecraft.editor.engine.ClipThumbnails
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * [project] is open. The counters and flag are one-shot notices not yet shown: [skippedCount] picked
     * files could not be read, [droppedEditCount] edits were lost in a crash, and [saveFailed] an edit
     * could not be saved.
     */
    data class Ready(
        val project: Project,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val selectedClipId: String? = null,
        val skippedCount: Int = 0,
        val droppedEditCount: Int = 0,
        val saveFailed: Boolean = false,
    ) : EditorState

    /** None of the picked files could be read. */
    data object AllFailed : EditorState

    /** The project could not be opened or created, for [reason]. */
    data class OpenFailed(
        val reason: OpenFailure,
    ) : EditorState
}

/** Holds the open project, its saving session, and its preview across configuration changes. */
class EditorViewModel(
    private val importer: MediaImporter,
    private val idGenerator: IdGenerator,
    private val store: ProjectStore,
    private val previewFactory: () -> ProjectPreview,
    private val thumbnailsFactory: () -> ClipThumbnails,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<EditorState>(EditorState.Idle)
    private val editLock = Mutex()
    private var session: ProjectSession? = null

    /** Current editor state. */
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    /** Preview of the open project; non-null while the state is [EditorState.Ready]. */
    var preview: ProjectPreview? = null
        private set

    /** Timeline thumbnails of the open project; non-null while the state is [EditorState.Ready]. */
    var thumbnails: ClipThumbnails? = null
        private set

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

    /** Applies and saves [command]; rejected commands change nothing. */
    fun dispatch(command: EditCommand) {
        edit { session -> (session.dispatch(command) as? DispatchResult.Applied)?.state }
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
        updateReady { it.copy(selectedClipId = clipId) }
    }

    /** Marks the skipped-files notice as shown. */
    fun acknowledgeSkipped() {
        updateReady { it.copy(skippedCount = 0) }
    }

    /** Marks the lost-edits notice as shown. */
    fun acknowledgeDroppedEdits() {
        updateReady { it.copy(droppedEditCount = 0) }
    }

    /** Marks the save-failure notice as shown. */
    fun acknowledgeSaveFailed() {
        updateReady { it.copy(saveFailed = false) }
    }

    /** Closes the open project, already saved, and returns to the home screen. */
    fun close() {
        session?.let { closing ->
            viewModelScope.launch {
                try {
                    closing.close()
                } catch (ignored: IOException) {
                }
            }
        }
        session = null
        releaseProjectResources()
        mutableState.value = EditorState.Idle
    }

    /** Releases the preview and thumbnails when the view model is destroyed; edits are already saved. */
    override fun onCleared() {
        releaseProjectResources()
    }

    /** Makes [opened] the open project and shows it. */
    private fun attach(
        opened: ProjectSession,
        skippedCount: Int,
        droppedEditCount: Int,
    ) {
        val current = opened.state
        session = opened
        preview = previewFactory().also { it.setProject(current.project) }
        thumbnails = thumbnailsFactory()
        mutableState.value =
            EditorState.Ready(
                project = current.project,
                canUndo = current.canUndo,
                canRedo = current.canRedo,
                skippedCount = skippedCount,
                droppedEditCount = droppedEditCount,
            )
    }

    /** Runs [change] on the open session one at a time and shows its result; a failed save raises the notice. */
    private fun edit(change: suspend (ProjectSession) -> SessionState?) {
        val target = session ?: return
        viewModelScope.launch {
            editLock.withLock {
                try {
                    change(target)?.let { if (session === target) publish(it) }
                } catch (ignored: IOException) {
                    updateReady { it.copy(saveFailed = true) }
                }
            }
        }
    }

    /** Shows [next] in the preview and state, dropping a selection whose clip no longer exists. */
    private fun publish(next: SessionState) {
        preview?.setProject(next.project)
        val clipIds = next.project.tracks.flatMap { track -> track.clips.map { it.id } }.toSet()
        updateReady { ready ->
            ready.copy(
                project = next.project,
                canUndo = next.canUndo,
                canRedo = next.canRedo,
                selectedClipId = ready.selectedClipId?.takeIf { it in clipIds },
            )
        }
    }

    /** Applies [transform] when a project is open. */
    private fun updateReady(transform: (EditorState.Ready) -> EditorState.Ready) {
        mutableState.update { state -> if (state is EditorState.Ready) transform(state) else state }
    }

    /** Releases and forgets the preview player and thumbnails. */
    private fun releaseProjectResources() {
        preview?.release()
        preview = null
        thumbnails?.close()
        thumbnails = null
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
                        thumbnailsFactory = container::createThumbnails,
                    )
                }
            }
    }
}

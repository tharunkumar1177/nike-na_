/*
 * Editor state holder: imports picked media, builds the project, and owns its preview player.
 */
package com.framecraft.editor.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.framecraft.editor.di.AppContainer
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

/** What the editor is showing. */
sealed interface EditorState {
    /** No project is open; the home screen shows. */
    data object Idle : EditorState

    /** Picked media is being probed. */
    data object Loading : EditorState

    /** [project] is open; [skippedCount] picked files could not be read and are not yet acknowledged. */
    data class Ready(
        val project: Project,
        val skippedCount: Int,
    ) : EditorState

    /** None of the picked files could be read. */
    data object AllFailed : EditorState
}

/** Holds the open project and its preview across configuration changes. */
class EditorViewModel(
    private val importer: MediaImporter,
    private val idGenerator: IdGenerator,
    private val previewFactory: () -> ProjectPreview,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<EditorState>(EditorState.Idle)

    /** Current editor state. */
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    /** Preview of the open project; non-null while the state is [EditorState.Ready]. */
    var preview: ProjectPreview? = null
        private set

    /** Imports [uris] into a new project named [projectName]; ignored when empty or a project is open. */
    fun importMedia(
        uris: List<String>,
        projectName: String,
    ) {
        if (uris.isEmpty() || mutableState.value != EditorState.Idle) return
        mutableState.value = EditorState.Loading
        viewModelScope.launch {
            val results = withContext(ioDispatcher) { uris.map(importer::importUri) }
            val assets = results.filterIsInstance<ImportResult.Imported>().map { it.asset }
            mutableState.value =
                if (assets.isEmpty()) {
                    EditorState.AllFailed
                } else {
                    val project = ProjectFactory.create(projectName, assets, idGenerator)
                    preview = previewFactory().also { it.setProject(project) }
                    EditorState.Ready(project, skippedCount = results.size - assets.size)
                }
        }
    }

    /** Marks the skipped-files notice as shown. */
    fun acknowledgeSkipped() {
        mutableState.update { state -> if (state is EditorState.Ready) state.copy(skippedCount = 0) else state }
    }

    /** Closes the open project and returns to the home screen. */
    fun close() {
        releasePreview()
        mutableState.value = EditorState.Idle
    }

    /** Releases the preview player when the view model is destroyed. */
    override fun onCleared() {
        releasePreview()
    }

    /** Releases and forgets the preview player. */
    private fun releasePreview() {
        preview?.release()
        preview = null
    }

    /** Factory wiring. */
    companion object {
        /** Returns a factory that builds the view model from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EditorViewModel(container.mediaImporter, container.idGenerator, container::createPreview)
                }
            }
    }
}
